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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Represents the supported log4j2 configuration file types for the
 * {@link OpenmrsConfigurationFactory}.
 *
 * @since 2.9.x
 */
public enum ConfigurationFileType {

	XML("xml"),
	YAML("yaml"),
	JSON("json");

	private static final Map<String, ConfigurationFileType> BY_EXTENSION;

	static {
		Map<String, ConfigurationFileType> map = new HashMap<>();
		for (ConfigurationFileType type : values()) {
			map.put(type.extension, type);
		}
		map.put("yml", YAML);
		BY_EXTENSION = Map.copyOf(map);
	}

	private final String extension;

	ConfigurationFileType(String extension) {
		this.extension = extension;
	}

	/**
	 * @return the canonical file extension for this configuration type
	 */
	public String getExtension() {
		return extension;
	}

	/**
	 * Resolves a {@link ConfigurationFileType} from a file extension string. Both "yaml" and "yml"
	 * resolve to {@link #YAML}.
	 *
	 * @param extension the file extension (without leading dot)
	 * @return an {@link Optional} containing the matching type, or empty if not recognized
	 */
	public static Optional<ConfigurationFileType> fromExtension(String extension) {
		return Optional.ofNullable(BY_EXTENSION.get(extension));
	}
}
