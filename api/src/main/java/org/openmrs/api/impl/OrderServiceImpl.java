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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.lang3.time.DateUtils;
import org.openmrs.CareSetting;
import org.openmrs.Concept;
import org.openmrs.ConceptClass;
import org.openmrs.DrugOrder;
import org.openmrs.Encounter;
import org.openmrs.GlobalProperty;
import org.openmrs.Order;
import org.openmrs.Order.FulfillerStatus;
import org.openmrs.OrderAttribute;
import org.openmrs.OrderAttributeType;
import org.openmrs.OrderFrequency;
import org.openmrs.OrderGroup;
import org.openmrs.OrderGroupAttribute;
import org.openmrs.OrderGroupAttributeType;
import org.openmrs.OrderType;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.Visit;
import org.openmrs.api.APIException;
import org.openmrs.api.AmbiguousOrderException;
import org.openmrs.api.CannotDeleteObjectInUseException;
import org.openmrs.api.CannotUnvoidOrderException;
import org.openmrs.api.EditedOrderDoesNotMatchPreviousException;
import org.openmrs.api.GlobalPropertyListener;
import org.openmrs.api.MissingRequiredPropertyException;
import org.openmrs.api.OrderContext;
import org.openmrs.api.OrderNumberGenerator;
import org.openmrs.api.OrderService;
import org.openmrs.api.RefByUuid;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.OrderDAO;
import org.openmrs.customdatatype.CustomDatatypeUtil;
import org.openmrs.parameter.OrderSearchCriteria;
import org.openmrs.util.OpenmrsConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import static org.openmrs.Order.Action.DISCONTINUE;
import static org.openmrs.Order.Action.NEW;
import static org.openmrs.Order.Action.REVISE;

/**
 * Default implementation of the Order-related services class. This method should not be invoked by
 * itself. Spring injection is used to inject this implementation into the ServiceContext. Which
 * implementation is injected is determined by the spring application context file:
 * /metadata/api/spring/applicationContext.xml
 *
 * @see org.openmrs.api.OrderService
 */
@Service("orderService")
@Transactional
public class OrderServiceImpl extends BaseOpenmrsService implements OrderService, OrderNumberGenerator, GlobalPropertyListener, RefByUuid {

	private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

	private static final String ORDER_NUMBER_PREFIX = "ORD-";

	protected OrderDAO dao;

	private OrderValidationDelegate validationDelegate;

	private OrderNumberDelegate numberDelegate;

	private static OrderNumberGenerator orderNumberGenerator = null;

	public OrderServiceImpl() {
	}

	/**
	 * @see org.openmrs.api.OrderService#setOrderDAO(org.openmrs.api.db.OrderDAO)
	 */
	@Autowired
	@Override
	public void setOrderDAO(OrderDAO dao) {
		this.dao = dao;
		this.validationDelegate = new OrderValidationDelegate(dao);
		this.numberDelegate = new OrderNumberDelegate(dao, this::getOrderNumberGenerator);
	}

	/**
	 * @see org.openmrs.api.OrderService#saveOrder(org.openmrs.Order, org.openmrs.api.OrderContext)
	 */
	@Override
	public synchronized Order saveOrder(Order order, OrderContext orderContext) throws APIException {
		return saveOrder(order, orderContext, false);
	}

	/**
	 * @see org.openmrs.api.OrderService#saveOrderGroup(org.openmrs.OrderGroup)
	 */
	@Override
	public OrderGroup saveOrderGroup(OrderGroup orderGroup) throws APIException {
		return saveOrderGroup(orderGroup, null);
	}

	/**
	 * @see org.openmrs.api.OrderService#saveOrderGroup(org.openmrs.OrderGroup,
	 *      org.openmrs.api.OrderContext)
	 */
	@Override
	public OrderGroup saveOrderGroup(OrderGroup orderGroup, OrderContext orderContext) throws APIException {
		if (orderGroup.getId() == null) {
			// an OrderGroup requires an encounter, which has a patient, so it
			// is odd that OrderGroup has a patient field. There is no obvious
			// reason why they should ever be different.
			orderGroup.setPatient(orderGroup.getEncounter().getPatient());
			CustomDatatypeUtil.saveAttributesIfNecessary(orderGroup);
			dao.saveOrderGroup(orderGroup);
		}
		List<Order> orders = orderGroup.getOrders();
		for (Order order : orders) {
			if (order.getId() == null) {
				order.setEncounter(orderGroup.getEncounter());
				Context.getOrderService().saveOrder(order, orderContext);
			}
		}
		Set<OrderGroup> nestedGroups = orderGroup.getNestedOrderGroups();
		if (nestedGroups != null) {
			for (OrderGroup nestedGroup : nestedGroups) {
				Context.getOrderService().saveOrderGroup(nestedGroup, orderContext);
			}
		}
		return orderGroup;
	}

	/**
	 * @see org.openmrs.api.OrderService#saveOrder(org.openmrs.Order, org.openmrs.api.OrderContext)
	 */
	@Override
	public synchronized Order saveRetrospectiveOrder(Order order, OrderContext orderContext) {
		return saveOrder(order, orderContext, true);
	}

