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

import java.util.Date;
import java.util.List;

import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Location;
import org.openmrs.Person;
import org.openmrs.Visit;
import org.openmrs.util.OpenmrsConstants.PERSON_TYPE;

/**
 * An immutable search parameter object for observations. A convenience builder for constructing
 * instances is available via {@link #builder()}.
 *
 * @since 3.0.0
 * @see org.openmrs.api.ObsService#getObservations(ObsSearchCriteria)
 * @see org.openmrs.api.ObsService#getObservationCount(ObsSearchCriteria)
 */
public record ObsSearchCriteria(List<Person>whom,List<Encounter>encounters,List<Concept>questions,List<Concept>answers,List<PERSON_TYPE>personTypes,List<Location>locations,List<String>sort,List<Visit>visits,Integer mostRecentN,Integer obsGroupId,Date fromDate,Date toDate,boolean includeVoidedObs,String accessionNumber){

/**
 * @return a new {@link Builder} instance
 */
public static Builder builder(){return new Builder();}

/**
 * A convenience builder for {@link ObsSearchCriteria}.
 */
public static class Builder {

	private List<Person> whom;

	private List<Encounter> encounters;

	private List<Concept> questions;

	private List<Concept> answers;

	private List<PERSON_TYPE> personTypes;

	private List<Location> locations;

	private List<String> sort;

	private List<Visit> visits;

	private Integer mostRecentN;

	private Integer obsGroupId;

	private Date fromDate;

	private Date toDate;

	private boolean includeVoidedObs;

	private String accessionNumber;

	public Builder whom(List<Person> whom) {
		this.whom = whom;
		return this;
	}

	public Builder encounters(List<Encounter> encounters) {
		this.encounters = encounters;
		return this;
	}

	public Builder questions(List<Concept> questions) {
		this.questions = questions;
		return this;
	}

	public Builder answers(List<Concept> answers) {
		this.answers = answers;
		return this;
	}

	public Builder personTypes(List<PERSON_TYPE> personTypes) {
		this.personTypes = personTypes;
		return this;
	}

	public Builder locations(List<Location> locations) {
		this.locations = locations;
		return this;
	}

	public Builder sort(List<String> sort) {
		this.sort = sort;
		return this;
	}

	public Builder visits(List<Visit> visits) {
		this.visits = visits;
		return this;
	}

	public Builder mostRecentN(Integer mostRecentN) {
		this.mostRecentN = mostRecentN;
		return this;
	}

	public Builder obsGroupId(Integer obsGroupId) {
		this.obsGroupId = obsGroupId;
		return this;
	}

	public Builder fromDate(Date fromDate) {
		this.fromDate = fromDate;
		return this;
	}

	public Builder toDate(Date toDate) {
		this.toDate = toDate;
		return this;
	}

	public Builder includeVoidedObs(boolean includeVoidedObs) {
		this.includeVoidedObs = includeVoidedObs;
		return this;
	}

	public Builder accessionNumber(String accessionNumber) {
		this.accessionNumber = accessionNumber;
		return this;
	}

	/**
	 * Create an {@link ObsSearchCriteria} with the properties of this builder instance.
	 *
	 * @return a new search criteria instance
	 */
	public ObsSearchCriteria build() {
		return new ObsSearchCriteria(whom, encounters, questions, answers, personTypes, locations, sort, visits, mostRecentN,
		        obsGroupId, fromDate, toDate, includeVoidedObs, accessionNumber);
	}
}}
