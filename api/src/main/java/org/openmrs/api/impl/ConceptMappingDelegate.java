/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.api.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.Concept;
import org.openmrs.ConceptMap;
import org.openmrs.ConceptMapType;
import org.openmrs.ConceptReferenceTerm;
import org.openmrs.ConceptReferenceTermMap;
import org.openmrs.ConceptSource;
import org.openmrs.Drug;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.ConceptDAO;
import org.openmrs.util.OpenmrsUtil;

/**
 * Delegate for concept mapping and reference term operations, extracted from
 * {@link ConceptServiceImpl}.
 * <p>
 * Handles concept sources, concept map types, concept reference terms, concept-to-source mappings,
 * and drug-to-source mappings. This class is not a Spring bean — it receives the DAO via
 * constructor and is called internally by {@link ConceptServiceImpl}.
 *
 * @since 2.9.0
 */
class ConceptMappingDelegate {

	private final ConceptDAO dao;

	ConceptMappingDelegate(ConceptDAO dao) {
		this.dao = dao;
	}

	// ---- Concept Source operations ----

	/**
	 * @see org.openmrs.api.ConceptService#getConceptSource(java.lang.Integer)
	 */
	public ConceptSource getConceptSource(Integer conceptSourceId) {
		return dao.getConceptSource(conceptSourceId);
	}

	/**
	 * @see org.openmrs.api.ConceptService#getAllConceptSources(boolean)
	 */
	public List<ConceptSource> getAllConceptSources(boolean includeRetired) {
		return dao.getAllConceptSources(includeRetired);
	}

	/**
	 * @see org.openmrs.api.ConceptService#getAllConceptSources()
	 */
	public List<ConceptSource> getAllConceptSources() {
		return dao.getAllConceptSources(false);
	}

	/**
	 * @see org.openmrs.api.ConceptService#getAllConceptSourcesIncludingRetired()
	 */
	public List<ConceptSource> getAllConceptSourcesIncludingRetired() {
		return dao.getAllConceptSources(true);
	}

	/**
	 * @see org.openmrs.api.ConceptService#purgeConceptSource(org.openmrs.ConceptSource)
	 */
	public ConceptSource purgeConceptSource(ConceptSource cs) {
		return dao.deleteConceptSource(cs);
	}

	/**
	 * @see org.openmrs.api.ConceptService#retireConceptSource(org.openmrs.ConceptSource, String)
	 */
	public ConceptSource retireConceptSource(ConceptSource cs, String reason) {
		// retireReason is automatically set in BaseRetireHandler
		return Context.getConceptService().saveConceptSource(cs);
	}

	/**
	 * @see org.openmrs.api.ConceptService#saveConceptSource(org.openmrs.ConceptSource)
	 */
	public ConceptSource saveConceptSource(ConceptSource conceptSource) {
		return dao.saveConceptSource(conceptSource);
	}

	/**
	 * @see org.openmrs.api.ConceptService#getConceptSourceByUuid(String)
	 */
	public ConceptSource getConceptSourceByUuid(String uuid) {
		return dao.getConceptSourceByUuid(uuid);
	}

	/**
	 * @see org.openmrs.api.ConceptService#getConceptSourceByName(java.lang.String)
	 */
	public ConceptSource getConceptSourceByName(String conceptSourceName) {
		return dao.getConceptSourceByName(conceptSourceName);
	}

	/**
	 * @see org.openmrs.api.ConceptService#getConceptSourceByUniqueId(java.lang.String)
	 */
	public ConceptSource getConceptSourceByUniqueId(String uniqueId) {
		if (uniqueId == null) {
			throw new IllegalArgumentException("uniqueId is required");
		}
		return dao.getConceptSourceByUniqueId(uniqueId);
	}

	/**
	 * @see org.openmrs.api.ConceptService#getConceptSourceByHL7Code(java.lang.String)
	 */
	public ConceptSource getConceptSourceByHL7Code(String hl7Code) {
		if (hl7Code == null) {
			throw new IllegalArgumentException("hl7Code is required");
		}
		return dao.getConceptSourceByHL7Code(hl7Code);
	}

	// ---- Concept Mapping operations ----

