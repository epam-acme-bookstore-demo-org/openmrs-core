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

import java.util.Calendar;
import java.util.Collection;
import java.util.Date;

import org.openmrs.Concept;
import org.openmrs.ConceptAnswer;
import org.openmrs.ConceptName;
import org.openmrs.ConceptProposal;
import org.openmrs.Drug;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.api.context.Context;
import org.openmrs.hl7.HL7Constants;
import org.openmrs.obs.ComplexData;
import org.openmrs.util.OpenmrsConstants;
import org.openmrs.util.OpenmrsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.DataTypeException;
import ca.uhn.hl7v2.model.Type;
import ca.uhn.hl7v2.model.Varies;
import ca.uhn.hl7v2.model.v25.datatype.CE;
import ca.uhn.hl7v2.model.v25.datatype.CWE;
import ca.uhn.hl7v2.model.v25.datatype.DT;
import ca.uhn.hl7v2.model.v25.datatype.DTM;
import ca.uhn.hl7v2.model.v25.datatype.ED;
import ca.uhn.hl7v2.model.v25.datatype.FT;
import ca.uhn.hl7v2.model.v25.datatype.ID;
import ca.uhn.hl7v2.model.v25.datatype.NM;
import ca.uhn.hl7v2.model.v25.datatype.ST;
import ca.uhn.hl7v2.model.v25.datatype.TM;
import ca.uhn.hl7v2.model.v25.datatype.TS;
import ca.uhn.hl7v2.model.v25.group.ORU_R01_OBSERVATION;
import ca.uhn.hl7v2.model.v25.segment.OBR;
import ca.uhn.hl7v2.model.v25.segment.OBX;

/**
 * Static utility methods for HL7 observation (OBX) segment parsing and concept resolution,
 * extracted from {@link ORUR01Handler}.
 *
 * @since 2.8.0
 */
final class HL7ObservationHandler {

	private static final Logger log = LoggerFactory.getLogger(HL7ObservationHandler.class);

	private HL7ObservationHandler() {
	}