	private Order saveOrder(Order order, OrderContext orderContext, boolean isRetrospective) {

		validationDelegate.failOnExistingOrder(order);
		validationDelegate.ensureDateActivatedIsSet(order);
		validationDelegate.ensureConceptIsSet(order);
		validationDelegate.ensureDrugOrderAutoExpirationDateIsSet(order);
		validationDelegate.ensureOrderTypeIsSet(order, orderContext);
		validationDelegate.ensureCareSettingIsSet(order, orderContext);
		validationDelegate.failOnOrderTypeMismatch(order);

		// If isRetrospective is false, but the dateActivated is prior to the current date, set isRetrospective to true
		if (!isRetrospective) {
			Date dateActivated = order.getDateActivated();
			var currentDate = new Date();
			isRetrospective = !dateActivated.after(currentDate) && !DateUtils.isSameDay(dateActivated, currentDate);
		}

		Order previousOrder = order.getPreviousOrder();
		if (REVISE == order.getAction()) {
			if (previousOrder == null) {
				throw new MissingRequiredPropertyException("Order.previous.required", (Object[]) null);
			}
			numberDelegate.stopOrder(previousOrder, numberDelegate.aMomentBefore(order.getDateActivated()), isRetrospective);
		} else if (DISCONTINUE == order.getAction()) {
			discontinueExistingOrdersIfNecessary(order, isRetrospective);
		}

		if (previousOrder != null) {
			//concept should be the same as on previous order, same applies to drug for drug orders
			if (NEW != order.getAction() && !order.hasSameOrderableAs(previousOrder)) {
				throw new EditedOrderDoesNotMatchPreviousException("Order.orderable.doesnot.match");
			} else if (!order.getOrderType().equals(previousOrder.getOrderType())) {
				throw new EditedOrderDoesNotMatchPreviousException("Order.type.doesnot.match");
			} else if (!order.getCareSetting().equals(previousOrder.getCareSetting())) {
				throw new EditedOrderDoesNotMatchPreviousException("Order.care.setting.doesnot.match");
			} else if (!validationDelegate.getActualType(order).equals(validationDelegate.getActualType(previousOrder))) {
				throw new EditedOrderDoesNotMatchPreviousException("Order.class.doesnot.match");
			}
		}

		if (DISCONTINUE != order.getAction()) {
			var asOfDate = new Date();
			if (isRetrospective) {
				asOfDate = order.getDateActivated();
			}
			List<Order> activeOrders = getActiveOrders(order.getPatient(), null, order.getCareSetting(), asOfDate);
			List<String> parallelOrders = List.of();
			if (orderContext != null && orderContext.getAttribute(PARALLEL_ORDERS) != null) {
				parallelOrders = Arrays.asList((String[]) orderContext.getAttribute(PARALLEL_ORDERS));
			}
			for (Order activeOrder : activeOrders) {
				//Reject if there is an active drug order for the same orderable with overlapping schedule
				if (!parallelOrders.contains(activeOrder.getUuid())
				        && validationDelegate.areDrugOrdersOfSameOrderableAndOverlappingSchedule(order, activeOrder)) {
					throw new AmbiguousOrderException("Order.cannot.have.more.than.one");
				}
			}
		}
		return numberDelegate.saveOrderInternal(order, orderContext);
	}

	/**
	 * If this is a discontinue order, ensure that the previous order is discontinued. If a
	 * previousOrder is present, then ensure this is discontinued. If no previousOrder is present, then
	 * try to find a previousOrder and discontinue it. If cannot find a previousOrder, throw exception
	 *
	 * @param order
	 * @param isRetrospective
	 */
	//Ignore and return if this is not an order to discontinue
	private void discontinueExistingOrdersIfNecessary(Order order, Boolean isRetrospective) {
		if (DISCONTINUE != order.getAction()) {
			return;
		}

		//Mark previousOrder as discontinued if it is not already
		Order previousOrder = order.getPreviousOrder();
		if (previousOrder != null) {
			numberDelegate.stopOrder(previousOrder, numberDelegate.aMomentBefore(order.getDateActivated()), isRetrospective);
			return;
		}

		//Mark first order found corresponding to this DC order as discontinued.
		Date asOfDate = null;
		if (isRetrospective) {
			asOfDate = order.getDateActivated();
		}
		List<? extends Order> orders = getActiveOrders(order.getPatient(), order.getOrderType(), order.getCareSetting(),
		    asOfDate);
		boolean isDrugOrderAndHasADrug = validationDelegate.isDrugOrder(order)
		        && (((DrugOrder) order).getDrug() != null || ((DrugOrder) order).isNonCodedDrug());
		Order orderToBeDiscontinued = validationDelegate.findOrderToDiscontinue(order, orders, isDrugOrderAndHasADrug);
		if (orderToBeDiscontinued != null) {
			order.setPreviousOrder(orderToBeDiscontinued);
			numberDelegate.stopOrder(orderToBeDiscontinued, numberDelegate.aMomentBefore(order.getDateActivated()),
			    isRetrospective);
		}
	}

	/**
	 * @see org.openmrs.api.OrderService#purgeOrder(org.openmrs.Order)
	 */
	@Override
	public void purgeOrder(Order order) throws APIException {
		purgeOrder(order, false);
	}

	/**
	 * @see org.openmrs.api.OrderService#purgeOrder(Order)
	 */
	@Override
	public void purgeOrder(Order order, boolean cascade) throws APIException {
		if (cascade) {
			dao.deleteObsThatReference(order);
		}

		dao.deleteOrder(order);
	}

	/**
	 * @see org.openmrs.api.OrderService#voidOrder(org.openmrs.Order, java.lang.String)
	 */
	@Override
	public Order voidOrder(Order order, String voidReason) throws APIException {
		if (!StringUtils.hasLength(voidReason)) {
			throw new IllegalArgumentException("voidReason cannot be empty or null");
		}

		Order previousOrder = order.getPreviousOrder();
		if (previousOrder != null && validationDelegate.isDiscontinueOrReviseOrder(order)) {
			numberDelegate.setProperty(previousOrder, "dateStopped", null);
		}

		return numberDelegate.saveOrderInternal(order, null);
	}

