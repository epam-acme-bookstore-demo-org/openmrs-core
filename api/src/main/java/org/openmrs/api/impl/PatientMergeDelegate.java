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
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientProgram;
import org.openmrs.Person;
import org.openmrs.PersonAddress;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonName;
import org.openmrs.Relationship;
import org.openmrs.User;
import org.openmrs.Visit;
import org.openmrs.api.APIException;
import org.openmrs.api.EncounterService;
import org.openmrs.api.ObsService;
import org.openmrs.api.PersonService;
import org.openmrs.api.ProgramWorkflowService;
import org.openmrs.api.UserService;
import org.openmrs.api.VisitService;
import org.openmrs.api.context.Context;
import org.openmrs.parameter.EncounterSearchCriteria;
import org.openmrs.parameter.EncounterSearchCriteriaBuilder;
import org.openmrs.person.PersonMergeLog;
import org.openmrs.person.PersonMergeLogData;
import org.openmrs.serialization.SerializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Delegate that handles patient merge operations, extracted from {@link PatientServiceImpl} to
 * reduce class size and improve maintainability.
 * <p>
 * This is an internal implementation class and should not be used directly by external code.
 */
class PatientMergeDelegate {

	private static final Logger log = LoggerFactory.getLogger(PatientMergeDelegate.class);

	PatientMergeDelegate() {
	}

	/**
	 * Merges two patients by moving data from {@code notPreferred} into {@code preferred}, then voiding
	 * the non-preferred patient.
	 *
	 * @param preferred the patient to keep
	 * @param notPreferred the patient to merge and void
	 * @param service the calling service, used for direct savePatient calls
	 */
	void mergePatients(Patient preferred, Patient notPreferred, PatientServiceImpl service)
	        throws APIException, SerializationException {
		log.debug(
		    "Merging patients: (preferred)" + preferred.getPatientId() + ", (notPreferred) " + notPreferred.getPatientId());
		if (preferred.getPatientId().equals(notPreferred.getPatientId())) {
			log.debug("Merge operation cancelled: Cannot merge user" + preferred.getPatientId() + " to self");
			throw new APIException("Patient.merge.cancelled", new Object[] { preferred.getPatientId() });
		}
		requireNoActiveOrderOfSameType(preferred, notPreferred);
		var mergedData = new PersonMergeLogData();
		mergeVisits(preferred, notPreferred, mergedData);
		mergeEncounters(preferred, notPreferred, mergedData);
		mergeProgramEnrolments(preferred, notPreferred, mergedData);
		mergeRelationships(preferred, notPreferred, mergedData);
		mergeObservationsNotContainedInEncounters(preferred, notPreferred, mergedData);
		mergeIdentifiers(preferred, notPreferred, mergedData);

		mergeNames(preferred, notPreferred, mergedData);
		mergeAddresses(preferred, notPreferred, mergedData, service);
		mergePersonAttributes(preferred, notPreferred, mergedData);
		mergeGenderInformation(preferred, notPreferred, mergedData);
		mergeDateOfBirth(preferred, notPreferred, mergedData);
		mergeDateOfDeath(preferred, notPreferred, mergedData);

		// void the non preferred patient
		Context.getPatientService().voidPatient(notPreferred, "Merged with patient #" + preferred.getPatientId());

		// void the person associated with not preferred patient
		Context.getPersonService().voidPerson(notPreferred,
		    "The patient corresponding to this person has been voided and Merged with patient #" + preferred.getPatientId());

		// associate the Users associated with the not preferred person, to the preferred person.
		changeUserAssociations(preferred, notPreferred, mergedData);

		// Save the newly update preferred patient
		// This must be called _after_ voiding the nonPreferred patient so that
		//  a "Duplicate Identifier" error doesn't pop up.
		preferred = service.savePatient(preferred);

		//save the person merge log
		var personMergeLog = new PersonMergeLog();
		personMergeLog.setWinner(preferred);
		personMergeLog.setLoser(notPreferred);
		personMergeLog.setPersonMergeLogData(mergedData);
		Context.getPersonService().savePersonMergeLog(personMergeLog);
	}