	/**
	 * Creates the Obs pojo from the OBX message.
	 */
	static Obs parseObs(Encounter encounter, OBX obx, OBR obr, String uid) throws HL7Exception, ProposingConceptException {
		if (log.isDebugEnabled()) {
			log.debug("parsing observation: " + obx);
		}
		Varies[] values = obx.getObservationValue();

		// bail out if no values were found
		if (values == null || values.length < 1) {
			return null;
		}

		String hl7Datatype = values[0].getName();
		if (log.isDebugEnabled()) {
			log.debug("  datatype = " + hl7Datatype);
		}
		Concept concept = getConcept(obx.getObservationIdentifier(), uid);
		if (log.isDebugEnabled()) {
			log.debug("  concept = " + concept.getConceptId());
		}
		ConceptName conceptName = getConceptName(obx.getObservationIdentifier());
		if (log.isDebugEnabled()) {
			log.debug("  concept-name = " + conceptName);
		}

		Date datetime = getDatetime(obx);
		if (log.isDebugEnabled()) {
			log.debug("  timestamp = " + datetime);
		}
		if (datetime == null) {
			datetime = encounter.getEncounterDatetime();
		}

		var obs = new Obs();
		obs.setPerson(encounter.getPatient());
		obs.setConcept(concept);
		obs.setEncounter(encounter);
		obs.setObsDatetime(datetime);
		obs.setLocation(encounter.getLocation());
		obs.setCreator(encounter.getCreator());
		obs.setDateCreated(encounter.getDateCreated());

		// set comments if there are any
		var comments = new StringBuilder();
		ORU_R01_OBSERVATION parent = (ORU_R01_OBSERVATION) obx.getParent();
		// iterate over all OBX NTEs
		for (int i = 0; i < parent.getNTEReps(); i++) {
			for (FT obxComment : parent.getNTE(i).getComment()) {
				if (comments.length() > 0) {
					comments.append(" ");
				}
				comments = comments.append(obxComment.getValue());
			}
		}
		// only set comments if there are any
		if (StringUtils.hasText(comments.toString())) {
			obs.setComment(comments.toString());
		}

		Type obx5 = values[0].getData();
		if ("NM".equals(hl7Datatype)) {
			String value = ((NM) obx5).getValue();
			if (value == null || value.length() == 0) {
				log.warn("Not creating null valued obs for concept " + concept);
				return null;
			} else if ("0".equals(value) || "1".equals(value)) {
				concept = concept.hydrate(concept.getConceptId().toString());
				obs.setConcept(concept);
				parseNumericBooleanOrCodedValue(obs, concept, conceptName, value, uid);
			} else {
				try {
					obs.setValueNumeric(Double.valueOf(value));
				} catch (NumberFormatException e) {
					throw new HL7Exception(Context.getMessageSourceService().getMessage("ORUR01.error.notnumericConcept",
					    new Object[] { value, concept.getConceptId(), conceptName.getName(), uid }, null), e);
				}
			}
		} else if ("CWE".equals(hl7Datatype)) {
			log.debug("  CWE observation");
			parseCweObsValue(obs, (CWE) obx5, concept, uid);
			if (log.isDebugEnabled()) {
				log.debug("  Done with CWE");
			}
		} else if ("CE".equals(hl7Datatype)) {
			CE value = (CE) obx5;
			String valueIdentifier = value.getIdentifier().getValue();
			String valueName = value.getText().getValue();
			if (isConceptProposal(valueIdentifier)) {
				throw new ProposingConceptException(concept, valueName);
			}
			try {
				obs.setValueCoded(getConcept(value, uid));
				obs.setValueCodedName(getConceptName(value));
			} catch (NumberFormatException e) {
				throw new HL7Exception(Context.getMessageSourceService().getMessage("ORUR01.error.InvalidConceptId",
				    new Object[] { valueIdentifier, valueName }, null));
			}
		} else if ("DT".equals(hl7Datatype)) {
			DT value = (DT) obx5;
			if (value != null) {
				Date valueDate = getDate(value.getYear(), value.getMonth(), value.getDay(), 0, 0, 0);
				obs.setValueDatetime(valueDate);
			} else {
				log.warn("Not creating null valued obs for concept " + concept);
				return null;
			}
		} else if ("TS".equals(hl7Datatype)) {
			DTM value = ((TS) obx5).getTime();
			if (value != null) {
				Date valueDate = getDate(value.getYear(), value.getMonth(), value.getDay(), value.getHour(),
				    value.getMinute(), value.getSecond());

				obs.setValueDatetime(valueDate);
			} else {
				log.warn("Not creating null valued obs for concept " + concept);
				return null;
			}
		} else if ("TM".equals(hl7Datatype)) {
			TM value = (TM) obx5;
			if (value != null) {
				Date valueTime = getDate(0, 0, 0, value.getHour(), value.getMinute(), value.getSecond());
				obs.setValueDatetime(valueTime);
			} else {
				log.warn("Not creating null valued obs for concept " + concept);
				return null;
			}
		} else if ("ST".equals(hl7Datatype)) {
			ST value = (ST) obx5;
			if (value == null || value.getValue() == null || value.getValue().isBlank()) {
				log.warn("Not creating null valued obs for concept " + concept);
				return null;
			}
			obs.setValueText(value.getValue());
		} else if ("ED".equals(hl7Datatype)) {
			ED value = (ED) obx5;
			if (value == null || value.getData() == null || !StringUtils.hasText(value.getData().getValue())) {
				log.warn("Not creating null valued obs for concept " + concept);
				return null;
			}
			//we need to hydrate the concept so that the EncounterSaveHandler
			//doesn't fail since it needs to check if it is a concept numeric
			Concept c = Context.getConceptService().getConcept(obs.getConcept().getConceptId());
			obs.setConcept(c);
			String title = null;
			if (obs.getValueCodedName() != null) {
				title = obs.getValueCodedName().getName();
			}
			if (!StringUtils.hasText(title)) {
				title = c.getName().getName();
			}
			obs.setComplexData(new ComplexData(title, value.getData().getValue()));
		} else {
			// unsupported data type
			// TODO: support RP (report), SN (structured numeric)
			// do we need to support BIT just in case it slips thru?
			throw new HL7Exception(Context.getMessageSourceService().getMessage("ORUR01.error.UpsupportedObsType",
			    new Object[] { hl7Datatype }, null));
		}

		return obs;
	}

