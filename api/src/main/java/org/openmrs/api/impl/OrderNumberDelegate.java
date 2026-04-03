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

import java.lang.reflect.Field;
import java.util.Calendar;
import java.util.Date;
import java.util.function.Supplier;

import org.apache.commons.lang3.time.DateUtils;
import org.openmrs.Order;
import org.openmrs.api.APIException;
import org.openmrs.api.CannotStopDiscontinuationOrderException;
import org.openmrs.api.CannotStopInactiveOrderException;
import org.openmrs.api.OrderContext;
import org.openmrs.api.OrderNumberGenerator;
import org.openmrs.api.db.OrderDAO;
import org.openmrs.util.ConfigUtil;
import org.openmrs.util.OpenmrsConstants;

import static org.openmrs.Order.Action.DISCONTINUE;

/**
 * Delegate that handles order numbering, internal save mechanics, and order stop/discontinue
 * operations, extracted from {@link OrderServiceImpl} to reduce class size and improve
 * maintainability.
 * <p>
 * This is an internal implementation class and should not be used directly by external code.
 */
class OrderNumberDelegate {

	private final OrderDAO dao;

	private final Supplier<OrderNumberGenerator> generatorSupplier;

	OrderNumberDelegate(OrderDAO dao, Supplier<OrderNumberGenerator> generatorSupplier) {
		this.dao = dao;
		this.generatorSupplier = generatorSupplier;
	}

	/**
	 * Internal save method that assigns an order number for new orders, adjusts auto-expire dates, and
	 * persists via the DAO.
	 */
	Order saveOrderInternal(Order order, OrderContext orderContext) {
		if (order.getOrderId() == null) {
			if (order.getOrderNumber() == null) {
				setProperty(order, "orderNumber", generatorSupplier.get().getNewOrderNumber(orderContext));
			}

			//DC orders should auto expire upon creating them
			if (DISCONTINUE == order.getAction()) {
				order.setAutoExpireDate(order.getDateActivated());
			} else if (order.getAutoExpireDate() != null) {
				Calendar cal = Calendar.getInstance();
				cal.setTime(order.getAutoExpireDate());
				int hours = cal.get(Calendar.HOUR_OF_DAY);
				int minutes = cal.get(Calendar.MINUTE);
				int seconds = cal.get(Calendar.SECOND);
				cal.get(Calendar.MILLISECOND);
				//roll autoExpireDate to end of day (23:59:59) if no time portion is specified
				if (hours == 0 && minutes == 0 && seconds == 0) {
					cal.set(Calendar.HOUR_OF_DAY, 23);
					cal.set(Calendar.MINUTE, 59);
					cal.set(Calendar.SECOND, 59);
					// the OpenMRS database is only precise to the second
					cal.set(Calendar.MILLISECOND, 0);
					order.setAutoExpireDate(cal.getTime());
				}
			}
		}

		return dao.saveOrder(order);
	}

	void setProperty(Order order, String propertyName, Object value) {
		Boolean isAccessible = null;
		Field field = null;
		try {
			field = Order.class.getDeclaredField(propertyName);
			field.setAccessible(true);
			field.set(order, value);
		} catch (Exception e) {
			throw new APIException("Order.failed.set.property", new Object[] { propertyName, order }, e);
		} finally {
			if (field != null && isAccessible != null) {
				field.setAccessible(isAccessible);
			}
		}
	}

	/**
	 * To support MySQL datetime values (which are only precise to the second) we subtract one second.
	 * Eventually we may move this method and enhance it to subtract the smallest moment the underlying
	 * database will represent.
	 *
	 * @param date the date
	 * @return one moment before date
	 */
	Date aMomentBefore(Date date) {
		return DateUtils.addSeconds(date, -1);
	}

	/**
	 * Make necessary checks, set necessary fields for stopping an order and save.
	 *
	 * @param orderToStop the order to stop
	 * @param discontinueDate the date to set as stopped
	 * @param isRetrospective whether this is a retrospective operation
	 */
	void stopOrder(Order orderToStop, Date discontinueDate, boolean isRetrospective) {
		if (discontinueDate == null) {
			discontinueDate = new Date();
		}
		if (discontinueDate.after(new Date())) {
			throw new IllegalArgumentException("Discontinue date cannot be in the future");
		}
		if (DISCONTINUE == orderToStop.getAction()) {
			throw new CannotStopDiscontinuationOrderException();
		}

		if (!ConfigUtil.getProperty(OpenmrsConstants.GP_ALLOW_SETTING_STOP_DATE_ON_INACTIVE_ORDERS, false)) {
			if (isRetrospective && orderToStop.getDateStopped() != null) {
				throw new CannotStopInactiveOrderException();
			}
			if (!isRetrospective && !orderToStop.isActive()) {
				throw new CannotStopInactiveOrderException();
			} else if (isRetrospective && !orderToStop.isActive(discontinueDate)) {
				throw new CannotStopInactiveOrderException();
			}
		}

		setProperty(orderToStop, "dateStopped", discontinueDate);
		saveOrderInternal(orderToStop, null);
	}
}
