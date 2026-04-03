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

import java.util.Collection;

import org.openmrs.EncounterType;
import org.openmrs.Field;
import org.openmrs.FormField;

/**
 * An immutable search parameter object for forms. A convenience builder for constructing
 * instances is available via {@link #builder()}.
 *
 * @since 3.0.0
 * @see org.openmrs.api.FormService#getForms(FormSearchCriteria)
 * @see org.openmrs.api.FormService#getFormCount(FormSearchCriteria)
 */
public record FormSearchCriteria(String partialNameSearch,Boolean published,Collection<EncounterType>encounterTypes,Boolean retired,Collection<FormField>containingAnyFormField,Collection<FormField>containingAllFormFields,Collection<Field>fields){

/**
 * @return a new {@link Builder} instance
 */
public static Builder builder(){return new Builder();}

/**
 * A convenience builder for {@link FormSearchCriteria}.
 */
public static class Builder {

	private String partialNameSearch;

	private Boolean published;

	private Collection<EncounterType> encounterTypes;

	private Boolean retired;

	private Collection<FormField> containingAnyFormField;

	private Collection<FormField> containingAllFormFields;

	private Collection<Field> fields;

	public Builder partialNameSearch(String partialNameSearch) {
		this.partialNameSearch = partialNameSearch;
		return this;
	}

	public Builder published(Boolean published) {
		this.published = published;
		return this;
	}

	public Builder encounterTypes(Collection<EncounterType> encounterTypes) {
		this.encounterTypes = encounterTypes;
		return this;
	}

	public Builder retired(Boolean retired) {
		this.retired = retired;
		return this;
	}

	public Builder containingAnyFormField(Collection<FormField> containingAnyFormField) {
		this.containingAnyFormField = containingAnyFormField;
		return this;
	}

	public Builder containingAllFormFields(Collection<FormField> containingAllFormFields) {
		this.containingAllFormFields = containingAllFormFields;
		return this;
	}

	public Builder fields(Collection<Field> fields) {
		this.fields = fields;
		return this;
	}

	/**
	 * Create a {@link FormSearchCriteria} with the properties of this builder instance.
	 *
	 * @return a new search criteria instance
	 */
	public FormSearchCriteria build() {
		return new FormSearchCriteria(partialNameSearch, published, encounterTypes, retired, containingAnyFormField,
		        containingAllFormFields, fields);
	}
}}
