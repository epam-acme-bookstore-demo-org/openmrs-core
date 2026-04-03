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

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.Concept;
import org.openmrs.ConceptName;
import org.openmrs.ConceptProposal;
import org.openmrs.Obs;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.ConceptDAO;
import org.openmrs.util.OpenmrsConstants;
import org.openmrs.validator.ValidateUtil;

/**
 * Delegate for concept proposal operations, extracted from {@link ConceptServiceImpl}.
 * <p>
 * Handles concept proposal CRUD and the mapping workflow that converts proposals into concepts or
 * synonyms. This class is not a Spring bean — it receives the DAO via constructor and is called
 * internally by {@link ConceptServiceImpl}.
 *
 * @since 2.9.0
 */
class ConceptProposalDelegate {

	private final ConceptDAO dao;

	ConceptProposalDelegate(ConceptDAO dao) {
		this.dao = dao;
	}

	/**
	 * @see org.openmrs.api.ConceptService#getConceptProposal(java.lang.Integer)
	 */
	public ConceptProposal getConceptProposal(Integer conceptProposalId) {
		return dao.getConceptProposal(conceptProposalId);
	}

	/**
	 * @see org.openmrs.api.ConceptService#getAllConceptProposals(boolean)
	 */
	public List<ConceptProposal> getAllConceptProposals(boolean includeCompleted) {
		return dao.getAllConceptProposals(includeCompleted);
	}

	/**
	 * @see org.openmrs.api.ConceptService#getConceptProposals(java.lang.String)
	 */
	public List<ConceptProposal> getConceptProposals(String cp) {
		return dao.getConceptProposals(cp);
	}

	/**
	 * @see org.openmrs.api.ConceptService#getProposedConcepts(java.lang.String)
	 */
	public List<Concept> getProposedConcepts(String text) {
		return dao.getProposedConcepts(text);
	}

	/**
	 * @see org.openmrs.api.ConceptService#saveConceptProposal(org.openmrs.ConceptProposal)
	 */
	public ConceptProposal saveConceptProposal(ConceptProposal conceptProposal) {
		return dao.saveConceptProposal(conceptProposal);
	}

	/**
	 * @see org.openmrs.api.ConceptService#purgeConceptProposal(org.openmrs.ConceptProposal)
	 */
	public void purgeConceptProposal(ConceptProposal cp) {
		dao.purgeConceptProposal(cp);
	}

	/**
	 * @see org.openmrs.api.ConceptService#mapConceptProposalToConcept(ConceptProposal, Concept, Locale)
	 */
	public Concept mapConceptProposalToConcept(ConceptProposal cp, Concept mappedConcept, Locale locale) {

		if (cp.getState().equals(OpenmrsConstants.CONCEPT_PROPOSAL_REJECT)) {
			cp.rejectConceptProposal();
			Context.getConceptService().saveConceptProposal(cp);
			return null;
		}

		if (mappedConcept == null) {
			throw new APIException("Concept.mapped.illegal", (Object[]) null);
		}

		ConceptName conceptName = null;
		if (cp.getState().equals(OpenmrsConstants.CONCEPT_PROPOSAL_CONCEPT) || StringUtils.isBlank(cp.getFinalText())) {
			cp.setState(OpenmrsConstants.CONCEPT_PROPOSAL_CONCEPT);
			cp.setFinalText("");
		} else if (cp.getState().equals(OpenmrsConstants.CONCEPT_PROPOSAL_SYNONYM)) {

			Context.getConceptService().checkIfLocked();

			String finalText = cp.getFinalText();
			conceptName = new ConceptName(finalText, null);
			conceptName.setConcept(mappedConcept);
			conceptName.setLocale(locale == null ? Context.getLocale() : locale);
			conceptName.setDateCreated(new Date());
			conceptName.setCreator(Context.getAuthenticatedUser());
			//If this is pre 1.9
			if (conceptName.getUuid() == null) {
				conceptName.setUuid(UUID.randomUUID().toString());
			}
			mappedConcept.addName(conceptName);
			mappedConcept.setChangedBy(Context.getAuthenticatedUser());
			mappedConcept.setDateChanged(new Date());
			ValidateUtil.validate(mappedConcept);
			Context.getConceptService().saveConcept(mappedConcept);
		}

		cp.setMappedConcept(mappedConcept);

		if (cp.getObsConcept() != null) {
			var ob = new Obs();
			ob.setEncounter(cp.getEncounter());
			ob.setConcept(cp.getObsConcept());
			ob.setValueCoded(cp.getMappedConcept());
			if (cp.getState().equals(OpenmrsConstants.CONCEPT_PROPOSAL_SYNONYM)) {
				ob.setValueCodedName(conceptName);
			}
			ob.setCreator(Context.getAuthenticatedUser());
			ob.setDateCreated(new Date());
			ob.setObsDatetime(cp.getEncounter().getEncounterDatetime());
			ob.setLocation(cp.getEncounter().getLocation());
			ob.setPerson(cp.getEncounter().getPatient());
			if (ob.getUuid() == null) {
				ob.setUuid(UUID.randomUUID().toString());
			}
			Context.getObsService().saveObs(ob, null);
			cp.setObs(ob);
		}

		return mappedConcept;
	}

	/**
	 * @see org.openmrs.api.ConceptService#getConceptProposalByUuid(java.lang.String)
	 */
	public ConceptProposal getConceptProposalByUuid(String uuid) {
		return dao.getConceptProposalByUuid(uuid);
	}
}
