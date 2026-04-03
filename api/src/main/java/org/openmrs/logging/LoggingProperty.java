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

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents the supported property keys for the {@link OpenmrsPropertyLookup} log4j2 lookup.
 * <p>
 * These correspond to properties referenced in log4j2 configuration as
 * <tt>${openmrs:&lt;property&gt;}</tt>.
 *
 * @since 2.9.x
 */
public enum LoggingProperty {

	APPLICATION_DIRECTORY("applicationDirectory"),
	LOG_LOCATION("logLocation"),
	LOG_LAYOUT("logLayout");

	private static final Map<String, LoggingProperty> BY_KEY = Stream.of(values())
	        .collect(Collectors.toUnmodifiableMap(p -> p.key, p -> p));

	private final String key;

	LoggingProperty(String key) {
		this.key = key;
	}

	/**
	 * @return the string key used in log4j2 configuration lookups
	 */
	public String getKey() {
		return key;
	}

	/**
	 * Resolves a {@link LoggingProperty} from its string key.
	 *
	 * @param key the property key string
	 * @return an {@link Optional} containing the matching property, or empty if not found
	 */
	public static Optional<LoggingProperty> fromString(String key) {
		return Optional.ofNullable(BY_KEY.get(key));
	}
}