	/**
	 * @see org.openmrs.api.OrderService#unvoidOrder(org.openmrs.Order)
	 */
	@Override
	public Order unvoidOrder(Order order) throws APIException {
		Order previousOrder = order.getPreviousOrder();
		if (previousOrder != null && validationDelegate.isDiscontinueOrReviseOrder(order)) {
			if (!previousOrder.isActive()) {
				final String action = DISCONTINUE == order.getAction() ? "discontinuation" : "revision";
				throw new CannotUnvoidOrderException(action);
			}
			numberDelegate.stopOrder(previousOrder, numberDelegate.aMomentBefore(order.getDateActivated()), false);
		}

		return numberDelegate.saveOrderInternal(order, null);
	}

	@Override
	public Order updateOrderFulfillerStatus(Order order, Order.FulfillerStatus orderFulfillerStatus,
	        String fullFillerComment) {
		return updateOrderFulfillerStatus(order, orderFulfillerStatus, fullFillerComment, null);
	}

	@Override
	public Order updateOrderFulfillerStatus(Order order, FulfillerStatus orderFulfillerStatus, String fullFillerComment,
	        String accessionNumber) {

		if (orderFulfillerStatus != null) {
			order.setFulfillerStatus(orderFulfillerStatus);
		}

		if (fullFillerComment != null) {
			order.setFulfillerComment(fullFillerComment);
		}

		if (accessionNumber != null) {
			order.setAccessionNumber(accessionNumber);
		}

		return numberDelegate.saveOrderInternal(order, null);
	}

	/**
	 * @see org.openmrs.api.OrderService#getOrder(java.lang.Integer)
	 */
	@Override
	@Transactional(readOnly = true)
	public Order getOrder(Integer orderId) throws APIException {
		return dao.getOrder(orderId);
	}

	/**
	 * @see OrderService#getOrders(org.openmrs.Patient, org.openmrs.CareSetting, org.openmrs.OrderType,
	 *      boolean)
	 */
	@Override
	public List<Order> getOrders(Patient patient, CareSetting careSetting, OrderType orderType, boolean includeVoided) {
		return this.getOrders(patient, null, careSetting, orderType, includeVoided);
	}

	/**
	 * @see OrderService#getOrders(org.openmrs.Patient, org.openmrs.Visit, org.openmrs.CareSetting,
	 *      org.openmrs.OrderType, boolean)
	 */
	@Override
	public List<Order> getOrders(Patient patient, Visit visit, CareSetting careSetting, OrderType orderType,
	        boolean includeVoided) {
		if (patient == null) {
			throw new IllegalArgumentException("Patient is required");
		}
		if (careSetting == null) {
			throw new IllegalArgumentException("CareSetting is required");
		}
		List<OrderType> orderTypes = null;
		if (orderType != null) {
			orderTypes = new ArrayList<>();
			orderTypes.add(orderType);
			orderTypes.addAll(getSubtypes(orderType, true));
		}
		return dao.getOrders(patient, visit, careSetting, orderTypes, includeVoided, false);
	}

	/**
	 * @see OrderService#getAllOrdersByPatient(org.openmrs.Patient)
	 */
	@Override
	public List<Order> getAllOrdersByPatient(Patient patient) {
		if (patient == null) {
			throw new IllegalArgumentException("Patient is required");
		}
		return dao.getOrders(patient, null, null, true, true);
	}

	@Override
	public List<Order> getOrders(OrderSearchCriteria orderSearchCriteria) {
		return dao.getOrders(orderSearchCriteria);
	}

	/**
	 * @see org.openmrs.api.OrderService#getOrderByUuid(java.lang.String)
	 */
	@Override
	@Transactional(readOnly = true)
	public Order getOrderByUuid(String uuid) throws APIException {
		return dao.getOrderByUuid(uuid);
	}

	/**
	 * @see org.openmrs.api.OrderService#getDiscontinuationOrder(Order)
	 */
	@Transactional(readOnly = true)
	@Override
	public Order getDiscontinuationOrder(Order order) throws APIException {
		return dao.getDiscontinuationOrder(order);
	}

	/**
	 * @see org.openmrs.api.OrderService#getRevisionOrder(Order)
	 */
	@Override
	public Order getRevisionOrder(Order order) throws APIException {
		return dao.getRevisionOrder(order);
	}

	/**
	 * @see org.openmrs.api.OrderNumberGenerator#getNewOrderNumber(org.openmrs.api.OrderContext)
	 * @param orderContext
	 */
	@Override
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public String getNewOrderNumber(OrderContext orderContext) throws APIException {
		return ORDER_NUMBER_PREFIX + Context.getOrderService().getNextOrderNumberSeedSequenceValue();
	}

	/**
	 * @see org.openmrs.api.OrderService#getOrderByOrderNumber(java.lang.String)
	 */
	@Override
	@Transactional(readOnly = true)
	public Order getOrderByOrderNumber(String orderNumber) {
		return dao.getOrderByOrderNumber(orderNumber);
	}

