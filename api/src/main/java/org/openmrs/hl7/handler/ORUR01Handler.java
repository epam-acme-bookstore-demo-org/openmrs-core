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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.openmrs.Concept;
import org.openmrs.ConceptProposal;
import org.openmrs.Encounter;
import org.openmrs.EncounterRole;
import org.openmrs.EncounterType;
import org.openmrs.Form;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.Person;
import org.openmrs.Provider;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.hl7.HL7Constants;
import org.openmrs.hl7.HL7InQueueProcessor;
import org.openmrs.util.OpenmrsConstants;
import org.openmrs.util.OpenmrsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.app.Application;
import ca.uhn.hl7v2.app.ApplicationException;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v25.datatype.CX;
import ca.uhn.hl7v2.model.v25.datatype.EI;
import ca.uhn.hl7v2.model.v25.datatype.FT;
import ca.uhn.hl7v2.model.v25.datatype.PL;
import ca.uhn.hl7v2.model.v25.datatype.TS;
import ca.uhn.hl7v2.model.v25.datatype.XCN;
import ca.uhn.hl7v2.model.v25.group.ORU_R01_ORDER_OBSERVATION;
import ca.uhn.hl7v2.model.v25.group.ORU_R01_PATIENT_RESULT;
import ca.uhn.hl7v2.model.v25.message.ORU_R01;
import ca.uhn.hl7v2.model.v25.segment.MSH;
import ca.uhn.hl7v2.model.v25.segment.NK1;
import ca.uhn.hl7v2.model.v25.segment.OBR;
import ca.uhn.hl7v2.model.v25.segment.OBX;
import ca.uhn.hl7v2.model.v25.segment.ORC;
import ca.uhn.hl7v2.model.v25.segment.PID;
import ca.uhn.hl7v2.model.v25.segment.PV1;
import ca.uhn.hl7v2.parser.EncodingCharacters;
import ca.uhn.hl7v2.parser.PipeParser;

/**
 * Parses ORUR01 messages into openmrs Encounter objects Usage: GenericParser parser = new
 * GenericParser(); MessageTypeRouter router = new MessageTypeRouter();
 * router.registerApplication("ORU", "R01", new ORUR01Handler()); Message hl7message =
 * parser.parse(somehl7string);
 *
 * @see HL7InQueueProcessor
 */
@Component
public class ORUR01Handler implements Application {

	private static final Logger log = LoggerFactory.getLogger(ORUR01Handler.class);

	private static EncounterRole unknownRole = null;

	/**
	 * Always returns true, assuming that the router calling this handler will only call this handler
	 * with ORU_R01 messages.
	 *
	 * @return true
	 */
	@Override
	public boolean canProcess(Message message) {
		return message != null && "ORU_R01".equals(message.getName());
	}

