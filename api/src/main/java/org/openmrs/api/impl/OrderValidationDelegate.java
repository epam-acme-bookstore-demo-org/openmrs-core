/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.api.impl;

import java.util.List;

import org.hibernate.proxy.HibernateProxy;
import org.openmrs.CareSetting;
import org.openmrs.Concept;
import org.openmrs.DrugOrder;
import org.openmrs.Order;
import org.openmrs.OrderType;
import org.openmrs.ReferralOrder;
import org.openmrs.TestOrder;
import org.openmrs.api.AmbiguousOrderException;
import org.openmrs.api.MissingRequiredPropertyException;
import org.openmrs.api.OrderContext;
import org.openmrs.api.OrderEntryException;
import org.openmrs.api.UnchangeableObjectException;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.OrderDAO;
import org.openmrs.order.OrderUtil;
import org.openmrs.util.OpenmrsConstants;
import org.openmrs.util.OpenmrsUtil;
import org.springframework.util.StringUtils;

import static org.openmrs.Order.Action.DISCONTINUE;
import static org.openmrs.Order.Action.REVISE;

/**
 * Delegate that handles order validation and preparation logic, extracted from
 * {@link OrderServiceImpl} to reduce class size and improve maintainability.
 * <p>
 * This is an internal implementation class and should not be used directly by external code.
 */
class OrderValidationDelegate {

	private final OrderDAO dao;

	OrderValidationDelegate(OrderDAO dao) {
		this.dao = dao;
	}

	void failOnExistingOrder(Order order) {
		if (order.getOrderId() != null) {
			throw new UnchangeableObjectException("Order.cannot.edit.existing");
		}
	}

	void ensureDateActivatedIsSet(Order order) {
		if (order.getDateActivated() == null) {
			order.setDateActivated(new java.util.Date());
		}
	}

	void ensureConceptIsSet(Order order) {
		Concept concept = order.getConcept();
		if (concept == null && isDrugOrder(order)) {
			DrugOrder drugOrder = (DrugOrder) order;
			if (drugOrder.getDrug() != null) {
				concept = drugOrder.getDrug().getConcept();
				drugOrder.setConcept(concept);
			} else {
				if (drugOrder.isNonCodedDrug()) {
					concept = getNonCodedDrugConcept();
					drugOrder.setConcept(concept);
				}
			}
		}
		if (concept == null) {
			throw new MissingRequiredPropertyException("Order.concept.required");
		}
	}

	void ensureDrugOrderAutoExpirationDateIsSet(Order order) {
		if (isDrugOrder(order)) {
			((DrugOrder) order).setAutoExpireDateBasedOnDuration();
		}
	}

	void ensureOrderTypeIsSet(Order order, OrderContext orderContext) {
		if (order.getOrderType() != null) {
			return;
		}
		OrderType orderType = null;
		if (orderContext != null) {
			orderType = orderContext.getOrderType();
		}
		if (orderType == null) {
			orderType = getOrderTypeByConcept(order.getConcept());
		}
		if (orderType == null && order instanceof DrugOrder) {
			orderType = getDefaultOrderType(DrugOrder.class, OrderType.DRUG_ORDER_TYPE_UUID);
		}
		if (orderType == null && order instanceof TestOrder) {
			orderType = getDefaultOrderType(TestOrder.class, OrderType.TEST_ORDER_TYPE_UUID);
		}
		if (orderType == null && order instanceof ReferralOrder) {
			orderType = getDefaultOrderType(ReferralOrder.class, OrderType.REFERRAL_ORDER_TYPE_UUID);
		}
		if (orderType == null) {
			throw new OrderEntryException("Order.type.cannot.determine");
		}
		Order previousOrder = order.getPreviousOrder();
		if (previousOrder != null && !orderType.equals(previousOrder.getOrderType())) {
			throw new OrderEntryException("Order.type.does.not.match");
		}
		order.setOrderType(orderType);
	}

	void ensureCareSettingIsSet(Order order, OrderContext orderContext) {
		if (order.getCareSetting() != null) {
			return;
		}
		CareSetting careSetting = null;
		if (orderContext != null) {
			careSetting = orderContext.getCareSetting();
		}
		Order previousOrder = order.getPreviousOrder();
		if (careSetting == null || (previousOrder != null && !careSetting.equals(previousOrder.getCareSetting()))) {
			throw new OrderEntryException("Order.care.cannot.determine");
		}
		order.setCareSetting(careSetting);
	}