	/**
	 * @see org.openmrs.api.OrderService#getOrderHistoryByConcept(org.openmrs.Patient,
	 *      org.openmrs.Concept)
	 */
	@Override
	@Transactional(readOnly = true)
	public List<Order> getOrderHistoryByConcept(Patient patient, Concept concept) {
		if (patient == null || concept == null) {
			throw new IllegalArgumentException("patient and concept are required");
		}
		var concepts = new ArrayList<Concept>();
		concepts.add(concept);

		var patients = new ArrayList<Patient>();
		patients.add(patient);

		return dao.getOrders(null, patients, concepts, new ArrayList<>(), new ArrayList<>());
	}

	/**
	 * @see org.openmrs.api.OrderService#getNextOrderNumberSeedSequenceValue()
	 */
	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public synchronized Long getNextOrderNumberSeedSequenceValue() {
		return dao.getNextOrderNumberSeedSequenceValue();
	}

	/**
	 * @see org.openmrs.api.OrderService#getOrderHistoryByOrderNumber(java.lang.String)
	 */
	@Override
	@Transactional(readOnly = true)
	public List<Order> getOrderHistoryByOrderNumber(String orderNumber) {
		var orders = new ArrayList<Order>();
		Order order = dao.getOrderByOrderNumber(orderNumber);
		while (order != null) {
			orders.add(order);
			order = order.getPreviousOrder();
		}
		return orders;
	}

	/**
	 * @see org.openmrs.api.OrderService#getActiveOrders(org.openmrs.Patient, org.openmrs.OrderType,
	 *      org.openmrs.CareSetting, java.util.Date)
	 */
	@Override
	@Transactional(readOnly = true)
	public List<Order> getActiveOrders(Patient patient, OrderType orderType, CareSetting careSetting, Date asOfDate) {
		return this.getActiveOrders(patient, null, orderType, careSetting, asOfDate);
	}

	/**
	 * @see org.openmrs.api.OrderService#getActiveOrders(org.openmrs.Patient, org.openmrs.Visit,
	 *      org.openmrs.OrderType, org.openmrs.CareSetting, java.util.Date)
	 */
	@Override
	@Transactional(readOnly = true)
	public List<Order> getActiveOrders(Patient patient, Visit visit, OrderType orderType, CareSetting careSetting,
	        Date asOfDate) {
		if (patient == null) {
			throw new IllegalArgumentException("Patient is required when fetching active orders");
		}
		if (asOfDate == null) {
			asOfDate = new Date();
		}
		List<OrderType> orderTypes = null;
		if (orderType != null) {
			orderTypes = new ArrayList<>();
			orderTypes.add(orderType);
			orderTypes.addAll(getSubtypes(orderType, true));
		}
		return dao.getActiveOrders(patient, visit, orderTypes, careSetting, asOfDate);
	}

	/**
	 * @see org.openmrs.api.OrderService#getCareSetting(Integer)
	 */
	@Override
	public CareSetting getCareSetting(Integer careSettingId) {
		return dao.getCareSetting(careSettingId);
	}

	/**
	 * @see org.openmrs.api.OrderService#getCareSettingByUuid(String)
	 */
	@Override
	public CareSetting getCareSettingByUuid(String uuid) {
		return dao.getCareSettingByUuid(uuid);
	}

	/**
	 * @see org.openmrs.api.OrderService#getCareSettingByName(String)
	 */
	@Override
	public CareSetting getCareSettingByName(String name) {
		return dao.getCareSettingByName(name);
	}

	/**
	 * @see org.openmrs.api.OrderService#getCareSettings(boolean)
	 */
	@Override
	@Deprecated(since = "3.0.0", forRemoval = true)
	public List<CareSetting> getCareSettings(boolean includeRetired) {
		return dao.getCareSettings(includeRetired);
	}

	/**
	 * @see org.openmrs.api.OrderService#getCareSettings()
	 */
	@Override
	public List<CareSetting> getCareSettings() {
		return dao.getCareSettings(false);
	}

	/**
	 * @see org.openmrs.api.OrderService#getCareSettingsIncludingRetired()
	 */
	@Override
	public List<CareSetting> getCareSettingsIncludingRetired() {
		return dao.getCareSettings(true);
	}

	/**
	 * @see OrderService#getOrderTypeByName(String)
	 */
	@Override
	public OrderType getOrderTypeByName(String orderTypeName) {
		return dao.getOrderTypeByName(orderTypeName);
	}

	/**
	 * @see OrderService#getOrderFrequency(Integer)
	 */
	@Override
	public OrderFrequency getOrderFrequency(Integer orderFrequencyId) {
		return dao.getOrderFrequency(orderFrequencyId);
	}

	/**
	 * @see OrderService#getOrderFrequencyByUuid(String)
	 */
	@Override
	public OrderFrequency getOrderFrequencyByUuid(String uuid) {
		return dao.getOrderFrequencyByUuid(uuid);
	}

	/**
	 * @see OrderService#getOrderFrequencies(boolean)
	 */
	@Override
	@Deprecated(since = "3.0.0", forRemoval = true)
	public List<OrderFrequency> getOrderFrequencies(boolean includeRetired) {
		return dao.getOrderFrequencies(includeRetired);
	}

	/**
	 * @see OrderService#getOrderFrequencies()
	 */
	@Override
	public List<OrderFrequency> getOrderFrequencies() {
		return dao.getOrderFrequencies(false);
	}

	/**
	 * @see OrderService#getOrderFrequenciesIncludingRetired()
	 */
	@Override
	public List<OrderFrequency> getOrderFrequenciesIncludingRetired() {
		return dao.getOrderFrequencies(true);
	}