	/**
	 * Processes an ORU R01 event message Question datatype is coded or 1
	 * <p>
	 * <strong>Should</strong> create encounter and obs from hl7 message<br/>
	 * <strong>Should</strong> create basic concept proposal<br/>
	 * <strong>Should</strong> create concept proposal and with obs alongside<br/>
	 * <strong>Should</strong> not create problem list observation with concept proposals<br/>
	 * <strong>Should</strong> append to an existing encounter<br/>
	 * <strong>Should</strong> create obs group for OBRs<br/>
	 * <strong>Should</strong> create obs valueCodedName<br/>
	 * <strong>Should</strong> fail on empty concept proposals<br/>
	 * <strong>Should</strong> fail on empty concept answers<br/>
	 * <strong>Should</strong> set value_Coded matching a boolean concept for obs if the answer is 0 or
	 * 1 and<br/>
	 * <strong>Should</strong> set value as boolean for obs if the answer is 0 or 1 and Question
	 * datatype is Boolean<br/>
	 * <strong>Should</strong> set value_Numeric for obs if Question datatype is Numeric and the answer
	 * is either 0<br/>
	 * <strong>Should</strong> set value_Numeric for obs if Question datatype is Numeric<br/>
	 * <strong>Should</strong> fail if question datatype is coded and a boolean is not a valid
	 * answer<br/>
	 * <strong>Should</strong> fail if question datatype is neither Boolean nor numeric nor coded<br/>
	 * <strong>Should</strong> create an encounter and find the provider by identifier<br/>
	 * <strong>Should</strong> create an encounter and find the provider by personId<br/>
	 * <strong>Should</strong> create an encounter and find the provider by uuid<br/>
	 * <strong>Should</strong> create an encounter and find the provider by providerId<br/>
	 * <strong>Should</strong> fail if the provider name type code is not specified and is not a
	 * personId<br/>
	 * <strong>Should</strong> understand form uuid if present<br/>
	 * <strong>Should</strong> prefer form uuid over id if both are present<br/>
	 * <strong>Should</strong> prefer form id if uuid is not found<br/>
	 * <strong>Should</strong> set complex data for obs with complex concepts
	 */
	@Override
	public Message processMessage(Message message) throws ApplicationException {

		if (!(message instanceof ORU_R01)) {
			throw new ApplicationException(Context.getMessageSourceService().getMessage("ORUR01.error.invalidMessage"));
		}

		log.debug("Processing ORU_R01 message");

		Message response;
		try {
			ORU_R01 oru = (ORU_R01) message;
			response = processORU_R01(oru);
		} catch (ClassCastException e) {
			log.warn("Error casting " + message.getClass().getName() + " to ORU_R01", e);
			throw new ApplicationException(Context.getMessageSourceService().getMessage("ORUR01.error.invalidMessageType ",
			    new Object[] { message.getClass().getName() }, null), e);
		} catch (HL7Exception e) {
			log.warn("Error while processing ORU_R01 message", e);
			throw new ApplicationException(Context.getMessageSourceService().getMessage("ORUR01.error.WhileProcessing"), e);
		}

		log.debug("Finished processing ORU_R01 message");

		return response;
	}

