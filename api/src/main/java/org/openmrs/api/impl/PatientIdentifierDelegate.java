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
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.openmrs.BaseOpenmrsMetadata;
import org.openmrs.Concept;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PersonAddress;
import org.openmrs.PersonName;
import org.openmrs.api.APIException;
import org.openmrs.api.BlankIdentifierException;
import org.openmrs.api.InsufficientIdentifiersException;
import org.openmrs.api.MissingRequiredIdentifierException;
import org.openmrs.api.PatientIdentifierException;
import org.openmrs.api.PatientIdentifierTypeLockedException;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.PatientDAO;
import org.openmrs.util.OpenmrsConstants;
import org.openmrs.util.PrivilegeConstants;
import org.openmrs.validator.PatientIdentifierValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Delegate that handles patient identifier validation, save-preparation checks, and patient
 * lifecycle operations (exit from care, process death), extracted from {@link PatientServiceImpl}
 * to reduce class size and improve maintainability.
 * <p>
 * This is an internal implementation class and should not be used directly by external code.
 */
class PatientIdentifierDelegate {

	private static final Logger log = LoggerFactory.getLogger(PatientIdentifierDelegate.class);

	private final PatientDAO dao;

	PatientIdentifierDelegate(PatientDAO dao) {
		this.dao = dao;
	}

	void requireAppropriatePatientModificationPrivilege(Patient patient) {
		if (patient.getPatientId() == null) {
			Context.requirePrivilege(PrivilegeConstants.ADD_PATIENTS);
		} else {
			Context.requirePrivilege(PrivilegeConstants.EDIT_PATIENTS);
		}
		if (patient.getVoided()) {
			Context.requirePrivilege(PrivilegeConstants.DELETE_PATIENTS);
		}
	}

	void setPreferredPatientIdentifier(Patient patient) {
		PatientIdentifier preferredIdentifier = null;
		PatientIdentifier possiblePreferredId = patient.getPatientIdentifier();
		if (possiblePreferredId != null && possiblePreferredId.getPreferred() && !possiblePreferredId.getVoided()) {
			preferredIdentifier = possiblePreferredId;
		}

		for (PatientIdentifier id : patient.getIdentifiers()) {
			if (preferredIdentifier == null && !id.getVoided()) {
				id.setPreferred(true);
				preferredIdentifier = id;
				continue;
			}

			if (!id.equals(preferredIdentifier)) {
				id.setPreferred(false);
			}
		}
	}

	void setPreferredPatientName(Patient patient) {
		PersonName preferredName = null;
		PersonName possiblePreferredName = patient.getPersonName();
		if (possiblePreferredName != null && possiblePreferredName.getPreferred() && !possiblePreferredName.getVoided()) {
			preferredName = possiblePreferredName;
		}

		for (PersonName name : patient.getNames()) {
			if (preferredName == null && !name.getVoided()) {
				name.setPreferred(true);
				preferredName = name;
				continue;
			}

			if (!name.equals(preferredName)) {
				name.setPreferred(false);
			}
		}
	}

	void setPreferredPatientAddress(Patient patient) {
		PersonAddress preferredAddress = null;
		PersonAddress possiblePreferredAddress = patient.getPersonAddress();
		if (possiblePreferredAddress != null && possiblePreferredAddress.getPreferred()
		        && !possiblePreferredAddress.getVoided()) {
			preferredAddress = possiblePreferredAddress;
		}

		for (PersonAddress address : patient.getAddresses()) {
			if (preferredAddress == null && !address.getVoided()) {
				address.setPreferred(true);
				preferredAddress = address;
				continue;
			}

			if (!address.equals(preferredAddress)) {
				address.setPreferred(false);
			}
		}
	}

	/**
	 * @see org.openmrs.api.PatientService#checkPatientIdentifiers(org.openmrs.Patient)
	 */
	void checkPatientIdentifiers(Patient patient) throws PatientIdentifierException {
		// check patient has at least one identifier
		if (!patient.getVoided() && patient.getActiveIdentifiers().isEmpty()) {
			throw new InsufficientIdentifiersException("At least one nonvoided Patient Identifier is required");
		}

		final List<PatientIdentifier> patientIdentifiers = new ArrayList<>(patient.getIdentifiers());

		patientIdentifiers.stream().filter(pi -> !pi.getVoided()).forEach(pi -> {
			try {
				PatientIdentifierValidator.validateIdentifier(pi);
			} catch (BlankIdentifierException bie) {
				patient.removeIdentifier(pi);
				throw bie;
			}
		});

		checkForMissingRequiredIdentifiers(patientIdentifiers);
	}

	/**
	 * @see org.openmrs.api.PatientService#checkIfPatientIdentifierTypesAreLocked()
	 */
	void checkIfPatientIdentifierTypesAreLocked() {
		String locked = Context.getAdministrationService()
		        .getGlobalProperty(OpenmrsConstants.GLOBAL_PROPERTY_PATIENT_IDENTIFIER_TYPES_LOCKED, "false");
		if ("true".equalsIgnoreCase(locked)) {
			throw new PatientIdentifierTypeLockedException();
		}
	}