	/**
	 * @see OrderService#getOrderFrequencies(String, java.util.Locale, boolean, boolean)
	 */
	@Override
	public List<OrderFrequency> getOrderFrequencies(String searchPhrase, Locale locale, boolean exactLocale,
	        boolean includeRetired) {
		if (searchPhrase == null) {
			throw new IllegalArgumentException("searchPhrase is required");
		}
		return dao.getOrderFrequencies(searchPhrase, locale, exactLocale, includeRetired);
	}

	/**
	 * @see org.openmrs.api.OrderService#discontinueOrder(org.openmrs.Order, org.openmrs.Concept,
	 *      java.util.Date, org.openmrs.Provider, org.openmrs.Encounter)
	 */
	@Override
	public Order discontinueOrder(Order orderToDiscontinue, Concept reasonCoded, Date discontinueDate, Provider orderer,
	        Encounter encounter) {
		if (discontinueDate == null) {
			discontinueDate = numberDelegate.aMomentBefore(new Date());
		}
		numberDelegate.stopOrder(orderToDiscontinue, discontinueDate, false);
		Order newOrder = orderToDiscontinue.cloneForDiscontinuing();
		newOrder.setOrderReason(reasonCoded);
		newOrder.setOrderer(orderer);
		newOrder.setEncounter(encounter);
		newOrder.setDateActivated(discontinueDate);
		return numberDelegate.saveOrderInternal(newOrder, null);
	}

	/**
	 * @see org.openmrs.api.OrderService#discontinueOrder(org.openmrs.Order, String, java.util.Date,
	 *      org.openmrs.Provider, org.openmrs.Encounter)
	 */
	@Override
	public Order discontinueOrder(Order orderToDiscontinue, String reasonNonCoded, Date discontinueDate, Provider orderer,
	        Encounter encounter) {
		if (discontinueDate == null) {
			discontinueDate = numberDelegate.aMomentBefore(new Date());
		}
		numberDelegate.stopOrder(orderToDiscontinue, discontinueDate, false);
		Order newOrder = orderToDiscontinue.cloneForDiscontinuing();
		newOrder.setOrderReasonNonCoded(reasonNonCoded);
		newOrder.setOrderer(orderer);
		newOrder.setEncounter(encounter);
		newOrder.setDateActivated(discontinueDate);
		return numberDelegate.saveOrderInternal(newOrder, null);
	}

	/**
	 * Gets the configured order number generator, if none is specified, it defaults to an instance if
	 * this class
	 *
	 * @return the configured or default order number generator
	 */
	private OrderNumberGenerator getOrderNumberGenerator() {
		if (orderNumberGenerator == null) {
			String generatorBeanId = Context.getAdministrationService()
			        .getGlobalProperty(OpenmrsConstants.GP_ORDER_NUMBER_GENERATOR_BEAN_ID);
			if (StringUtils.hasText(generatorBeanId)) {
				orderNumberGenerator = Context.getRegisteredComponent(generatorBeanId, OrderNumberGenerator.class);
				log.info("Successfully set the configured order number generator");
			} else {
				orderNumberGenerator = this;
				log.info("Setting default order number generator");
			}
		}

		return orderNumberGenerator;
	}

	/**
	 * @see org.openmrs.api.OrderService#saveOrderFrequency(org.openmrs.OrderFrequency)
	 */
	@Override
	public OrderFrequency saveOrderFrequency(OrderFrequency orderFrequency) throws APIException {
		return dao.saveOrderFrequency(orderFrequency);
	}

	/**
	 * @see org.openmrs.api.OrderService#retireOrderFrequency(org.openmrs.OrderFrequency,
	 *      java.lang.String)
	 */
	@Override
	public OrderFrequency retireOrderFrequency(OrderFrequency orderFrequency, String reason) {
		return dao.saveOrderFrequency(orderFrequency);
	}

	/**
	 * @see org.openmrs.api.OrderService#unretireOrderFrequency(org.openmrs.OrderFrequency)
	 */
	@Override
	public OrderFrequency unretireOrderFrequency(OrderFrequency orderFrequency) {
		return Context.getOrderService().saveOrderFrequency(orderFrequency);
	}

	/**
	 * @see org.openmrs.api.OrderService#purgeOrderFrequency(org.openmrs.OrderFrequency)
	 */
	@Override
	public void purgeOrderFrequency(OrderFrequency orderFrequency) {

		if (dao.isOrderFrequencyInUse(orderFrequency)) {
			throw new CannotDeleteObjectInUseException("Order.frequency.cannot.delete", (Object[]) null);
		}

		dao.purgeOrderFrequency(orderFrequency);
	}

	/**
	 * @see org.openmrs.api.OrderService#getOrderFrequencyByConcept(org.openmrs.Concept)
	 */
	@Override
	@Transactional(readOnly = true)
	public OrderFrequency getOrderFrequencyByConcept(Concept concept) {
		return dao.getOrderFrequencyByConcept(concept);
	}

	/**
	 * @see GlobalPropertyListener#supportsPropertyName(String)
	 */
	@Override
	public boolean supportsPropertyName(String propertyName) {
		return OpenmrsConstants.GP_ORDER_NUMBER_GENERATOR_BEAN_ID.equals(propertyName);
	}

	/**
	 * @see GlobalPropertyListener#globalPropertyChanged(org.openmrs.GlobalProperty)
	 */
	@Override
	public void globalPropertyChanged(GlobalProperty newValue) {
		setOrderNumberGenerator(null);
	}

	/**
	 * @see GlobalPropertyListener#globalPropertyDeleted(String)
	 */
	@Override
	public void globalPropertyDeleted(String propertyName) {
		setOrderNumberGenerator(null);
	}

