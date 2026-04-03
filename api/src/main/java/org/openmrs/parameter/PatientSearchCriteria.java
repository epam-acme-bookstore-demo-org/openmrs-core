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

import java.util.List;

import org.openmrs.PatientIdentifierType;

/**
 * An immutable search parameter object for patients. A convenience builder for constructing
 * instances is available via {@link #builder()}.
 *
 * @since 3.0.0
 * @see org.openmrs.api.PatientService#getPatients(PatientSearchCriteria)
 */
public record PatientSearchCriteria(String name,String identifier,List<PatientIdentifierType>identifierTypes,boolean matchIdentifierExactly,Integer start,Integer length){

/**
 * @return a new {@link Builder} instance
 */
public static Builder builder(){return new Builder();}

/**
 * A convenience builder for {@link PatientSearchCriteria}.
 */
public static class Builder {

	private String name;

	private String identifier;

	private List<PatientIdentifierType> identifierTypes;

	private boolean matchIdentifierExactly;

	private Integer start;

	private Integer length;

	public Builder name(String name) {
		this.name = name;
		return this;
	}

	public Builder identifier(String identifier) {
		this.identifier = identifier;
		return this;
	}

	public Builder identifierTypes(List<PatientIdentifierType> identifierTypes) {
		this.identifierTypes = identifierTypes;
		return this;
	}

	public Builder matchIdentifierExactly(boolean matchIdentifierExactly) {
		this.matchIdentifierExactly = matchIdentifierExactly;
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
	 * Create a {@link PatientSearchCriteria} with the properties of this builder instance.
	 *
	 * @return a new search criteria instance
	 */
	public PatientSearchCriteria build() {
		return new PatientSearchCriteria(name, identifier, identifierTypes, matchIdentifierExactly, start, length);
	}
}}
