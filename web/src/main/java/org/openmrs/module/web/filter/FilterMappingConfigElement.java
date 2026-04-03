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
 * Represents the supported XML child element names within a {@code <filter-mapping>} element in a
 * module configuration file.
 *
 * @see ModuleFilterMapping#retrieveFilterMappings
 * @since 3.0.0
 */
public enum FilterMappingConfigElement {

	FILTER_NAME("filter-name"),
	URL_PATTERN("url-pattern"),
	SERVLET_NAME("servlet-name");

	private static final Map<String, FilterMappingConfigElement> BY_NODE_NAME = Stream.of(values())
	        .collect(Collectors.toUnmodifiableMap(e -> e.nodeName, e -> e));

	private final String nodeName;

	FilterMappingConfigElement(String nodeName) {
		this.nodeName = nodeName;
	}

	/**
	 * @return the XML element name
	 */
	public String getNodeName() {
		return nodeName;
	}

	/**
	 * Resolves a {@link FilterMappingConfigElement} from an XML node name.
	 *
	 * @param nodeName the XML element name
	 * @return an {@link Optional} containing the matching element, or empty if not recognized
	 */
	public static Optional<FilterMappingConfigElement> fromNodeName(String nodeName) {
		return Optional.ofNullable(BY_NODE_NAME.get(nodeName));
	}
}