	/**
	 * Helper method to deter instance methods from setting static fields
	 */
	private static void setOrderNumberGenerator(OrderNumberGenerator orderNumberGenerator) {
		OrderServiceImpl.orderNumberGenerator = orderNumberGenerator;
	}

	/**
	 * @see org.openmrs.api.OrderService#getOrderType(Integer)
	 */

	@Override
	@Transactional(readOnly = true)
	public OrderType getOrderType(Integer orderTypeId) {
		return dao.getOrderType(orderTypeId);
	}

	/**
	 * @see org.openmrs.api.OrderService#getOrderTypeByUuid(String)
	 */
	@Override
	@Transactional(readOnly = true)
	public OrderType getOrderTypeByUuid(String uuid) {
		return dao.getOrderTypeByUuid(uuid);
	}

	/**
	 * @see org.openmrs.api.OrderService#getOrderTypes(boolean)
	 */
	@Override
	@Transactional(readOnly = true)
	@Deprecated(since = "3.0.0", forRemoval = true)
	public List<OrderType> getOrderTypes(boolean includeRetired) {
		return dao.getOrderTypes(includeRetired);
	}

	/**
	 * @see org.openmrs.api.OrderService#getOrderTypes()
	 */
	@Override
	@Transactional(readOnly = true)
	public List<OrderType> getOrderTypes() {
		return dao.getOrderTypes(false);
	}

	/**
	 * @see org.openmrs.api.OrderService#getOrderTypesIncludingRetired()
	 */
	@Override
	@Transactional(readOnly = true)
	public List<OrderType> getOrderTypesIncludingRetired() {
		return dao.getOrderTypes(true);
	}

	/**
	 * @see org.openmrs.api.OrderService#saveOrderType(org.openmrs.OrderType)
	 */
	@Override
	public OrderType saveOrderType(OrderType orderType) {
		return dao.saveOrderType(orderType);
	}

	/**
	 * @see org.openmrs.api.OrderService#purgeOrderType(org.openmrs.OrderType)
	 */
	@Override
	public void purgeOrderType(OrderType orderType) {
		if (dao.isOrderTypeInUse(orderType)) {
			throw new CannotDeleteObjectInUseException("Order.type.cannot.delete", (Object[]) null);
		}
		dao.purgeOrderType(orderType);
	}

	/**
	 * @see org.openmrs.api.OrderService#retireOrderType(org.openmrs.OrderType, String)
	 */
	@Override
	public OrderType retireOrderType(OrderType orderType, String reason) {
		return saveOrderType(orderType);
	}

	/**
	 * @see org.openmrs.api.OrderService#unretireOrderType(org.openmrs.OrderType)
	 */
	@Override
	public OrderType unretireOrderType(OrderType orderType) {
		return saveOrderType(orderType);
	}

	/**
	 * @see org.openmrs.api.OrderService#getSubtypes(org.openmrs.OrderType, boolean)
	 */
	@Override
	@Transactional(readOnly = true)
	public List<OrderType> getSubtypes(OrderType orderType, boolean includeRetired) {
		var allSubtypes = new ArrayList<OrderType>();
		List<OrderType> immediateAncestors = dao.getOrderSubtypes(orderType, includeRetired);
		while (!immediateAncestors.isEmpty()) {
			var ancestorsAtNextLevel = new ArrayList<OrderType>();
			for (OrderType type : immediateAncestors) {
				allSubtypes.add(type);
				ancestorsAtNextLevel.addAll(dao.getOrderSubtypes(type, includeRetired));
			}
			immediateAncestors = ancestorsAtNextLevel;
		}
		return allSubtypes;
	}

	/**
	 * @see org.openmrs.api.OrderService#getOrderTypeByConceptClass(org.openmrs.ConceptClass)
	 */
	@Override
	@Transactional(readOnly = true)
	public OrderType getOrderTypeByConceptClass(ConceptClass conceptClass) {
		return dao.getOrderTypeByConceptClass(conceptClass);
	}

	/**
	 * @see org.openmrs.api.OrderService#getOrderTypeByConcept(org.openmrs.Concept)
	 */
	@Override
	@Transactional(readOnly = true)
	public OrderType getOrderTypeByConcept(Concept concept) {
		return Context.getOrderService().getOrderTypeByConceptClass(concept.getConceptClass());
	}

	/**
	 * @see org.openmrs.api.OrderService#getOrderTypesByClassName(String, boolean)
	 */
	@Override
	@Transactional(readOnly = true)
	public List<OrderType> getOrderTypesByClassName(String javaClassName, boolean includeRetired) throws APIException {
		return dao.getOrderTypesByClassName(javaClassName, includeRetired);
	}

	/**
	 * @see org.openmrs.api.OrderService#getOrderTypesByClassName(String, boolean, boolean)
	 */
	@Override
	@Transactional(readOnly = true)
	public List<OrderType> getOrderTypesByClassName(String javaClassName, boolean includeSubclasses, boolean includeRetired)
	        throws APIException {
		if (!StringUtils.hasText(javaClassName)) {
			throw new APIException("javaClassName cannot be null");
		}

		if (!includeSubclasses) {
			return getOrderTypesByClassName(javaClassName, includeRetired);
		}

		Class<?> superClass;
		try {
			superClass = Context.loadClass(javaClassName);
		} catch (ClassNotFoundException e) {
			throw new APIException("Invalid javaClassName: " + javaClassName, e);
		}

		return getOrderTypes(includeRetired).stream().filter(ot -> {
			try {
				Class<?> c = Context.loadClass(ot.getJavaClassName());
				return superClass.isAssignableFrom(c);
			} catch (Exception ignore) {
				return false;
			}
		}).toList();
	}

