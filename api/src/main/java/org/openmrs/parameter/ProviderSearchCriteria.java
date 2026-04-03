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

import org.openmrs.ProviderAttributeType;

/**
 * An immutable search parameter object for providers. A convenience builder for constructing
 * instances is available via {@link #builder()}.
 *
 * @since 3.0.0
 * @see org.openmrs.api.ProviderService#getProviders(ProviderSearchCriteria)
 */
public record ProviderSearchCriteria(String query,Integer start,Integer length,Map<ProviderAttributeType,Object>attributes,boolean includeRetired){

/**
 * @return a new {@link Builder} instance
 */
public static Builder builder(){return new Builder();}

/**
 * A convenience builder for {@link ProviderSearchCriteria}.
 */
public static class Builder {

	private String query;

	private Integer start;

	private Integer length;

	private Map<ProviderAttributeType, Object> attributes;

	private boolean includeRetired;

	public Builder query(String query) {
		this.query = query;
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

	public Builder attributes(Map<ProviderAttributeType, Object> attributes) {
		this.attributes = attributes;
		return this;
	}

	public Builder includeRetired(boolean includeRetired) {
		this.includeRetired = includeRetired;
		return this;
	}

	/**
	 * Create a {@link ProviderSearchCriteria} with the properties of this builder instance.
	 *
	 * @return a new search criteria instance
	 */
	public ProviderSearchCriteria build() {
		return new ProviderSearchCriteria(query, start, length, attributes, includeRetired);
	}
}}
