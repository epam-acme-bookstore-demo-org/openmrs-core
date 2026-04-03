/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.logging;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.Level;

/**
 * Represents the valid OpenMRS log levels, mapping each to the corresponding Log4j2 {@link Level}.
 *
 * @since 2.9.x
 */
public enum OpenmrsLogLevel {

	TRACE(Level.TRACE),
	DEBUG(Level.DEBUG),
	INFO(Level.INFO),
	WARN(Level.WARN),
	ERROR(Level.ERROR),
	FATAL(Level.FATAL);

	private static final Map<String, OpenmrsLogLevel> BY_NAME = Stream.of(values())
	        .collect(Collectors.toUnmodifiableMap(l -> l.name().toLowerCase(Locale.ROOT), l -> l));

	private final Level log4jLevel;

	OpenmrsLogLevel(Level log4jLevel) {
		this.log4jLevel = log4jLevel;
	}

	/**
	 * @return the corresponding Log4j2 {@link Level}
	 */
	public Level getLog4jLevel() {
		return log4jLevel;
	}

	/**
	 * Resolves an {@link OpenmrsLogLevel} from a case-insensitive string.
	 *
	 * @param level the log level string (e.g. "trace", "DEBUG")
	 * @return an {@link Optional} containing the matching level, or empty if not recognized
	 */
	public static Optional<OpenmrsLogLevel> fromString(String level) {
		if (level == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(BY_NAME.get(level.toLowerCase(Locale.ROOT)));
	}
}
