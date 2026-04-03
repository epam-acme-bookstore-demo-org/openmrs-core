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

import org.openmrs.Concept;

/**
 * An immutable search parameter object for drugs. A convenience builder for constructing
 * instances is available via {@link #builder()}.
 *
 * @since 3.0.0
 * @see org.openmrs.api.ConceptService#getDrugs(DrugSearchCriteria)
 * @see org.openmrs.api.ConceptService#getCountOfDrugs(DrugSearchCriteria)
 */
public record DrugSearchCriteria(String drugName,Concept concept,boolean searchKeywords,boolean searchDrugConceptNames,boolean includeRetired,Integer start,Integer length){

/**
 * @return a new {@link Builder} instance
 */
public static Builder builder(){return new Builder();}

/**
 * A convenience builder for {@link DrugSearchCriteria}.
 */
public static class Builder {

	private String drugName;

	private Concept concept;

	private boolean searchKeywords;

	private boolean searchDrugConceptNames;

	private boolean includeRetired;

	private Integer start;

	private Integer length;

	public Builder drugName(String drugName) {
		this.drugName = drugName;
		return this;
	}

	public Builder concept(Concept concept) {
		this.concept = concept;
		return this;
	}

	public Builder searchKeywords(boolean searchKeywords) {
		this.searchKeywords = searchKeywords;
		return this;
	}

	public Builder searchDrugConceptNames(boolean searchDrugConceptNames) {
		this.searchDrugConceptNames = searchDrugConceptNames;
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
	 * Create a {@link DrugSearchCriteria} with the properties of this builder instance.
	 *
	 * @return a new search criteria instance
	 */
	public DrugSearchCriteria build() {
		return new DrugSearchCriteria(drugName, concept, searchKeywords, searchDrugConceptNames, includeRetired, start,
		        length);
	}
}}