	void failOnOrderTypeMismatch(Order order) {
		if (!order.getOrderType().getJavaClass().isAssignableFrom(order.getClass())) {
			throw new OrderEntryException("Order.type.class.does.not.match",
			        new Object[] { order.getOrderType().getJavaClass(), order.getClass().getName() });
		}
	}

	boolean areDrugOrdersOfSameOrderableAndOverlappingSchedule(Order firstOrder, Order secondOrder) {
		return firstOrder.hasSameOrderableAs(secondOrder)
		        && !OpenmrsUtil.nullSafeEquals(firstOrder.getPreviousOrder(), secondOrder)
		        && OrderUtil.checkScheduleOverlap(firstOrder, secondOrder)
		        && firstOrder.getOrderType().equals(getDefaultOrderType(DrugOrder.class, OrderType.DRUG_ORDER_TYPE_UUID));
	}

	boolean isDrugOrder(Order order) {
		return DrugOrder.class.isAssignableFrom(getActualType(order));
	}

	boolean isDiscontinueOrReviseOrder(Order order) {
		return DISCONTINUE == order.getAction() || REVISE == order.getAction();
	}

	/**
	 * Returns the class object of the specified persistent object returning the actual persistent class
	 * in case it is a hibernate proxy.
	 *
	 * @param persistentObject the persistent object
	 * @return the Class object
	 */
	Class<?> getActualType(Object persistentObject) {
		Class<?> type = persistentObject.getClass();
		if (persistentObject instanceof HibernateProxy proxy) {
			type = proxy.getHibernateLazyInitializer().getPersistentClass();
		}
		return type;
	}

	/**
	 * Finds the single matching active order to discontinue, or throws if ambiguous.
	 */
	Order findOrderToDiscontinue(Order order, List<? extends Order> activeOrders, boolean isDrugOrderAndHasADrug) {
		Order orderToBeDiscontinued = null;
		for (Order activeOrder : activeOrders) {
			if (!getActualType(order).equals(getActualType(activeOrder))) {
				continue;
			}

			Order matched = matchOrderForDiscontinuation(order, activeOrder, isDrugOrderAndHasADrug);
			if (matched == null) {
				continue;
			}

			if (orderToBeDiscontinued != null) {
				throw new AmbiguousOrderException("Order.discontinuing.ambiguous.orders");
			}
			orderToBeDiscontinued = matched;
		}
		return orderToBeDiscontinued;
	}

	OrderType getDefaultOrderType(Class<? extends Order> orderSubclass, String fallbackUuid) {
		OrderType type = dao.getOrderTypeByUuid(fallbackUuid);

		if (type == null) {
			List<OrderType> types = dao.getOrderTypes(false).stream().filter(ot -> {
				try {
					Class<?> c = Context.loadClass(ot.getJavaClassName());
					return orderSubclass.isAssignableFrom(c);
				} catch (Exception ignore) {
					return false;
				}
			}).toList();
			if (types.size() == 1) {
				type = types.getFirst();
			}
		}
		return type;
	}

	/**
	 * Returns the active order if it matches for discontinuation, or null.
	 */
	private Order matchOrderForDiscontinuation(Order order, Order activeOrder, boolean isDrugOrderAndHasADrug) {
		//For drug orders, the drug must match if the order has a drug
		if (isDrugOrderAndHasADrug) {
			return order.hasSameOrderableAs(activeOrder) ? activeOrder : null;
		}
		if (activeOrder.getConcept().equals(order.getConcept())) {
			return activeOrder;
		}
		return null;
	}

	private Concept getNonCodedDrugConcept() {
		String conceptUuid = Context.getAdministrationService().getGlobalProperty(OpenmrsConstants.GP_DRUG_ORDER_DRUG_OTHER);
		if (StringUtils.hasText(conceptUuid)) {
			return Context.getConceptService().getConceptByUuid(conceptUuid);
		}
		return null;
	}

	private OrderType getOrderTypeByConcept(Concept concept) {
		return dao.getOrderTypeByConceptClass(concept.getConceptClass());
	}
}
