/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.logic;

import org.openmrs.logic.op.ComparisonOperator;
import org.openmrs.logic.op.Operand;

/**
 * Represents a duration of time &mdash; e.g., one year, two weeks, or 18 months. Used within
 * criteria. Use the <code>Duration duration = Duration.days(5.0);</code> to get a duration object
 *
 * @see org.openmrs.logic.LogicCriteria
 */
public class Duration implements Operand {

	public static enum Units {
		SECONDS,
		MINUTES,
		HOURS,
		DAYS,
		WEEKS,
		MONTHS,
		YEARS
	}

	private Double duration;

	private Units units;

	/**
	 * Private constructor used by the static methods on this class. Use the
	 * <code>Duration duration = Duration.days(5.0);</code> to get a duration object
	 *
	 * @param duration
	 * @param units
	 */
	private Duration(Double duration, Units units) {
		this.duration = duration;
		this.units = units;
	}

	public Double getDuration() {
		return duration;
	}

	public Units getUnits() {
		return units;
	}

	/**
	 * Returns the equivalent duration in days
	 *
	 * @return the equivalent duration in days
	 */
	public Double getDurationInDays() {
		return switch (units) {
			case SECONDS -> duration / 86400;
			case MINUTES -> duration / 1440;
			case HOURS -> duration / 24;
			case DAYS -> duration;
			case WEEKS -> duration * 7;
			case MONTHS -> duration * 30;
			case YEARS -> duration * 365;
			default -> 0d;
		};
	}

	/**
	 * Returns the equivalent duration in milliseconds
	 *
	 * @return the equivalent duration in milliseconds
	 */
	public long getDurationInMillis() {
		long d = duration.longValue();
		return switch (units) {
			case SECONDS -> d * 1000;
			case MINUTES -> d * 60000;
			case HOURS -> d * 3600000;
			case DAYS -> d * 86400000;
			case WEEKS -> d * 10080000;
			case MONTHS -> d * 2628000000L;
			case YEARS -> d * 31536000000L;
			default -> 0;
		};
	}

	/**
	 * Returns a duration for the given number of seconds
	 *
	 * @param duration number of seconds for duration
	 * @return <code>Duration</code> object for given number of seconds
	 */
	public static Duration seconds(Double duration) {
		return new Duration(duration, Units.SECONDS);
	}

	/**
	 * Returns a duration for the given number of seconds
	 *
	 * @param duration number of seconds for duration
	 * @return <code>Duration</code> object for given number of seconds
	 */
	public static Duration seconds(int duration) {
		return seconds(Double.valueOf(duration));
	}

	/**
	 * Returns a duration for the given number of minutes
	 *
	 * @param duration number of minutes for duration
	 * @return <code>Duration</code> object for given number of minutes
	 */
	public static Duration minutes(Double duration) {
		return new Duration(duration, Units.MINUTES);
	}

	/**
	 * Returns a duration for the given number of minutes
	 *
	 * @param duration number of minutes for duration
	 * @return <code>Duration</code> object for given number of minutes
	 */
	public static Duration minutes(int duration) {
		return minutes(Double.valueOf(duration));
	}

	/**
	 * Returns a duration for the given number of hours
	 *
	 * @param duration number of hours for duration
	 * @return <code>Duration</code> object for given number of hours
	 */
	public static Duration hours(Double duration) {
		return new Duration(duration, Units.HOURS);
	}

	/**
	 * Returns a duration for the given number of hours
	 *
	 * @param duration number of hours for duration
	 * @return <code>Duration</code> object for given number of hours
	 */
	public static Duration hours(int duration) {
		return hours(Double.valueOf(duration));
	}

	/**
	 * Returns a duration for the given number of days
	 *
	 * @param duration number of days for duration
	 * @return <code>Duration</code> object with specified number of days
	 */
	public static Duration days(Double duration) {
		return new Duration(duration, Units.DAYS);
	}

	/**
	 * Returns a duration for the given number of days
	 *
	 * @param duration number of days for duration
	 * @return <code>Duration</code> object with specified number of days
	 */
	public static Duration days(int duration) {
		return days(Double.valueOf(duration));
	}

	/**
	 * Returns a duration for the given number of weeks
	 *
	 * @param duration number of weeks for duration
	 * @return <code>Duration</code> object with specified number of weeks
	 */
	public static Duration weeks(Double duration) {
		return new Duration(duration, Units.WEEKS);
	}

	/**
	 * Returns a duration for the given number of weeks
	 *
	 * @param duration number of weeks for duration
	 * @return <code>Duration</code> object with specified number of weeks
	 */
	public static Duration weeks(int duration) {
		return weeks(Double.valueOf(duration));
	}

	/**
	 * Returns a duration for the given number of months
	 *
	 * @param duration number of months for duration
	 * @return <code>Duration</code> object with specified number of months
	 */
	public static Duration months(Double duration) {
		return new Duration(duration, Units.MONTHS);
	}

	/**
	 * Returns a duration for the given number of months
	 *
	 * @param duration number of months for duration
	 * @return <code>Duration</code> object with specified number of months
	 */
	public static Duration months(int duration) {
		return months(Double.valueOf(duration));
	}

	/**
	 * Returns a duration for the given number of years
	 *
	 * @param duration number of years for duration
	 * @return <code>Duration</code> object with specified number of years
	 */
	public static Duration years(Double duration) {
		return new Duration(duration, Units.YEARS);
	}

	/**
	 * Returns a duration for the given number of years
	 *
	 * @param duration number of years for duration
	 * @return <code>Duration</code> object with specified number of years
	 */
	public static Duration years(int duration) {
		return years(Double.valueOf(duration));
	}

	/**
	 * @see org.openmrs.logic.op.Operand#supports(org.openmrs.logic.op.ComparisonOperator)
	 */
	@Override
	public boolean supports(ComparisonOperator operator) {
		return (ComparisonOperator.WITHIN.equals(operator));
	}

}
