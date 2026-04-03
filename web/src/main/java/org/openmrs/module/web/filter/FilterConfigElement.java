/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.web.filter;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents the supported XML child element names within a {@code <filter>} element in a module
 * configuration file.
 *
 * @see ModuleFilterDefinition#retrieveFilterDefinitions
 * @since 2.9.x
 */
public enum FilterConfigElement {

	FILTER_NAME("filter-name"),
	FILTER_CLASS("filter-class"),
	INIT_PARAM("init-param");

	private static final Map<String, FilterConfigElement> BY_NODE_NAME = Stream.of(values())
	        .collect(Collectors.toUnmodifiableMap(e -> e.nodeName, e -> e));

	private final String nodeName;

	FilterConfigElement(String nodeName) {
		this.nodeName = nodeName;
	}

	/**
	 * @return the XML element name
	 */
	public String getNodeName() {
		return nodeName;
	}

	/**
	 * Resolves a {@link FilterConfigElement} from an XML node name.
	 *
	 * @param nodeName the XML element name
	 * @return an {@link Optional} containing the matching element, or empty if not recognized
	 */
	public static Optional<FilterConfigElement> fromNodeName(String nodeName) {
		return Optional.ofNullable(BY_NODE_NAME.get(nodeName));
	}
}