	private static void parseNumericBooleanOrCodedValue(Obs obs, Concept concept, ConceptName conceptName, String value,
	        String uid) throws HL7Exception {
		if (concept.getDatatype().isBoolean()) {
			obs.setValueBoolean("1".equals(value));
			return;
		}
		if (concept.getDatatype().isNumeric()) {
			try {
				obs.setValueNumeric(Double.valueOf(value));
			} catch (NumberFormatException e) {
				throw new HL7Exception(Context.getMessageSourceService().getMessage("ORUR01.error.notnumericConcept",
				    new Object[] { value, concept.getConceptId(), conceptName.getName(), uid }, null), e);
			}
			return;
		}
		if (concept.getDatatype().isCoded()) {
			parseCodedBooleanAnswer(obs, concept, value, uid);
			return;
		}
		throw new HL7Exception(Context.getMessageSourceService().getMessage("ORUR01.error.CannotSetBoolean",
		    new Object[] { obs.getConcept().getConceptId() }, null));
	}

	private static void parseCodedBooleanAnswer(Obs obs, Concept concept, String value, String uid) throws HL7Exception {
		Concept answer = "1".equals(value) ? Context.getConceptService().getTrueConcept()
		        : Context.getConceptService().getFalseConcept();
		boolean isValidAnswer = false;
		Collection<ConceptAnswer> conceptAnswers = concept.getAnswers();
		if (conceptAnswers != null && !conceptAnswers.isEmpty()) {
			for (ConceptAnswer conceptAnswer : conceptAnswers) {
				if (conceptAnswer.getAnswerConcept().getId().equals(answer.getId())) {
					obs.setValueCoded(answer);
					isValidAnswer = true;
					break;
				}
			}
		}
		if (!isValidAnswer) {
			throw new HL7Exception(Context.getMessageSourceService().getMessage("ORUR01.error.invalidAnswer",
			    new Object[] { answer.toString(), uid }, null));
		}
	}

	private static void parseCweObsValue(Obs obs, CWE value, Concept concept, String uid)
	        throws HL7Exception, ProposingConceptException {
		String valueIdentifier = value.getIdentifier().getValue();
		log.debug("    value id = " + valueIdentifier);
		String valueName = value.getText().getValue();
		log.debug("    value name = " + valueName);
		if (isConceptProposal(valueIdentifier)) {
			if (log.isDebugEnabled()) {
				log.debug("Proposing concept");
			}
			throw new ProposingConceptException(concept, valueName);
		}
		log.debug("    not proposal");
		try {
			Concept valueConcept = getConcept(value, uid);
			obs.setValueCoded(valueConcept);
			if (HL7Constants.HL7_LOCAL_DRUG.equals(value.getNameOfAlternateCodingSystem().getValue())) {
				var valueDrug = new Drug();
				valueDrug.setDrugId(Integer.valueOf(value.getAlternateIdentifier().getValue()));
				obs.setValueDrug(valueDrug);
			} else {
				ConceptName valueConceptName = getConceptName(value);
				if (valueConceptName != null) {
					if (log.isDebugEnabled()) {
						log.debug("    value concept-name-id = " + valueConceptName.getConceptNameId());
						log.debug("    value concept-name = " + valueConceptName.getName());
					}
					obs.setValueCodedName(valueConceptName);
				}
			}
		} catch (NumberFormatException e) {
			throw new HL7Exception(Context.getMessageSourceService().getMessage("ORUR01.error.InvalidConceptId",
			    new Object[] { valueIdentifier, valueName }, null));
		}
	}