	/**
	 * Establishes that a patient has left care. Creates relevant observations.
	 */
	void exitFromCare(Patient patient, Date dateExited, Concept reasonForExit) throws APIException {

		if (patient == null) {
			throw new APIException("Patient.invalid.care", (Object[]) null);
		}
		if (dateExited == null) {
			throw new APIException("Patient.no.valid.dateExited", (Object[]) null);
		}
		if (reasonForExit == null) {
			throw new APIException("Patient.no.valid.reasonForExit", (Object[]) null);
		}

		// need to create an observation to represent this (otherwise how
		// will we know?)
		saveReasonForExitObs(patient, dateExited, reasonForExit);
	}

	/**
	 * @see org.openmrs.api.PatientService#saveCauseOfDeathObs(org.openmrs.Patient, java.util.Date,
	 *      org.openmrs.Concept, java.lang.String)
	 */
	void saveCauseOfDeathObs(Patient patient, Date deathDate, Concept cause, String otherReason) throws APIException {

		if (patient == null) {
			throw new APIException("Patient.null", (Object[]) null);
		}
		if (deathDate == null) {
			throw new APIException("Patient.death.date.null", (Object[]) null);
		}
		if (cause == null) {
			throw new APIException("Patient.cause.null", (Object[]) null);
		}

		if (!patient.getDead()) {
			patient.setDead(true);
			patient.setDeathDate(deathDate);
			patient.setCauseOfDeath(cause);
		}

		log.debug("Patient is dead, so let's make sure there's an Obs for it");
		// need to make sure there is an Obs that represents the patient's
		// cause of death, if applicable

		String codProp = Context.getAdministrationService().getGlobalProperty("concept.causeOfDeath");

		Concept causeOfDeath = Context.getConceptService().getConcept(codProp);

		if (causeOfDeath == null) {
			log.debug("Cause of death is null - should not have gotten here without throwing an error on the form.");
			return;
		}

		List<Obs> obssDeath = Context.getObsService().getObservationsByPersonAndConcept(patient, causeOfDeath);
		if (obssDeath == null) {
			return;
		}

		if (obssDeath.size() > 1) {
			log.error("Multiple causes of death (" + obssDeath.size() + ")?  Shouldn't be...");
			return;
		}

		Obs obsDeath = getOrCreateCauseOfDeathObs(obssDeath, patient, causeOfDeath);
		populateCauseOfDeathObs(obsDeath, patient, otherReason);
	}

	/**
	 * Processes a patient's death including setting patient attributes and creating observations.
	 */
	void processDeath(Patient patient, Date dateDied, Concept causeOfDeath, String otherReason, PatientServiceImpl service)
	        throws APIException {

		if (patient != null && dateDied != null && causeOfDeath != null) {
			// set appropriate patient characteristics
			patient.setDead(true);
			patient.setDeathDate(dateDied);
			patient.setCauseOfDeath(causeOfDeath);
			service.savePatient(patient);
			saveCauseOfDeathObs(patient, dateDied, causeOfDeath, otherReason);

			// exit from program
			// first, need to get Concept for "Patient Died"
			String strPatientDied = Context.getAdministrationService().getGlobalProperty("concept.patientDied");
			Concept conceptPatientDied = Context.getConceptService().getConcept(strPatientDied);

			if (conceptPatientDied == null) {
				log.debug("ConceptPatientDied is null");
			}
			exitFromCare(patient, dateDied, conceptPatientDied);

		} else {
			if (patient == null) {
				throw new APIException("Patient.invalid.dead", (Object[]) null);
			}
			if (dateDied == null) {
				throw new APIException("Patient.no.valid.dateDied", (Object[]) null);
			}
			if (causeOfDeath == null) {
				throw new APIException("Patient.no.valid.causeOfDeath", (Object[]) null);
			}
		}
	}

	private void checkForMissingRequiredIdentifiers(List<PatientIdentifier> patientIdentifiers) {
		final Set<PatientIdentifierType> patientIdentifierTypes = patientIdentifiers.stream()
		        .map(PatientIdentifier::getIdentifierType).collect(Collectors.toSet());

		final List<PatientIdentifierType> requiredTypes = Optional
		        .ofNullable(dao.getPatientIdentifierTypes(null, null, true, null)).orElse(Collections.emptyList());
		final Set<String> missingRequiredTypeNames = requiredTypes.stream()
		        .filter(requiredType -> !patientIdentifierTypes.contains(requiredType)).map(BaseOpenmrsMetadata::getName)
		        .collect(Collectors.toSet());

		if (!missingRequiredTypeNames.isEmpty()) {
			throw new MissingRequiredIdentifierException("Patient is missing the following required identifier(s): "
			        + String.join(", ", missingRequiredTypeNames));
		}
	}