	/**
	 * @see org.openmrs.api.OrderService#getDrugRoutes()
	 */
	@Override
	@Transactional(readOnly = true)
	public List<Concept> getDrugRoutes() {
		return getSetMembersOfConceptSetFromGP(OpenmrsConstants.GP_DRUG_ROUTES_CONCEPT_UUID);
	}

	@Override
	@Transactional(readOnly = true)
	public List<Concept> getDrugDosingUnits() {
		return getSetMembersOfConceptSetFromGP(OpenmrsConstants.GP_DRUG_DOSING_UNITS_CONCEPT_UUID);
	}

	@Override
	@Transactional(readOnly = true)
	public List<Concept> getDrugDispensingUnits() {
		List<Concept> dispensingUnits = new ArrayList<>(
		        getSetMembersOfConceptSetFromGP(OpenmrsConstants.GP_DRUG_DISPENSING_UNITS_CONCEPT_UUID));
		for (Concept concept : getDrugDosingUnits()) {
			if (!dispensingUnits.contains(concept)) {
				dispensingUnits.add(concept);
			}
		}
		return dispensingUnits;
	}

	@Override
	@Transactional(readOnly = true)
	public List<Concept> getDurationUnits() {
		return getSetMembersOfConceptSetFromGP(OpenmrsConstants.GP_DURATION_UNITS_CONCEPT_UUID);
	}

	/**
	 * @see org.openmrs.api.OrderService#getTestSpecimenSources()
	 */
	@Override
	public List<Concept> getTestSpecimenSources() {
		return getSetMembersOfConceptSetFromGP(OpenmrsConstants.GP_TEST_SPECIMEN_SOURCES_CONCEPT_UUID);
	}

	@Override
	public Concept getNonCodedDrugConcept() {
		String conceptUuid = Context.getAdministrationService().getGlobalProperty(OpenmrsConstants.GP_DRUG_ORDER_DRUG_OTHER);
		if (StringUtils.hasText(conceptUuid)) {
			return Context.getConceptService().getConceptByUuid(conceptUuid);
		}
		return null;
	}

	@Override
	@Transactional(readOnly = true)
	public OrderGroup getOrderGroupByUuid(String uuid) throws APIException {
		return dao.getOrderGroupByUuid(uuid);
	}

	@Override
	@Transactional(readOnly = true)
	public OrderGroup getOrderGroup(Integer orderGroupId) throws APIException {
		return dao.getOrderGroupById(orderGroupId);
	}

	private List<Concept> getSetMembersOfConceptSetFromGP(String globalProperty) {
		String conceptUuid = Context.getAdministrationService().getGlobalProperty(globalProperty);
		Concept concept = Context.getConceptService().getConceptByUuid(conceptUuid);
		if (concept != null && concept.getSet()) {
			return concept.getSetMembers();
		}
		return List.of();
	}

	@Override
	public List<OrderGroup> getOrderGroupsByPatient(Patient patient) throws APIException {
		return dao.getOrderGroupsByPatient(patient);
	}

	@Override
	public List<OrderGroup> getOrderGroupsByEncounter(Encounter encounter) throws APIException {
		return dao.getOrderGroupsByEncounter(encounter);
	}

	/**
	 * @see org.openmrs.api.OrderService#getOrderGroupAttributeTypes()
	 */
	@Override
	@Transactional(readOnly = true)
	public List<OrderGroupAttributeType> getAllOrderGroupAttributeTypes() throws APIException {
		return dao.getAllOrderGroupAttributeTypes();
	}

	/**
	 * @see org.openmrs.api.OrderService#getOrderGroupAttributeTypeById()
	 */
	@Override
	@Transactional(readOnly = true)
	public OrderGroupAttributeType getOrderGroupAttributeType(Integer id) throws APIException {
		return dao.getOrderGroupAttributeType(id);
	}

	/**
	 * @see org.openmrs.api.OrderService#getOrderGroupAttributeTypeByUuid()
	 */
	@Override
	@Transactional(readOnly = true)
	public OrderGroupAttributeType getOrderGroupAttributeTypeByUuid(String uuid) throws APIException {
		return dao.getOrderGroupAttributeTypeByUuid(uuid);
	}

	/**
	 * @see org.openmrs.api.OrderService#saveOrderGroupAttributeType()
	 */
	@Override
	public OrderGroupAttributeType saveOrderGroupAttributeType(OrderGroupAttributeType orderGroupAttributeType)
	        throws APIException {
		return dao.saveOrderGroupAttributeType(orderGroupAttributeType);
	}

	/**
	 * @see org.openmrs.api.OrderService#retireOrderGroupAttributeType()
	 */
	@Override
	public OrderGroupAttributeType retireOrderGroupAttributeType(OrderGroupAttributeType orderGroupAttributeType,
	        String reason) throws APIException {
		return Context.getOrderService().saveOrderGroupAttributeType(orderGroupAttributeType);
	}

	/**
	 * @see org.openmrs.api.OrderService#unretireOrderGroupAttributeType()
	 */
	@Override
	public OrderGroupAttributeType unretireOrderGroupAttributeType(OrderGroupAttributeType orderGroupAttributeType)
	        throws APIException {
		return Context.getOrderService().saveOrderGroupAttributeType(orderGroupAttributeType);
	}