	/**
	 * Derive a concept name from the CWE component of an hl7 message.
	 */
	private static ConceptName getConceptName(CWE cwe) throws HL7Exception {
		ST altIdentifier = cwe.getAlternateIdentifier();
		ID altCodingSystem = cwe.getNameOfAlternateCodingSystem();
		return getConceptName(altIdentifier, altCodingSystem);
	}

	/**
	 * Derive a concept name from the CE component of an hl7 message.
	 */
	private static ConceptName getConceptName(CE ce) throws HL7Exception {
		ST altIdentifier = ce.getAlternateIdentifier();
		ID altCodingSystem = ce.getNameOfAlternateCodingSystem();
		return getConceptName(altIdentifier, altCodingSystem);
	}

	/**
	 * Derive a concept name from identifier and coding system components.
	 */
	private static ConceptName getConceptName(ST altIdentifier, ID altCodingSystem) throws HL7Exception {
		if (altIdentifier != null && HL7Constants.HL7_LOCAL_CONCEPT_NAME.equals(altCodingSystem.getValue())) {
			String hl7ConceptNameId = altIdentifier.getValue();
			return getConceptName(hl7ConceptNameId);
		}

		return null;
	}

	/**
	 * Utility method to retrieve the openmrs ConceptName specified in an hl7 message observation
	 * segment. This method assumes that the check for 99NAM has been done already and is being given an
	 * openmrs conceptNameId.
	 *
	 * @param hl7ConceptNameId internal ConceptNameId to look up
	 * @return ConceptName from the database
	 * @throws HL7Exception if the name cannot be resolved
	 */
	private static ConceptName getConceptName(String hl7ConceptNameId) throws HL7Exception {
		ConceptName specifiedConceptName = null;
		if (hl7ConceptNameId != null) {
			// get the exact concept name specified by the id
			try {
				Integer conceptNameId = Integer.valueOf(hl7ConceptNameId);
				specifiedConceptName = new ConceptName();
				specifiedConceptName.setConceptNameId(conceptNameId);
			} catch (NumberFormatException e) {
				// if it is not a valid number, more than likely it is a bad hl7 message
				log.debug("Invalid concept name ID '" + hl7ConceptNameId + "'", e);
			}
		}
		return specifiedConceptName;
	}

	private static boolean isConceptProposal(String identifier) {
		return OpenmrsUtil.nullSafeEquals(identifier, OpenmrsConstants.PROPOSED_CONCEPT_IDENTIFIER);
	}

	static Date getDate(int year, int month, int day, int hour, int minute, int second) {
		Calendar cal = Calendar.getInstance();
		// Calendar.set(MONTH, int) is zero-based, Hl7 is not
		cal.set(year, month - 1, day, hour, minute, second);
		return cal.getTime();
	}

	/**
	 * Get an openmrs Concept object out of the given hl7 coded element.
	 *
	 * @param codedElement ce to pull from
	 * @param uid unique string for this message for any error reporting purposes
	 * @return new Concept object
	 * @throws HL7Exception if parsing errors occur
	 */
	static Concept getConcept(CE codedElement, String uid) throws HL7Exception {
		String hl7ConceptId = codedElement.getIdentifier().getValue();

		String codingSystem = codedElement.getNameOfCodingSystem().getValue();
		return getConcept(hl7ConceptId, codingSystem, uid);
	}

	/**
	 * Get an openmrs Concept object out of the given hl7 coded with exceptions element.
	 *
	 * @param codedElement cwe to pull from
	 * @param uid unique string for this message for any error reporting purposes
	 * @return new Concept object
	 * @throws HL7Exception if parsing errors occur
	 */
	static Concept getConcept(CWE codedElement, String uid) throws HL7Exception {
		String hl7ConceptId = codedElement.getIdentifier().getValue();

		String codingSystem = codedElement.getNameOfCodingSystem().getValue();
		return getConcept(hl7ConceptId, codingSystem, uid);
	}