	/**
	 * @see org.openmrs.api.ConceptService#getConceptByMapping(java.lang.String, java.lang.String,
	 *      java.lang.Boolean)
	 */
	public Concept getConceptByMapping(String code, String sourceName, Boolean includeRetired) {
		List<Concept> concepts = Context.getConceptService().getConceptsByMapping(code, sourceName, includeRetired);

		if (concepts.isEmpty()) {
			return null;
		}
		// we want to throw an exception if there is more than one non-retired concept;
		// since the getConceptByMapping DAO method returns a list with all non-retired concept
		// sorted to the front of the list, we can test if there is more than one retired concept
		// by testing if the second concept in the list is retired or not
		else if (concepts.size() > 1 && !concepts.get(1).getRetired()) {
			throw new APIException("Concept.error.multiple.non.retired", new Object[] { code, sourceName });
		} else {
			return concepts.getFirst();
		}
	}

	/**
	 * @see org.openmrs.api.ConceptService#getConceptsByMapping(java.lang.String, java.lang.String,
	 *      boolean)
	 */
	public List<Concept> getConceptsByMapping(String code, String sourceName, boolean includeRetired) {
		var concepts = new ArrayList<Concept>();
		for (Integer conceptId : Context.getConceptService().getConceptIdsByMapping(code, sourceName, includeRetired)) {
			concepts.add(dao.getConcept(conceptId));
		}
		return concepts;
	}

	/**
	 * @see org.openmrs.api.ConceptService#getConceptIdsByMapping(java.lang.String, java.lang.String,
	 *      boolean)
	 */
	public List<Integer> getConceptIdsByMapping(String code, String sourceName, boolean includeRetired) {
		return dao.getConceptIdsByMapping(code, sourceName, includeRetired);
	}

	/**
	 * @see org.openmrs.api.ConceptService#getConceptMappingsToSource(ConceptSource)
	 */
	public List<ConceptMap> getConceptMappingsToSource(ConceptSource conceptSource) {
		return dao.getConceptMapsBySource(conceptSource);
	}

	// ---- Concept Map Type operations ----

	/**
	 * @see org.openmrs.api.ConceptService#getActiveConceptMapTypes()
	 */
	public List<ConceptMapType> getActiveConceptMapTypes() {
		return Context.getConceptService().getConceptMapTypes(true, false);
	}

	/**
	 * @see org.openmrs.api.ConceptService#getConceptMapTypes(boolean, boolean)
	 */
	public List<ConceptMapType> getConceptMapTypes(boolean includeRetired, boolean includeHidden) {
		return dao.getConceptMapTypes(includeRetired, includeHidden);
	}

	/**
	 * @see org.openmrs.api.ConceptService#getConceptMapType(Integer)
	 */
	public ConceptMapType getConceptMapType(Integer conceptMapTypeId) {
		return dao.getConceptMapType(conceptMapTypeId);
	}

	/**
	 * @see org.openmrs.api.ConceptService#getConceptMapTypeByUuid(String)
	 */
	public ConceptMapType getConceptMapTypeByUuid(String uuid) {
		return dao.getConceptMapTypeByUuid(uuid);
	}

	/**
	 * @see org.openmrs.api.ConceptService#getConceptMapTypeByName(java.lang.String)
	 */
	public ConceptMapType getConceptMapTypeByName(String name) {
		return dao.getConceptMapTypeByName(name);
	}

	/**
	 * @see org.openmrs.api.ConceptService#saveConceptMapType(org.openmrs.ConceptMapType)
	 */
	public ConceptMapType saveConceptMapType(ConceptMapType conceptMapType) {
		return dao.saveConceptMapType(conceptMapType);
	}

	/**
	 * @see org.openmrs.api.ConceptService#retireConceptMapType(org.openmrs.ConceptMapType,
	 *      java.lang.String)
	 */
	public ConceptMapType retireConceptMapType(ConceptMapType conceptMapType, String retireReason) {
		String tmpRetireReason = retireReason;
		if (StringUtils.isBlank(tmpRetireReason)) {
			tmpRetireReason = Context.getMessageSourceService().getMessage("general.default.retireReason");
		}
		conceptMapType.setRetireReason(tmpRetireReason);
		return dao.saveConceptMapType(conceptMapType);
	}