	/**
	 * Bulk of the processing done here. Called by the main processMessage method
	 * <p>
	 * <strong>Should</strong> process multiple NK1 segments
	 *
	 * @param oru the message to process
	 * @return the processed message
	 * @throws HL7Exception
	 */
	private Message processORU_R01(ORU_R01 oru) throws HL7Exception {

		// TODO: ideally, we would branch or alter our behavior based on the
		// sending application.

		// validate message
		validate(oru);

		// extract segments for convenient use below
		MSH msh = getMSH(oru);
		PID pid = getPID(oru);
		List<NK1> nk1List = getNK1List(oru);
		PV1 pv1 = getPV1(oru);
		ORC orc = getORC(oru); // we're using the ORC assoc with first OBR to
		// hold data enterer and date entered for now

		// Obtain message control id (unique ID for message from sending
		// application)
		String messageControlId = msh.getMessageControlID().getValue();
		log.debug("Found HL7 message in inbound queue with control id = {}", messageControlId);
		// create the encounter
		Patient patient = HL7PatientHandler.getPatient(pid);
		log.debug("Processing HL7 message for patient {}", patient.getPatientId());
		Encounter encounter = createEncounter(msh, patient, pv1, orc);

		// do the discharge to location logic
		try {
			HL7PatientHandler.updateHealthCenter(patient, pv1);
		} catch (Exception e) {
			log.error("Error while processing Discharge To Location (" + messageControlId + ")", e);
		}

		// process NK1 (relationship) segments
		for (NK1 nk1 : nk1List) {
			HL7PatientHandler.processNK1(patient, nk1);
		}

		// list of concepts proposed in the obs of this encounter.
		// these proposals need to be created after the encounter
		// has been created
		var conceptProposals = new ArrayList<ConceptProposal>();

		// create observations
		log.debug("Creating observations for message {}...", messageControlId);
		// we ignore all MEDICAL_RECORD_OBSERVATIONS that are OBRs.  We do not
		// create obs_groups for them
		var ignoredConceptIds = new ArrayList<Integer>();

		String obrConceptId = Context.getAdministrationService()
		        .getGlobalProperty(OpenmrsConstants.GLOBAL_PROPERTY_MEDICAL_RECORD_OBSERVATIONS, "1238");
		if (StringUtils.hasLength(obrConceptId)) {
			ignoredConceptIds.add(Integer.valueOf(obrConceptId));
		}

		// we also ignore all PROBLEM_LIST that are OBRs
		String obrProblemListConceptId = Context.getAdministrationService()
		        .getGlobalProperty(OpenmrsConstants.GLOBAL_PROPERTY_PROBLEM_LIST, "1284");
		if (StringUtils.hasLength(obrProblemListConceptId)) {
			ignoredConceptIds.add(Integer.valueOf(obrProblemListConceptId));
		}

		ORU_R01_PATIENT_RESULT patientResult = oru.getPATIENT_RESULT();
		int numObr = patientResult.getORDER_OBSERVATIONReps();
		for (int i = 0; i < numObr; i++) {
			log.debug("Processing OBR ({} of {})", i, numObr);
			ORU_R01_ORDER_OBSERVATION orderObs = patientResult.getORDER_OBSERVATION(i);

			// the parent obr
			OBR obr = orderObs.getOBR();

			if (!StringUtils.hasText(obr.getUniversalServiceIdentifier().getIdentifier().getValue())) {
				throw new HL7Exception(Context.getMessageSourceService().getMessage("ORUR01.errorInvalidOBR ",
				    new Object[] { messageControlId }, null));
			}

			// if we're not ignoring this obs group, create an
			// Obs grouper object that the underlying obs objects will use
			Obs obsGrouper = null;
			Concept obrConcept = HL7ObservationHandler.getConcept(obr.getUniversalServiceIdentifier(), messageControlId);
			if (obrConcept != null && !ignoredConceptIds.contains(obrConcept.getId())) {
				// maybe check for a parent obs group from OBR-29 Parent ?

				// create an obs for this obs group too
				obsGrouper = new Obs();
				obsGrouper.setConcept(obrConcept);
				obsGrouper.setPerson(encounter.getPatient());
				obsGrouper.setEncounter(encounter);
				Date datetime = HL7ObservationHandler.getDatetime(obr);
				if (datetime == null) {
					datetime = encounter.getEncounterDatetime();
				}
				obsGrouper.setObsDatetime(datetime);
				obsGrouper.setLocation(encounter.getLocation());
				obsGrouper.setCreator(encounter.getCreator());

				// set comments if there are any
				var comments = new StringBuilder();
				ORU_R01_ORDER_OBSERVATION parent = (ORU_R01_ORDER_OBSERVATION) obr.getParent();
				int totalNTEs = parent.getNTEReps();
				for (int iNTE = 0; iNTE < totalNTEs; iNTE++) {
					for (FT obxComment : parent.getNTE(iNTE).getComment()) {
						if (comments.length() > 0) {
							comments.append(" ");
						}
						comments.append(obxComment.getValue());
					}
				}
				// only set comments if there are any
				if (StringUtils.hasText(comments.toString())) {
					obsGrouper.setComment(comments.toString());
				}

				// add this obs as another row in the obs table
				encounter.addObs(obsGrouper);
			}

			// loop over the obs and create each object, adding it to the encounter
			int numObs = orderObs.getOBSERVATIONReps();
			HL7Exception errorInHL7Queue = null;
			for (int j = 0; j < numObs; j++) {
				if (log.isDebugEnabled()) {
					log.debug("Processing OBS ({} of {})", j, numObs);
				}

				OBX obx = orderObs.getOBSERVATION(j).getOBX();
				try {
					log.debug("Parsing observation");
					Obs obs = HL7ObservationHandler.parseObs(encounter, obx, obr, messageControlId);
					if (obs != null) {

						// if we're backfilling an encounter, don't use
						// the creator/dateCreated from the encounter
						if (encounter.getEncounterId() != null) {
							obs.setCreator(getEnterer(orc));
							obs.setDateCreated(new Date());
						}

						// set the obsGroup on this obs
						if (obsGrouper != null) {
							// set the obs to the group.  This assumes the group is already
							// on the encounter and that when the encounter is saved it will
							// propagate to the children obs
							obsGrouper.addGroupMember(obs);
						} else {
							// set this obs on the encounter object that we
							// will be saving later
							log.debug("Obs is not null. Adding to encounter object");
							encounter.addObs(obs);
							log.debug("Done with this obs");
						}
					}
				} catch (ProposingConceptException proposingException) {
					Concept questionConcept = proposingException.getConcept();
					String value = proposingException.getValueName();
					//if the sender never specified any text for the proposed concept
					if (!StringUtils.isEmpty(value)) {
						conceptProposals.add(HL7ObservationHandler.createConceptProposal(encounter, questionConcept, value));
					} else {
						errorInHL7Queue = new HL7Exception(
						        Context.getMessageSourceService().getMessage("Hl7.proposed.concept.name.empty"),
						        proposingException);
						break;//stop any further processing of current message
					}

				} catch (HL7Exception e) {
					errorInHL7Queue = e;
				} finally {
					// Handle obs-level exceptions
					if (errorInHL7Queue != null) {
						throw new HL7Exception(
						        Context.getMessageSourceService().getMessage("ORUR01.error.improperlyFormattedOBX",
						            new Object[] { PipeParser.encode(obx, new EncodingCharacters('|', "^~\\&")) }, null),
						        HL7Exception.DATA_TYPE_ERROR, errorInHL7Queue);
					}
				}
			}

		}

		if (log.isDebugEnabled()) {
			log.debug("Finished creating observations");
			log.debug("Current thread: {}", Thread.currentThread());
			log.debug("Creating the encounter object");
		}
		Context.getEncounterService().saveEncounter(encounter);

		// loop over the proposed concepts and save each to the database
		// now that the encounter is saved
		for (ConceptProposal proposal : conceptProposals) {
			Context.getConceptService().saveConceptProposal(proposal);
		}

		return oru;

	}

