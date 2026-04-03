/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.hl7.handler;

import java.util.HashSet;
import java.util.regex.Pattern;

import org.openmrs.Patient;
import org.openmrs.Person;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonAttributeType;
import org.openmrs.Relationship;
import org.openmrs.RelationshipType;
import org.openmrs.api.context.Context;
import org.openmrs.hl7.HL7Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.v25.datatype.DLD;
import ca.uhn.hl7v2.model.v25.datatype.IS;
import ca.uhn.hl7v2.model.v25.segment.NK1;
import ca.uhn.hl7v2.model.v25.segment.PID;
import ca.uhn.hl7v2.model.v25.segment.PV1;

/**
 * Static utility methods for HL7 patient (PID) segment parsing and relationship (NK1) processing,
 * extracted from {@link ORUR01Handler}.
 *
 * @since 2.8.0
 */
final class HL7PatientHandler {

	private static final Logger log = LoggerFactory.getLogger(HL7PatientHandler.class);

	private HL7PatientHandler() {
	}

	/**
	 * Resolves a {@link Patient} from a PID segment.
	 *
	 * @param pid the PID segment
	 * @return the resolved Patient
	 * @throws HL7Exception if the patient cannot be resolved
	 */
	static Patient getPatient(PID pid) throws HL7Exception {
		Integer patientId = Context.getHL7Service().resolvePatientId(pid);
		if (patientId == null) {
			throw new HL7Exception(Context.getMessageSourceService().getMessage("ORUR01.error.UnresolvedPatient"));
		}

		return Context.getPatientService().getPatient(patientId);
	}

	/**
	 * Gets a relative based on an NK1 segment.
	 *
	 * @param nk1 an NK1 segment from the HL7 request
	 * @return a matching Person or null if not found
	 * @throws HL7Exception if resolution fails
	 */
	static Person getRelative(NK1 nk1) throws HL7Exception {
		// if there are no associated party identifiers, the person will not exist
		if (nk1.getNextOfKinAssociatedPartySIdentifiers().length < 1) {
			return null;
		}
		// find the related person via given IDs
		return Context.getHL7Service().resolvePersonFromIdentifiers(nk1.getNextOfKinAssociatedPartySIdentifiers());
	}

	/**
	 * Process an NK1 segment and add relationships if needed.
	 *
	 * @param patient the patient
	 * @param nk1 the NK1 segment
	 * @throws HL7Exception if processing fails
	 */
	static void processNK1(Patient patient, NK1 nk1) throws HL7Exception {
		// guarantee we are working with our custom coding system
		String relCodingSystem = nk1.getRelationship().getNameOfCodingSystem().getValue();
		if (!relCodingSystem.equals(HL7Constants.HL7_LOCAL_RELATIONSHIP)) {
			throw new HL7Exception(Context.getMessageSourceService().getMessage("ORUR01.error.relationshipCoding",
			    new Object[] { relCodingSystem }, null));
		}

		// get the relationship type identifier
		String relIdentifier = nk1.getRelationship().getIdentifier().getValue();

		// validate the format of the relationship identifier
		if (!Pattern.matches("[0-9]+[AB]", relIdentifier)) {
			throw new HL7Exception(Context.getMessageSourceService().getMessage("ORUR01.error.relationshipType",
			    new Object[] { relIdentifier }, null));
		}

		// get the type ID
		Integer relTypeId;
		try {
			relTypeId = Integer.parseInt(relIdentifier.substring(0, relIdentifier.length() - 1));
		} catch (NumberFormatException e) {
			throw new HL7Exception(Context.getMessageSourceService().getMessage("ORUR01.error.relationshipType",
			    new Object[] { relIdentifier }, null));
		}

		// find the relationship type
		RelationshipType relType = Context.getPersonService().getRelationshipType(relTypeId);
		if (relType == null) {
			throw new HL7Exception(Context.getMessageSourceService().getMessage("ORUR01.error.relationshipTypeNotFound",
			    new Object[] { relTypeId }, null));
		}

		// find the relative
		Person relative = getRelative(nk1);

		// determine if the patient is person A or B; the relIdentifier indicates
		// the relative's side of the relationship, so the patient is the inverse
		boolean patientIsPersonA = relIdentifier.endsWith("B");
		boolean patientCanBeEitherPerson = relType.getbIsToA().equals(relType.getaIsToB());

		// look at existing relationships to determine if a new one is needed
		var rels = new HashSet<Relationship>();
		if (relative != null) {
			if (patientCanBeEitherPerson || patientIsPersonA) {
				rels.addAll(Context.getPersonService().getRelationships(patient, relative, relType));
			}
			if (patientCanBeEitherPerson || !patientIsPersonA) {
				rels.addAll(Context.getPersonService().getRelationships(relative, patient, relType));
			}
		}

		// create a relationship if none is found
		if (rels.isEmpty()) {

			// check the relative's existence
			if (relative == null) {
				// create one based on NK1 information
				relative = Context.getHL7Service().createPersonFromNK1(nk1);
				if (relative == null) {
					throw new HL7Exception(Context.getMessageSourceService().getMessage("ORUR01.error.relativeNotCreated"));
				}
			}

			// create the relationship
			var relation = new Relationship();
			if (patientCanBeEitherPerson || patientIsPersonA) {
				relation.setPersonA(patient);
				relation.setPersonB(relative);
			} else {
				relation.setPersonA(relative);
				relation.setPersonB(patient);
			}
			relation.setRelationshipType(relType);
			Context.getPersonService().saveRelationship(relation);
		}
	}