	/**
	 * @see org.openmrs.api.ConceptService#unretireConceptMapType(org.openmrs.ConceptMapType)
	 */
	public ConceptMapType unretireConceptMapType(ConceptMapType conceptMapType) {
		return Context.getConceptService().saveConceptMapType(conceptMapType);
	}

	/**
	 * @see org.openmrs.api.ConceptService#purgeConceptMapType(org.openmrs.ConceptMapType)
	 */
	public void purgeConceptMapType(ConceptMapType conceptMapType) {
		if (dao.isConceptMapTypeInUse(conceptMapType)) {
			throw new APIException("ConceptMapType.inUse", (Object[]) null);
		}
		dao.deleteConceptMapType(conceptMapType);
	}

	// ---- Concept Reference Term operations ----

	/**
	 * @see org.openmrs.api.ConceptService#getAllConceptReferenceTerms()
	 */
	public List<ConceptReferenceTerm> getAllConceptReferenceTerms() {
		return Context.getConceptService().getConceptReferenceTerms(true);
	}

	/**
	 * @see org.openmrs.api.ConceptService#getConceptReferenceTerms(boolean)
	 */
	public List<ConceptReferenceTerm> getConceptReferenceTerms(boolean includeRetired) {
		return dao.getConceptReferenceTerms(includeRetired);
	}

	/**
	 * @see org.openmrs.api.ConceptService#getConceptReferenceTerm(java.lang.Integer)
	 */
	public ConceptReferenceTerm getConceptReferenceTerm(Integer conceptReferenceTermId) {
		return dao.getConceptReferenceTerm(conceptReferenceTermId);
	}

	/**
	 * @see org.openmrs.api.ConceptService#getConceptReferenceTermByUuid(java.lang.String)
	 */
	public ConceptReferenceTerm getConceptReferenceTermByUuid(String uuid) {
		return dao.getConceptReferenceTermByUuid(uuid);
	}

	/**
	 * @see org.openmrs.api.ConceptService#getConceptReferenceTermByName(java.lang.String,
	 *      org.openmrs.ConceptSource)
	 */
	public ConceptReferenceTerm getConceptReferenceTermByName(String name, ConceptSource conceptSource) {
		//On addition of extra attributes to concept maps, terms that were generated from existing maps have
		//empty string values for the name property, ignore the search when name is an empty string but allow
		//white space characters
		if (StringUtils.isBlank(name)) {
			return null;
		}
		return dao.getConceptReferenceTermByName(name, conceptSource);
	}

	/**
	 * @see org.openmrs.api.ConceptService#getConceptReferenceTermByCode(java.lang.String,
	 *      org.openmrs.ConceptSource)
	 */
	public ConceptReferenceTerm getConceptReferenceTermByCode(String code, ConceptSource conceptSource) {
		return dao.getConceptReferenceTermByCode(code, conceptSource);
	}

	/**
	 * @see org.openmrs.api.ConceptService#getConceptReferenceTermByCode(java.lang.String,
	 *      org.openmrs.ConceptSource, boolean)
	 */
	public List<ConceptReferenceTerm> getConceptReferenceTermByCode(String code, ConceptSource conceptSource,
	        boolean includeRetired) {
		return dao.getConceptReferenceTermByCode(code, conceptSource, includeRetired);
	}

	/**
	 * @see org.openmrs.api.ConceptService#saveConceptReferenceTerm(org.openmrs.ConceptReferenceTerm)
	 */
	public ConceptReferenceTerm saveConceptReferenceTerm(ConceptReferenceTerm conceptReferenceTerm) {
		return dao.saveConceptReferenceTerm(conceptReferenceTerm);
	}

	/**
	 * @see org.openmrs.api.ConceptService#retireConceptReferenceTerm(ConceptReferenceTerm, String)
	 */
	public ConceptReferenceTerm retireConceptReferenceTerm(ConceptReferenceTerm conceptReferenceTerm, String retireReason) {
		String tmpRetireReason = retireReason;
		if (StringUtils.isBlank(tmpRetireReason)) {
			tmpRetireReason = Context.getMessageSourceService().getMessage("general.default.retireReason");
		}
		conceptReferenceTerm.setRetireReason(tmpRetireReason);
		return Context.getConceptService().saveConceptReferenceTerm(conceptReferenceTerm);
	}