	/**
	 * process an NK1 segment and add relationships if needed
	 * <p>
	 * <strong>Should</strong> create a relationship from a NK1 segment<br/>
	 * <strong>Should</strong> not create a relationship if one exists<br/>
	 * <strong>Should</strong> create a person if the relative is not found<br/>
	 * <strong>Should</strong> fail if the coding system is not 99REL<br/>
	 * <strong>Should</strong> fail if the relationship identifier is formatted improperly<br/>
	 * <strong>Should</strong> fail if the relationship type is not found
	 *
	 * @param patient
	 * @param nk1
	 * @throws HL7Exception
	 */
	protected void processNK1(Patient patient, NK1 nk1) throws HL7Exception {
		HL7PatientHandler.processNK1(patient, nk1);
	}

	/**
	 * Not used
	 *
	 * @param message
	 * @throws HL7Exception
	 */
	private void validate(Message message) throws HL7Exception {
		// TODO: check version, etc.
	}

	private MSH getMSH(ORU_R01 oru) {
		return oru.getMSH();
	}

	private PID getPID(ORU_R01 oru) {
		return oru.getPATIENT_RESULT().getPATIENT().getPID();
	}

	/**
	 * finds NK1 segments in an ORU_R01 message. all HAPI-rendered Messages have at least one NK1
	 * segment but if the original message truly does not contain an NK1, the setID will be null on the
	 * generated NK1
	 *
	 * @param oru ORU_R01 message to be parsed for NK1 segments
	 * @return list of not-null NK1 segments
	 * @throws HL7Exception
	 */
	public List<NK1> getNK1List(ORU_R01 oru) throws HL7Exception {
		var res = new ArrayList<NK1>();
		// there will always be at least one NK1, even if the original message does not contain one
		for (int i = 0; i < oru.getPATIENT_RESULT().getPATIENT().getNK1Reps(); i++) {
			// if the setIDNK1 value is null, this NK1 is blank
			if (oru.getPATIENT_RESULT().getPATIENT().getNK1(i).getSetIDNK1().getValue() != null) {
				res.add(oru.getPATIENT_RESULT().getPATIENT().getNK1(i));
			}
		}
		return res;
	}

	private PV1 getPV1(ORU_R01 oru) {
		return oru.getPATIENT_RESULT().getPATIENT().getVISIT().getPV1();
	}

	private ORC getORC(ORU_R01 oru) {
		return oru.getPATIENT_RESULT().getORDER_OBSERVATION().getORC();
	}