	/**
	 * Updates a patient's health center based on the discharge-to-location in the PV1 segment.
	 *
	 * @param patient the patient
	 * @param pv1 the PV1 segment
	 */
	static void updateHealthCenter(Patient patient, PV1 pv1) {
		// Update patient's location if it has changed
		if (log.isDebugEnabled()) {
			log.debug("Checking for discharge to location");
		}
		DLD dld = pv1.getDischargedToLocation();
		log.debug("DLD = " + dld);
		if (dld == null) {
			return;
		}
		IS hl7DischargeToLocation = dld.getDischargeLocation();
		log.debug("is = " + hl7DischargeToLocation);
		if (hl7DischargeToLocation == null) {
			return;
		}
		String dischargeToLocation = hl7DischargeToLocation.getValue();
		log.debug("dischargeToLocation = " + dischargeToLocation);
		if (dischargeToLocation != null && dischargeToLocation.length() > 0) {
			if (log.isDebugEnabled()) {
				log.debug("Patient discharged to " + dischargeToLocation);
			}
			// Ignore anything past the first subcomponent (or component)
			// delimiter
			for (int i = 0; i < dischargeToLocation.length(); i++) {
				char ch = dischargeToLocation.charAt(i);
				if (ch == '&' || ch == '^') {
					dischargeToLocation = dischargeToLocation.substring(0, i);
					break;
				}
			}
			Integer newLocationId = Integer.parseInt(dischargeToLocation);
			// Hydrate a full patient object from patient object containing only
			// identifier
			patient = Context.getPatientService().getPatient(patient.getPatientId());

			PersonAttributeType healthCenterAttrType = Context.getPersonService()
			        .getPersonAttributeTypeByName("Health Center");

			if (healthCenterAttrType == null) {
				log.error("A person attribute type with name 'Health Center' is not defined but patient "
				        + patient.getPatientId() + " is trying to change their health center to " + newLocationId);
				return;
			}

			PersonAttribute currentHealthCenter = patient.getAttribute("Health Center");

			if (currentHealthCenter == null || !newLocationId.toString().equals(currentHealthCenter.getValue())) {
				PersonAttribute newHealthCenter = new PersonAttribute(healthCenterAttrType, newLocationId.toString());

				log.debug("Updating patient's location from " + currentHealthCenter + " to " + newLocationId);

				// add attribute (and void old if there is one)
				patient.addAttribute(newHealthCenter);

				// save the patient and their new attribute
				Context.getPatientService().savePatient(patient);
			}

		}
		log.debug("finished discharge to location method");
	}
}