	/**
	 * @see org.openmrs.api.ConceptService#unretireConceptReferenceTerm(org.openmrs.ConceptReferenceTerm)
	 */
	public ConceptReferenceTerm unretireConceptReferenceTerm(ConceptReferenceTerm conceptReferenceTerm) {
		return Context.getConceptService().saveConceptReferenceTerm(conceptReferenceTerm);
	}

	/**
	 * @see org.openmrs.api.ConceptService#purgeConceptReferenceTerm(org.openmrs.ConceptReferenceTerm)
	 */
	public void purgeConceptReferenceTerm(ConceptReferenceTerm conceptReferenceTerm) {
		if (dao.isConceptReferenceTermInUse(conceptReferenceTerm)) {
			throw new APIException("ConceptRefereceTerm.inUse", (Object[]) null);
		}
		dao.deleteConceptReferenceTerm(conceptReferenceTerm);
	}

	/**
	 * @see org.openmrs.api.ConceptService#getConceptReferenceTerms(java.lang.String,
	 *      org.openmrs.ConceptSource, java.lang.Integer, java.lang.Integer, boolean)
	 */
	public List<ConceptReferenceTerm> getConceptReferenceTerms(String query, ConceptSource conceptSource, Integer start,
	        Integer length, boolean includeRetired) {
		Integer tmpLength = length;
		if (tmpLength == null) {
			tmpLength = 10000;
		}
		return dao.getConceptReferenceTerms(query, conceptSource, start, tmpLength, includeRetired);
	}

	/**
	 * @see org.openmrs.api.ConceptService#getCountOfConceptReferenceTerms(String, ConceptSource,
	 *      boolean)
	 */
	public Integer getCountOfConceptReferenceTerms(String query, ConceptSource conceptSource, boolean includeRetired) {
		return OpenmrsUtil.convertToInteger(dao.getCountOfConceptReferenceTerms(query, conceptSource, includeRetired));
	}

	/**
	 * @see org.openmrs.api.ConceptService#getReferenceTermMappingsTo(ConceptReferenceTerm)
	 */
	public List<ConceptReferenceTermMap> getReferenceTermMappingsTo(ConceptReferenceTerm term) {
		return dao.getReferenceTermMappingsTo(term);
	}

	/**
	 * @see org.openmrs.api.ConceptService#getDefaultConceptMapType()
	 */
	public ConceptMapType getDefaultConceptMapType() {
		//We need to fetch it in DAO since it must be done in the MANUAL flush mode to prevent pre-mature flushes.
		return dao.getDefaultConceptMapType();
	}

	// ---- Drug Mapping operations ----

	/**
	 * @see org.openmrs.api.ConceptService#getDrugsByMapping(String, ConceptSource, Collection, boolean)
	 */
	public List<Drug> getDrugsByMapping(String code, ConceptSource conceptSource,
	        Collection<ConceptMapType> withAnyOfTheseTypes, boolean includeRetired) {
		Collection<ConceptMapType> tmpWithAnyOfTheseTypes = withAnyOfTheseTypes == null ? List.of() : withAnyOfTheseTypes;

		if (conceptSource == null) {
			throw new APIException("ConceptSource.is.required", (Object[]) null);
		}

		return dao.getDrugsByMapping(code, conceptSource, tmpWithAnyOfTheseTypes, includeRetired);
	}

	/**
	 * @see org.openmrs.api.ConceptService#getDrugByMapping(String, org.openmrs.ConceptSource,
	 *      java.util.Collection)
	 */
	public Drug getDrugByMapping(String code, ConceptSource conceptSource,
	        Collection<ConceptMapType> withAnyOfTheseTypesOrOrderOfPreference) {
		Collection<ConceptMapType> tmpWithAnyOfTheseTypesOrOrderOfPreference = withAnyOfTheseTypesOrOrderOfPreference == null
		        ? List.of()
		        : withAnyOfTheseTypesOrOrderOfPreference;

		if (conceptSource == null) {
			throw new APIException("ConceptSource.is.required", (Object[]) null);
		}

		return dao.getDrugByMapping(code, conceptSource, tmpWithAnyOfTheseTypesOrOrderOfPreference);
	}
}