	private void requireNoActiveOrderOfSameType(Patient patient1, Patient patient2) {
		String messageKey = "Patient.merge.cannotHaveSameTypeActiveOrders";
		List<Order> ordersByPatient1 = Context.getOrderService().getAllOrdersByPatient(patient1);
		List<Order> ordersByPatient2 = Context.getOrderService().getAllOrdersByPatient(patient2);
		ordersByPatient1.forEach((Order order1) -> ordersByPatient2.forEach((Order order2) -> {
			if (order1.isActive() && order2.isActive() && order1.getOrderType().equals(order2.getOrderType())) {
				Object[] parameters = { patient1.getPatientId(), patient2.getPatientId(), order1.getOrderType() };
				String message = Context.getMessageSourceService().getMessage(messageKey, parameters, Context.getLocale());
				log.debug(message);
				throw new APIException(message);
			}
		}));
	}

	private void mergeProgramEnrolments(Patient preferred, Patient notPreferred, PersonMergeLogData mergedData) {
		// copy all program enrollments
		ProgramWorkflowService programService = Context.getProgramWorkflowService();
		for (PatientProgram pp : programService.getPatientPrograms(notPreferred, null, null, null, null, null, false)) {
			if (!pp.getVoided()) {
				pp.setPatient(preferred);
				log.debug("Moving patientProgram {} to {}", pp.getPatientProgramId(), preferred.getPatientId());
				PatientProgram persisted = programService.savePatientProgram(pp);
				mergedData.addMovedProgram(persisted.getUuid());
			}
		}
	}

	private void mergeVisits(Patient preferred, Patient notPreferred, PersonMergeLogData mergedData) {
		// move all visits, including voided ones (encounters will be handled below)
		//TODO: this should be a copy, not a move

		VisitService visitService = Context.getVisitService();

		for (Visit visit : visitService.getVisitsByPatient(notPreferred, true, true)) {
			log.debug("Merging visit {} to {}", visit.getVisitId(), preferred.getPatientId());
			visit.setPatient(preferred);
			Visit persisted = visitService.saveVisit(visit);
			mergedData.addMovedVisit(persisted.getUuid());
		}
	}

	private void mergeEncounters(Patient preferred, Patient notPreferred, PersonMergeLogData mergedData) {
		// change all encounters. This will cascade to obs and orders contained in those encounters
		// TODO: this should be a copy, not a move
		EncounterService es = Context.getEncounterService();

		EncounterSearchCriteria notPreferredPatientEncounterSearchCriteria = new EncounterSearchCriteriaBuilder()
		        .setIncludeVoided(true).setPatient(notPreferred).createEncounterSearchCriteria();
		for (Encounter e : es.getEncounters(notPreferredPatientEncounterSearchCriteria)) {
			e.setPatient(preferred);
			log.debug("Merging encounter " + e.getEncounterId() + " to " + preferred.getPatientId());
			Encounter persisted = es.saveEncounter(e);
			mergedData.addMovedEncounter(persisted.getUuid());
		}
	}