	/**
	 * This method does not call the database to create the encounter row. The encounter is only created
	 * after all obs have been attached to it Creates an encounter pojo to be attached later. This
	 * method does not create an encounterId
	 *
	 * @param msh
	 * @param patient
	 * @param pv1
	 * @param orc
	 * @return
	 * @throws HL7Exception
	 */
	private Encounter createEncounter(MSH msh, Patient patient, PV1 pv1, ORC orc) throws HL7Exception {

		// the encounter we will return
		Encounter encounter;

		// look for the encounter id in PV1-19
		CX visitNumber = pv1.getVisitNumber();
		Integer encounterId = null;
		try {
			encounterId = Integer.valueOf(visitNumber.getIDNumber().getValue());
		} catch (NumberFormatException e) {
			// pass
		}

		// if an encounterId was passed in, assume that these obs are
		// going to be appended to it.  Fetch the old encounter from
		// the database
		if (encounterId != null) {
			encounter = Context.getEncounterService().getEncounter(encounterId);
		} else {
			// if no encounter_id was passed in, this is a new
			// encounter, create the object
			encounter = new Encounter();

			Date encounterDate = getEncounterDate(pv1);
			Provider provider = getProvider(pv1);
			Location location = getLocation(pv1);
			Form form = getForm(msh);
			EncounterType encounterType = getEncounterType(msh, form);
			User enterer = getEnterer(orc);
			//			Date dateEntered = getDateEntered(orc); // ignore this since we have no place in the data model to store it

			encounter.setEncounterDatetime(encounterDate);
			if (unknownRole == null) {
				unknownRole = Context.getEncounterService()
				        .getEncounterRoleByUuid(EncounterRole.UNKNOWN_ENCOUNTER_ROLE_UUID);
			}
			encounter.setProvider(unknownRole, provider);
			encounter.setPatient(patient);
			encounter.setLocation(location);
			encounter.setForm(form);
			encounter.setEncounterType(encounterType);
			encounter.setCreator(enterer);
			encounter.setDateCreated(new Date());
		}

		return encounter;
	}

	/**
	 * Get a concept object representing this conceptId and coding system.<br>
	 * If codingSystem is 99DCT, then a new Concept with the given conceptId is returned.<br>
	 * Otherwise, the coding system is looked up in the ConceptMap for an openmrs concept mapped to that
	 * code.
	 * <p>
	 * <strong>Should</strong> return null if codingSystem not found<br/>
	 * <strong>Should</strong> return a Concept if given local coding system<br/>
	 * <strong>Should</strong> return a mapped Concept if given a valid mapping
	 *
	 * @param hl7ConceptId the given hl7 conceptId
	 * @param codingSystem the coding system for this conceptid (e.g. 99DCT)
	 * @param uid unique string for this message for any error reporting purposes
	 * @return a Concept object or null if no conceptId with given coding system found
	 */
	protected Concept getConcept(String hl7ConceptId, String codingSystem, String uid) throws HL7Exception {
		return HL7ObservationHandler.getConcept(hl7ConceptId, codingSystem, uid);
	}

	private Date getEncounterDate(PV1 pv1) throws HL7Exception {
		return tsToDate(pv1.getAdmitDateTime());
	}

	private Provider getProvider(PV1 pv1) throws HL7Exception {
		XCN hl7Provider = pv1.getAttendingDoctor(0);
		Provider provider = null;
		String id = hl7Provider.getIDNumber().getValue();
		String assignAuth = hl7Provider.getAssigningAuthority().getUniversalID().getValue();
		String type = hl7Provider.getAssigningAuthority().getUniversalIDType().getValue();
		String errorMessage;
		if (StringUtils.hasText(id)) {
			String specificErrorMsg = "";
			if (OpenmrsUtil.nullSafeEquals("L", type)) {
				if (HL7Constants.PROVIDER_ASSIGNING_AUTH_PROV_ID.equalsIgnoreCase(assignAuth)) {
					try {
						provider = Context.getProviderService().getProvider(Integer.valueOf(id));
					} catch (NumberFormatException e) {
						// ignore
					}
					specificErrorMsg = "with provider Id";
				} else if (HL7Constants.PROVIDER_ASSIGNING_AUTH_IDENTIFIER.equalsIgnoreCase(assignAuth)) {
					provider = Context.getProviderService().getProviderByIdentifier(id);
					specificErrorMsg = "with provider identifier";
				} else if (HL7Constants.PROVIDER_ASSIGNING_AUTH_PROV_UUID.equalsIgnoreCase(assignAuth)) {
					provider = Context.getProviderService().getProviderByUuid(id);
					specificErrorMsg = "with provider uuid";
				}
			} else {
				try {
					Person person = Context.getPersonService().getPerson(Integer.valueOf(id));
					Collection<Provider> providers = Context.getProviderService().getProvidersByPerson(person);
					if (!providers.isEmpty()) {
						provider = providers.iterator().next();
					}
				} catch (NumberFormatException e) {
					// ignore
				}
				specificErrorMsg = "associated to a person with person id";
			}

			errorMessage = "Could not resolve provider " + specificErrorMsg + ":" + id;
		} else {
			errorMessage = "No unique identifier was found for the provider";
		}

		if (provider == null) {
			throw new HL7Exception(errorMessage);
		}

		return provider;
	}