	/**
	 * @see org.openmrs.api.OrderService#purgeOrderGroupAttributeType()
	 */
	@Override
	public void purgeOrderGroupAttributeType(OrderGroupAttributeType orderGroupAttributeType) throws APIException {
		dao.deleteOrderGroupAttributeType(orderGroupAttributeType);
	}

	/**
	 * @see org.openmrs.api.OrderService#getOrderGroupAttributeTypeByName()
	 */
	@Override
	@Transactional(readOnly = true)
	public OrderGroupAttributeType getOrderGroupAttributeTypeByName(String orderGroupAttributeTypeName) throws APIException {
		return dao.getOrderGroupAttributeTypeByName(orderGroupAttributeTypeName);
	}

	/**
	 * @see org.openmrs.api.OrderService#getOrderGroupAttributeByUuid()
	 */
	@Override
	@Transactional(readOnly = true)
	public OrderGroupAttribute getOrderGroupAttributeByUuid(String uuid) throws APIException {
		return dao.getOrderGroupAttributeByUuid(uuid);
	}

	/**
	 * @see org.openmrs.api.OrderService#getAllOrderAttributeTypes()
	 */
	@Override
	@Transactional(readOnly = true)
	public List<OrderAttributeType> getAllOrderAttributeTypes() throws APIException {
		return dao.getAllOrderAttributeTypes();
	}

	/**
	 * @see org.openmrs.api.OrderService#getOrderAttributeTypeById(Integer)
	 */
	@Override
	@Transactional(readOnly = true)
	public OrderAttributeType getOrderAttributeTypeById(Integer id) throws APIException {
		return dao.getOrderAttributeTypeById(id);
	}

	/**
	 * @see org.openmrs.api.OrderService#getOrderAttributeTypeByUuid(String)
	 */
	@Override
	@Transactional(readOnly = true)
	public OrderAttributeType getOrderAttributeTypeByUuid(String uuid) throws APIException {
		return dao.getOrderAttributeTypeByUuid(uuid);
	}

	/**
	 * @see org.openmrs.api.OrderService#saveOrderAttributeType(OrderAttributeType)
	 */
	@Override
	public OrderAttributeType saveOrderAttributeType(OrderAttributeType orderAttributeType) throws APIException {
		return dao.saveOrderAttributeType(orderAttributeType);
	}

	/**
	 * @see org.openmrs.api.OrderService#retireOrderAttributeType(OrderAttributeType)
	 */
	@Override
	public OrderAttributeType retireOrderAttributeType(OrderAttributeType orderAttributeType, String reason)
	        throws APIException {
		return Context.getOrderService().saveOrderAttributeType(orderAttributeType);
	}

	/**
	 * @see org.openmrs.api.OrderService#unretireOrderAttributeType(OrderAttributeType)
	 */
	@Override
	public OrderAttributeType unretireOrderAttributeType(OrderAttributeType orderAttributeType) throws APIException {
		return Context.getOrderService().saveOrderAttributeType(orderAttributeType);
	}

	/**
	 * @see org.openmrs.api.OrderService#purgeOrderAttributeType(OrderAttributeType)
	 */
	@Override
	public void purgeOrderAttributeType(OrderAttributeType orderAttributeType) throws APIException {
		dao.deleteOrderAttributeType(orderAttributeType);
	}

	/**
	 * @see org.openmrs.api.OrderService#getOrderAttributeTypeByName(String)
	 */
	@Override
	@Transactional(readOnly = true)
	public OrderAttributeType getOrderAttributeTypeByName(String orderAttributeTypeName) throws APIException {
		return dao.getOrderAttributeTypeByName(orderAttributeTypeName);
	}

	/**
	 * @see org.openmrs.api.OrderService#getOrderAttributeByUuid(String)
	 */
	@Override
	@Transactional(readOnly = true)
	public OrderAttribute getOrderAttributeByUuid(String uuid) throws APIException {
		return dao.getOrderAttributeByUuid(uuid);
	}

	@Override
	@SuppressWarnings("unchecked") // Type-checked dispatch: each branch casts after verifying Class<T> type
	public <T> T getRefByUuid(Class<T> type, String uuid) {
		if (OrderType.class.equals(type)) {
			return (T) getOrderTypeByUuid(uuid);
		}
		if (CareSetting.class.equals(type)) {
			return (T) getCareSettingByUuid(uuid);
		}
		if (OrderGroup.class.equals(type)) {
			return (T) getOrderGroupByUuid(uuid);
		}
		if (OrderFrequency.class.equals(type)) {
			return (T) getOrderFrequencyByUuid(uuid);
		}
		if (OrderAttributeType.class.equals(type)) {
			return (T) getOrderAttributeTypeByUuid(uuid);
		}
		if (OrderAttribute.class.equals(type)) {
			return (T) getOrderAttributeByUuid(uuid);
		}
		if (Order.class.equals(type)) {
			return (T) getOrderByUuid(uuid);
		}
		if (OrderGroupAttribute.class.equals(type)) {
			return (T) getOrderGroupAttributeByUuid(uuid);
		}
		if (OrderGroupAttributeType.class.equals(type)) {
			return (T) getOrderGroupAttributeTypeByUuid(uuid);
		}
		throw new APIException("Unsupported type for getRefByUuid: " + type != null ? type.getName() : "null");
	}

	@Override
	public List<Class<?>> getRefTypes() {
		return Arrays.asList(OrderType.class, CareSetting.class, OrderGroup.class, OrderFrequency.class,
		    OrderAttributeType.class, OrderAttribute.class, Order.class, OrderGroupAttribute.class,
		    OrderGroupAttributeType.class);
	}

}