	private void mergeRelationships(Patient preferred, Patient notPreferred, PersonMergeLogData mergedData) {
		// copy all relationships
		PersonService personService = Context.getPersonService();
		var existingRelationships = new HashSet<String>();
		// fill in the existing relationships with hashes
		for (Relationship rel : personService.getRelationshipsByPerson(preferred)) {
			existingRelationships.add(relationshipHash(rel, preferred));
		}
		// iterate over notPreferred's relationships and only copy them if they are needed
		for (Relationship rel : personService.getRelationshipsByPerson(notPreferred)) {
			if (!rel.getVoided()) {
				boolean personAisPreferred = rel.getPersonA().equals(preferred);
				boolean personAisNotPreferred = rel.getPersonA().equals(notPreferred);
				boolean personBisPreferred = rel.getPersonB().equals(preferred);
				boolean personBisNotPreferred = rel.getPersonB().equals(notPreferred);
				String relHash = relationshipHash(rel, notPreferred);

				if ((personAisPreferred && personBisNotPreferred) || (personBisPreferred && personAisNotPreferred)) {
					// void this relationship if it's between the preferred and notPreferred patients
					personService.voidRelationship(rel, "person " + (personAisNotPreferred ? "A" : "B")
					        + " was merged to person " + (personAisPreferred ? "A" : "B"));
				} else if (existingRelationships.contains(relHash)) {
					// void this relationship if it already exists between preferred and the other side
					personService.voidRelationship(rel,
					    "person " + (personAisNotPreferred ? "A" : "B") + " was merged and a relationship already exists");
				} else {
					// copy this relationship and replace notPreferred with preferred
					Relationship tmpRel = rel.copy();
					if (personAisNotPreferred) {
						tmpRel.setPersonA(preferred);
					}
					if (personBisNotPreferred) {
						tmpRel.setPersonB(preferred);
					}
					log.debug("Copying relationship " + rel.getRelationshipId() + " to " + preferred.getPatientId());
					Relationship persisted = personService.saveRelationship(tmpRel);
					mergedData.addCreatedRelationship(persisted.getUuid());
					// void the existing relationship to the notPreferred
					personService.voidRelationship(rel, "person " + (personAisNotPreferred ? "A" : "B")
					        + " was merged, relationship copied to #" + tmpRel.getRelationshipId());
					// add the relationship hash to existing relationships
					existingRelationships.add(relHash);
				}
				mergedData.addVoidedRelationship(rel.getUuid());
			}
		}
	}

	private void mergeObservationsNotContainedInEncounters(Patient preferred, Patient notPreferred,
	        PersonMergeLogData mergedData) {
		// move all obs that weren't contained in encounters
		// TODO: this should be a copy, not a move
		ObsService obsService = Context.getObsService();
		for (Obs obs : obsService.getObservationsByPerson(notPreferred)) {
			if (obs.getEncounter() == null && !obs.getVoided()) {
				obs.setPerson(preferred);
				Obs persisted = obsService.saveObs(obs, "Merged from patient #" + notPreferred.getPatientId());
				mergedData.addMovedIndependentObservation(persisted.getUuid());
			}
		}
	}

	private void mergeIdentifiers(Patient preferred, Patient notPreferred, PersonMergeLogData mergedData) {
		// move all identifiers
		// (must be done after all calls to services above so hbm doesn't try to save things prematurely (hacky)
		for (PatientIdentifier pi : notPreferred.getActiveIdentifiers()) {
			var tmpIdentifier = new PatientIdentifier();
			tmpIdentifier.setIdentifier(pi.getIdentifier());
			tmpIdentifier.setIdentifierType(pi.getIdentifierType());
			tmpIdentifier.setLocation(pi.getLocation());
			tmpIdentifier.setPatient(preferred);
			boolean found = false;
			for (PatientIdentifier preferredIdentifier : preferred.getIdentifiers()) {
				if (preferredIdentifier.getIdentifier() != null
				        && preferredIdentifier.getIdentifier().equals(tmpIdentifier.getIdentifier())
				        && preferredIdentifier.getIdentifierType() != null
				        && preferredIdentifier.getIdentifierType().equals(tmpIdentifier.getIdentifierType())) {
					found = true;
				}
			}
			if (!found) {
				tmpIdentifier.setIdentifierType(pi.getIdentifierType());
				tmpIdentifier.setCreator(Context.getAuthenticatedUser());
				tmpIdentifier.setDateCreated(new Date());
				tmpIdentifier.setVoided(false);
				tmpIdentifier.setVoidedBy(null);
				tmpIdentifier.setVoidReason(null);
				tmpIdentifier.setUuid(UUID.randomUUID().toString());
				// we don't want to change the preferred identifier of the preferred patient
				tmpIdentifier.setPreferred(false);
				preferred.addIdentifier(tmpIdentifier);
				mergedData.addCreatedIdentifier(tmpIdentifier.getUuid());
				log.debug("Merging identifier " + tmpIdentifier.getIdentifier() + " to " + preferred.getPatientId());
			}
		}
	}

	private void mergeDateOfDeath(Patient preferred, Patient notPreferred, PersonMergeLogData mergedData) {
		mergedData.setPriorDateOfDeath(preferred.getDeathDate());
		if (preferred.getDeathDate() == null) {
			preferred.setDeathDate(notPreferred.getDeathDate());
		}

		if (preferred.getCauseOfDeath() != null) {
			mergedData.setPriorCauseOfDeath(preferred.getCauseOfDeath().getUuid());
		}
		if (preferred.getCauseOfDeath() == null) {
			preferred.setCauseOfDeath(notPreferred.getCauseOfDeath());
		}
	}