	private void saveReasonForExitObs(Patient patient, Date exitDate, Concept cause) throws APIException {

		if (patient == null) {
			throw new APIException("Patient.null", (Object[]) null);
		}
		if (exitDate == null) {
			throw new APIException("Patient.exit.date.null", (Object[]) null);
		}
		if (cause == null) {
			throw new APIException("Patient.cause.null", (Object[]) null);
		}

		// need to make sure there is an Obs that represents the patient's
		// exit
		log.debug("Patient is exiting, so let's make sure there's an Obs for it");

		String codProp = Context.getAdministrationService().getGlobalProperty("concept.reasonExitedCare");
		Concept reasonForExit = Context.getConceptService().getConcept(codProp);

		if (reasonForExit == null) {
			log.debug("Reason for exit is null - should not have gotten here without throwing an error on the form.");
			return;
		}

		List<Obs> obssExit = Context.getObsService().getObservationsByPersonAndConcept(patient, reasonForExit);
		if (obssExit == null) {
			return;
		}

		if (obssExit.size() > 1) {
			log.error("Multiple reasons for exit (" + obssExit.size() + ")?  Shouldn't be...");
			return;
		}

		Obs obsExit = getOrCreateReasonForExitObs(obssExit, patient, reasonForExit);

		if (obsExit != null) {
			// put the right concept and (maybe) text in this obs
			obsExit.setValueCoded(cause);
			obsExit.setValueCodedName(cause.getName()); // ABKTODO: presume current locale?
			obsExit.setObsDatetime(exitDate);
			Context.getObsService().saveObs(obsExit, "updated by PatientService.saveReasonForExit");
		}
	}

	/**
	 * Returns existing reason-for-exit obs or creates a new one.
	 */
	private Obs getOrCreateReasonForExitObs(List<Obs> obssExit, Patient patient, Concept reasonForExit) {
		if (obssExit.size() == 1) {
			// already has a reason for exit - let's edit it.
			log.debug("Already has a reason for exit, so changing it");
			return obssExit.getFirst();
		}

		// no reason for exit obs yet, so let's make one
		log.debug("No reason for exit yet, let's create one.");
		Obs obsExit = new Obs();
		obsExit.setPerson(patient);
		obsExit.setConcept(reasonForExit);

		Location loc = Context.getLocationService().getDefaultLocation();
		if (loc != null) {
			obsExit.setLocation(loc);
		} else {
			log.error("Could not find a suitable location for which to create this new Obs");
		}
		return obsExit;
	}

	/**
	 * Returns existing cause-of-death obs or creates a new one.
	 */
	private Obs getOrCreateCauseOfDeathObs(List<Obs> obssDeath, Patient patient, Concept causeOfDeath) {
		if (obssDeath.size() == 1) {
			// already has a cause of death - let's edit it.
			log.debug("Already has a cause of death, so changing it");
			return obssDeath.getFirst();
		}

		// no cause of death obs yet, so let's make one
		log.debug("No cause of death yet, let's create one.");
		Obs obsDeath = new Obs();
		obsDeath.setPerson(patient);
		obsDeath.setConcept(causeOfDeath);
		Location location = Context.getLocationService().getDefaultLocation();
		if (location != null) {
			obsDeath.setLocation(location);
		} else {
			log.error("Could not find a suitable location for which to create this new Obs");
		}
		return obsDeath;
	}

	/**
	 * Populates the cause-of-death obs with the patient's current cause and optional other reason text.
	 */
	private void populateCauseOfDeathObs(Obs obsDeath, Patient patient, String otherReason) {
		// put the right concept and (maybe) text in this obs
		Concept currCause = patient.getCauseOfDeath();
		if (currCause == null) {
			// set to NONE
			log.debug("Current cause is null, attempting to set to NONE");
			String noneConcept = Context.getAdministrationService().getGlobalProperty("concept.none");
			currCause = Context.getConceptService().getConcept(noneConcept);
		}

		if (currCause == null) {
			log.debug("Current cause is still null - aborting mission");
			return;
		}

		log.debug("Current cause is not null, setting to value_coded");
		obsDeath.setValueCoded(currCause);
		obsDeath.setValueCodedName(currCause.getName()); // ABKTODO: presume current locale?

		Date dateDeath = patient.getDeathDate();
		if (dateDeath == null) {
			dateDeath = new Date();
		}
		obsDeath.setObsDatetime(dateDeath);

		// check if this is an "other" concept - if so, then
		// we need to add value_text
		String otherConcept = Context.getAdministrationService().getGlobalProperty("concept.otherNonCoded");
		Concept conceptOther = Context.getConceptService().getConcept(otherConcept);
		if (conceptOther != null && conceptOther.equals(currCause)) {
			// seems like this is an other concept -
			// let's try to get the "other" field info
			log.debug("Setting value_text as " + otherReason);
			obsDeath.setValueText(otherReason);
		} else if (conceptOther != null) {
			log.debug("New concept is NOT the OTHER concept, so setting to blank");
			obsDeath.setValueText("");
		} else {
			log.debug("Don't seem to know about an OTHER concept, so deleting value_text");
			obsDeath.setValueText("");
		}

		Context.getObsService().saveObs(obsDeath, "updated by PatientService.saveCauseOfDeathObs");
	}
}