	/**
	 * Get a concept object representing this conceptId and coding system.
	 * <p>
	 * If codingSystem is 99DCT, then a new Concept with the given conceptId is returned. Otherwise, the
	 * coding system is looked up in the ConceptMap for an openmrs concept mapped to that code.
	 *
	 * @param hl7ConceptId the given hl7 conceptId
	 * @param codingSystem the coding system for this conceptid (e.g. 99DCT)
	 * @param uid unique string for this message for any error reporting purposes
	 * @return a Concept object or null if no conceptId with given coding system found
	 * @throws HL7Exception if the concept cannot be resolved
	 */
	static Concept getConcept(String hl7ConceptId, String codingSystem, String uid) throws HL7Exception {
		if (codingSystem == null || HL7Constants.HL7_LOCAL_CONCEPT.equals(codingSystem)) {
			// the concept is local
			try {
				Integer conceptId = Integer.valueOf(hl7ConceptId);
				return Context.getConceptService().getConcept(conceptId);
			} catch (NumberFormatException e) {
				throw new HL7Exception(Context.getMessageSourceService().getMessage("ORUR01.error.hl7ConceptId",
				    new Object[] { hl7ConceptId, uid }, null));
			}
		} else {
			// the concept is not local, look it up in our mapping
			return Context.getConceptService().getConceptByMapping(hl7ConceptId, codingSystem);
		}
	}

	/**
	 * Pull the timestamp for this obx out. If an invalid date is found, null is returned.
	 *
	 * @param obx the obs to parse and get the timestamp from
	 * @return an obx timestamp or null
	 * @throws HL7Exception if parsing fails
	 */
	static Date getDatetime(OBX obx) throws HL7Exception {
		TS ts = obx.getDateTimeOfTheObservation();
		return getDatetime(ts);
	}

	/**
	 * Pull the timestamp for this obr out. If an invalid date is found, null is returned.
	 *
	 * @param obr the OBR segment
	 * @return an obr timestamp or null
	 * @throws HL7Exception if parsing fails
	 */
	static Date getDatetime(OBR obr) throws HL7Exception {
		TS ts = obr.getObservationDateTime();
		return getDatetime(ts);
	}

	/**
	 * Return a java date object for the given TS.
	 *
	 * @param ts TS to parse
	 * @return date object or null
	 * @throws HL7Exception if parsing fails
	 */
	static Date getDatetime(TS ts) throws HL7Exception {
		Date datetime = null;
		DTM value = ts.getTime();

		if (value.getYear() == 0 || value.getValue() == null) {
			return null;
		}

		try {
			datetime = getDate(value.getYear(), value.getMonth(), value.getDay(), value.getHour(), value.getMinute(),
			    value.getSecond());
		} catch (DataTypeException e) {

		}
		return datetime;
	}

	/**
	 * Creates a ConceptProposal object that will need to be saved to the database at a later point.
	 *
	 * @param encounter the encounter
	 * @param concept the concept
	 * @param originalText the proposed text
	 * @return a new ConceptProposal
	 */
	static ConceptProposal createConceptProposal(Encounter encounter, Concept concept, String originalText) {
		// value is a proposed concept, create a ConceptProposal
		// instead of an Obs for this observation
		// TODO: at this point if componentSeparator (^) is in text,
		// we'll only use the text before that delimiter!
		var conceptProposal = new ConceptProposal();
		conceptProposal.setOriginalText(originalText);
		conceptProposal.setState(OpenmrsConstants.CONCEPT_PROPOSAL_UNMAPPED);
		conceptProposal.setEncounter(encounter);
		conceptProposal.setObsConcept(concept);
		return conceptProposal;
	}
}