	private void mergeDateOfBirth(Patient preferred, Patient notPreferred, PersonMergeLogData mergedData) {
		mergedData.setPriorDateOfBirth(preferred.getBirthdate());
		mergedData.setPriorDateOfBirthEstimated(preferred.getBirthdateEstimated());
		if (preferred.getBirthdate() == null
		        || (preferred.getBirthdateEstimated() && !notPreferred.getBirthdateEstimated())) {
			preferred.setBirthdate(notPreferred.getBirthdate());
			preferred.setBirthdateEstimated(notPreferred.getBirthdateEstimated());
		}
	}

	private void mergePersonAttributes(Patient preferred, Patient notPreferred, PersonMergeLogData mergedData) {
		// copy person attributes
		for (PersonAttribute attr : notPreferred.getAttributes()) {
			if (!attr.getVoided()) {
				PersonAttribute tmpAttr = attr.copy();
				tmpAttr.setPerson(null);
				tmpAttr.setUuid(UUID.randomUUID().toString());
				preferred.addAttribute(tmpAttr);
				mergedData.addCreatedAttribute(tmpAttr.getUuid());
			}
		}
	}

	private void mergeGenderInformation(Patient preferred, Patient notPreferred, PersonMergeLogData mergedData) {
		// move all other patient info
		mergedData.setPriorGender(preferred.getGender());
		if (!"M".equals(preferred.getGender()) && !"F".equals(preferred.getGender())) {
			preferred.setGender(notPreferred.getGender());
		}
	}

	private void mergeNames(Patient preferred, Patient notPreferred, PersonMergeLogData mergedData) {
		// move all names
		// (must be done after all calls to services above so hbm doesn't try to save things prematurely (hacky)
		for (PersonName newName : notPreferred.getNames()) {
			boolean containsName = false;
			for (PersonName currentName : preferred.getNames()) {
				containsName = currentName.equalsContent(newName);
				if (containsName) {
					break;
				}
			}
			if (!containsName) {
				PersonName tmpName = constructTemporaryName(newName);
				preferred.addName(tmpName);
				mergedData.addCreatedName(tmpName.getUuid());
				log.debug("Merging name " + newName.getGivenName() + " to " + preferred.getPatientId());
			}
		}
	}

	private PersonName constructTemporaryName(PersonName newName) {
		PersonName tmpName = PersonName.newInstance(newName);
		tmpName.setPersonNameId(null);
		tmpName.setVoided(false);
		tmpName.setVoidedBy(null);
		tmpName.setVoidReason(null);
		// we don't want to change the preferred name of the preferred patient
		tmpName.setPreferred(false);
		tmpName.setUuid(UUID.randomUUID().toString());
		return tmpName;
	}

