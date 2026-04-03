/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.parameter;

import java.util.Map;

import org.openmrs.Location;
import org.openmrs.LocationAttributeType;

/**
 * An immutable search parameter object for locations. A convenience builder for constructing
 * instances is available via {@link #builder()}.
 *
 * @since 3.0.0
 * @see org.openmrs.api.LocationService#getLocations(LocationSearchCriteria)
 */
public record LocationSearchCriteria(String nameFragment,Location parent,Map<LocationAttributeType,Object>attributeValues,boolean includeRetired,Integer start,Integer length){

/**
 * @return a new {@link Builder} instance
 */
public static Builder builder(){return new Builder();}

/**
 * A convenience builder for {@link LocationSearchCriteria}.
 */
public static class Builder {

	private String nameFragment;

	private Location parent;

	private Map<LocationAttributeType, Object> attributeValues;

	private boolean includeRetired;

	private Integer start;

	private Integer length;

	public Builder nameFragment(String nameFragment) {
		this.nameFragment = nameFragment;
		return this;
	}

	public Builder parent(Location parent) {
		this.parent = parent;
		return this;
	}

	public Builder attributeValues(Map<LocationAttributeType, Object> attributeValues) {
		this.attributeValues = attributeValues;
		return this;
	}

	public Builder includeRetired(boolean includeRetired) {
		this.includeRetired = includeRetired;
		return this;
	}

	public Builder start(Integer start) {
		this.start = start;
		return this;
	}

	public Builder length(Integer length) {
		this.length = length;
		return this;
	}

	/**
	 * Create a {@link LocationSearchCriteria} with the properties of this builder instance.
	 *
	 * @return a new search criteria instance
	 */
	public LocationSearchCriteria build() {
		return new LocationSearchCriteria(nameFragment, parent, attributeValues, includeRetired, start, length);
	}
}}