	private Location getLocation(PV1 pv1) throws HL7Exception {
		PL hl7Location = pv1.getAssignedPatientLocation();
		Integer locationId = Context.getHL7Service().resolveLocationId(hl7Location);
		if (locationId == null) {
			throw new HL7Exception(Context.getMessageSourceService().getMessage("ORUR01.error.UnresolvedLocation"));
		}

		return Context.getLocationService().getLocation(locationId);
	}

	/**
	 * needs to find a Form based on information in MSH-21. example: 16^AMRS.ELD.FORMID
	 * <p>
	 * <strong>Should</strong> pass if return value is null when uuid and id is null<br/>
	 * <strong>Should</strong> pass if return value is not null when uuid or id is not null
	 *
	 * @param msh
	 * @return
	 * @throws HL7Exception
	 */
	public Form getForm(MSH msh) throws HL7Exception {
		String uuid = null;
		String id = null;

		for (EI identifier : msh.getMessageProfileIdentifier()) {
			if (identifier != null && identifier.getNamespaceID() != null) {
				String identifierType = identifier.getNamespaceID().getValue();
				if (OpenmrsUtil.nullSafeEquals(identifierType, HL7Constants.HL7_FORM_UUID)) {
					uuid = identifier.getEntityIdentifier().getValue();
				} else if (OpenmrsUtil.nullSafeEquals(identifierType, HL7Constants.HL7_FORM_ID)) {
					id = identifier.getEntityIdentifier().getValue();
				} else {
					log.warn("Form identifier type of " + identifierType + " unknown to ORU R01 processor.");
				}
			}
		}

		Form form = null;

		if (uuid == null && id == null) {
			return form;
		}

		// prefer uuid over id
		if (uuid != null) {
			form = Context.getFormService().getFormByUuid(uuid);
		}

		// if uuid did not work ...
		if (id != null) {

			try {
				Integer formId = Integer.parseInt(id);
				form = Context.getFormService().getForm(formId);
			} catch (NumberFormatException e) {
				throw new HL7Exception(Context.getMessageSourceService().getMessage("ORUR01.error.parseFormId"), e);
			}

		}

		return form;

	}

	private EncounterType getEncounterType(MSH msh, Form form) {
		if (form != null) {
			return form.getEncounterType();
		}
		// TODO: resolve encounter type from MSH data - do we need PV1 too?
		return null;
	}

	private User getEnterer(ORC orc) throws HL7Exception {
		XCN hl7Enterer = orc.getEnteredBy(0);
		Integer entererId = Context.getHL7Service().resolveUserId(hl7Enterer);
		if (entererId == null) {
			throw new HL7Exception(Context.getMessageSourceService().getMessage("ORUR01.error.UnresolvedEnterer"));
		}
		var enterer = new User();
		enterer.setUserId(entererId);
		return enterer;
	}

	//TODO: Debug (and use) methods in HL7Util instead
	private Date tsToDate(TS ts) throws HL7Exception {
		// need to handle timezone
		String dtm = ts.getTime().getValue();
		int year = Integer.parseInt(dtm.substring(0, 4));
		int month = (dtm.length() >= 6 ? Integer.parseInt(dtm.substring(4, 6)) - 1 : 0);
		int day = (dtm.length() >= 8 ? Integer.parseInt(dtm.substring(6, 8)) : 1);
		int hour = (dtm.length() >= 10 ? Integer.parseInt(dtm.substring(8, 10)) : 0);
		int min = (dtm.length() >= 12 ? Integer.parseInt(dtm.substring(10, 12)) : 0);
		int sec = (dtm.length() >= 14 ? Integer.parseInt(dtm.substring(12, 14)) : 0);
		Calendar cal = Calendar.getInstance();
		cal.set(year, month, day, hour, min, sec);

		return cal.getTime();
	}
}