	private void mergeAddresses(Patient preferred, Patient notPreferred, PersonMergeLogData mergedData,
	        PatientServiceImpl service) throws SerializationException {
		// move all addresses
		// (must be done after all calls to services above so hbm doesn't try to save things prematurely (hacky)
		for (PersonAddress newAddress : notPreferred.getAddresses()) {
			boolean containsAddress = false;
			for (PersonAddress currentAddress : preferred.getAddresses()) {
				containsAddress = currentAddress.equalsContent(newAddress);
				if (containsAddress) {
					break;
				}
			}
			if (!containsAddress) {
				PersonAddress tmpAddress = (PersonAddress) newAddress.clone();
				tmpAddress.setPersonAddressId(null);
				tmpAddress.setVoided(false);
				tmpAddress.setVoidedBy(null);
				tmpAddress.setVoidReason(null);
				tmpAddress.setPreferred(false); // addresses from non-preferred patient shouldn't be marked as preferred
				tmpAddress.setUuid(UUID.randomUUID().toString());
				preferred.addAddress(tmpAddress);
				mergedData.addCreatedAddress(tmpAddress.getUuid());
				log.debug("Merging address " + newAddress.getPersonAddressId() + " to " + preferred.getPatientId());
			}
		}

		// copy person attributes
		for (PersonAttribute attr : notPreferred.getAttributes()) {
			if (!attr.getVoided()) {
				PersonAttribute tmpAttr = attr.copy();
				tmpAttr.setPerson(null);
				tmpAttr.setUuid(UUID.randomUUID().toString());
				preferred.addAttribute(tmpAttr);
				mergedData.addCreatedAttribute(tmpAttr.getUuid());
			}
		}

		// move all other patient info
		mergedData.setPriorGender(preferred.getGender());
		if (!"M".equals(preferred.getGender()) && !"F".equals(preferred.getGender())) {
			preferred.setGender(notPreferred.getGender());
		}

		mergedData.setPriorDateOfBirth(preferred.getBirthdate());
		mergedData.setPriorDateOfBirthEstimated(preferred.getBirthdateEstimated());
		if (preferred.getBirthdate() == null
		        || (preferred.getBirthdateEstimated() && !notPreferred.getBirthdateEstimated())) {
			preferred.setBirthdate(notPreferred.getBirthdate());
			preferred.setBirthdateEstimated(notPreferred.getBirthdateEstimated());
		}
		mergedData.setPriorDateOfDeathEstimated(preferred.getDeathdateEstimated());
		if (preferred.getDeathdateEstimated() == null) {
			preferred.setDeathdateEstimated(notPreferred.getDeathdateEstimated());
		}

		mergedData.setPriorDateOfDeath(preferred.getDeathDate());
		if (preferred.getDeathDate() == null) {
			preferred.setDeathDate(notPreferred.getDeathDate());
		}

		if (preferred.getCauseOfDeath() != null) {
			mergedData.setPriorCauseOfDeath(preferred.getCauseOfDeath().getUuid());
		}
		if (preferred.getCauseOfDeath() == null) {
			preferred.setCauseOfDeath(notPreferred.getCauseOfDeath());
		}

		// void the non preferred patient
		Context.getPatientService().voidPatient(notPreferred, "Merged with patient #" + preferred.getPatientId());

		// void the person associated with not preferred patient
		Context.getPersonService().voidPerson(notPreferred,
		    "The patient corresponding to this person has been voided and Merged with patient #" + preferred.getPatientId());

		// associate the Users associated with the not preferred person, to the preferred person.
		changeUserAssociations(preferred, notPreferred, mergedData);

		// Save the newly update preferred patient
		// This must be called _after_ voiding the nonPreferred patient so that
		//  a "Duplicate Identifier" error doesn't pop up.
		preferred = service.savePatient(preferred);

		//save the person merge log
		var personMergeLog = new PersonMergeLog();
		personMergeLog.setWinner(preferred);
		personMergeLog.setLoser(notPreferred);
		personMergeLog.setPersonMergeLogData(mergedData);
		Context.getPersonService().savePersonMergeLog(personMergeLog);
	}

	/**
	 * Change user associations for notPreferred to preferred person.
	 *
	 * @param preferred the preferred patient
	 * @param notPreferred the not preferred person
	 * @param mergedData a patient merge audit data object to update
	 */
	void changeUserAssociations(Patient preferred, Person notPreferred, PersonMergeLogData mergedData) {
		UserService userService = Context.getUserService();
		List<User> users = userService.getUsersByPerson(notPreferred, true);
		for (User user : users) {
			user.setPerson(preferred);
			User persisted = userService.saveUser(user);
			if (mergedData != null) {
				mergedData.addMovedUser(persisted.getUuid());
			}
		}
	}

	/**
	 * Generate a relationship hash for use in mergePatients; follows the convention:
	 * [relationshipType][A|B][relativeId]
	 *
	 * @param rel relationship under consideration
	 * @param primary the focus of the hash
	 * @return hash depicting relevant information to avoid duplicates
	 */
	private String relationshipHash(Relationship rel, Person primary) {
		boolean isA = rel.getPersonA().equals(primary);
		return rel.getRelationshipType().getRelationshipTypeId().toString() + (isA ? "A" : "B")
		        + (isA ? rel.getPersonB().getPersonId().toString() : rel.getPersonA().getPersonId().toString());
	}
}
