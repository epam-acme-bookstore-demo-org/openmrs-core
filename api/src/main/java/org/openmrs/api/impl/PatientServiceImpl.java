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
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.Allergen;
import org.openmrs.Allergies;
import org.openmrs.Allergy;
import org.openmrs.Concept;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PatientProgram;
import org.openmrs.Person;
import org.openmrs.api.APIException;
import org.openmrs.api.PatientIdentifierException;
import org.openmrs.api.PatientService;
import org.openmrs.api.RefByUuid;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.PatientDAO;
import org.openmrs.api.db.hibernate.HibernateUtil;
import org.openmrs.parameter.PatientSearchCriteria;
import org.openmrs.patient.IdentifierValidator;
import org.openmrs.patient.impl.LuhnIdentifierValidator;
import org.openmrs.serialization.SerializationException;
import org.openmrs.util.OpenmrsConstants;
import org.openmrs.util.OpenmrsUtil;
import org.openmrs.util.PrivilegeConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of the patient service. This class should not be used on its own. The
 * current OpenMRS implementation should be fetched from the Context via
 * <code>Context.getPatientService()</code>
 *
 * @see org.openmrs.api.context.Context
 * @see org.openmrs.api.PatientService
 * @see org.openmrs.api.PersonService
 */
@Service("patientService")
@Transactional
public class PatientServiceImpl extends BaseOpenmrsService implements PatientService, RefByUuid {

	private static final Logger log = LoggerFactory.getLogger(PatientServiceImpl.class);

	private PatientDAO dao;

	private PatientMergeDelegate mergeDelegate;

	private PatientIdentifierDelegate identifierDelegate;

	/**
	 * PatientIdentifierValidators registered through spring's applicationContext-service.xml
	 */
	@Autowired
	@Qualifier("identifierValidators")
	private Map<Class<? extends IdentifierValidator>, IdentifierValidator> identifierValidators;

	/**
	 * @see org.openmrs.api.PatientService#setPatientDAO(org.openmrs.api.db.PatientDAO)
	 */
	@Autowired
	@Override
	public void setPatientDAO(PatientDAO dao) {
		this.dao = dao;
		this.mergeDelegate = new PatientMergeDelegate();
		this.identifierDelegate = new PatientIdentifierDelegate(dao);
	}

	/**
	 * Clean up after this class. Set the static var to null so that the classloader can reclaim the
	 * space.
	 *
	 * @see org.openmrs.api.impl.BaseOpenmrsService#onShutdown()
	 */
	@Override
	public void onShutdown() {
		setIdentifierValidators(null);
	}

	/**
	 * @see org.openmrs.api.PatientService#savePatient(org.openmrs.Patient)
	 */
	@Override
	public Patient savePatient(Patient patient) throws APIException {
		identifierDelegate.requireAppropriatePatientModificationPrivilege(patient);

		if (!patient.getVoided() && patient.getIdentifiers().size() == 1) {
			patient.getPatientIdentifier().setPreferred(true);
		}

		if (!patient.getVoided()) {
			identifierDelegate.checkPatientIdentifiers(patient);
		}

		identifierDelegate.setPreferredPatientIdentifier(patient);
		identifierDelegate.setPreferredPatientName(patient);
		identifierDelegate.setPreferredPatientAddress(patient);

		return dao.savePatient(patient);
	}

	/**
	 * @see org.openmrs.api.PatientService#getPatient(java.lang.Integer)
	 */
	@Override
	@Transactional(readOnly = true)
	public Patient getPatient(Integer patientId) throws APIException {
		return dao.getPatient(patientId);
	}

	@Override
	@Transactional(readOnly = true)
	public Patient getPatientOrPromotePerson(Integer patientOrPersonId) {
		Person person = Context.getPersonService().getPerson(patientOrPersonId);
		if (person == null) {
			return null;
		}
		person = HibernateUtil.getRealObjectFromProxy(person);
		if (person instanceof Patient patient) {
			return patient;
		} else {
			return new Patient(person);
		}
	}

	/**
	 * @see org.openmrs.api.PatientService#getAllPatients()
	 */
	@Override
	@Transactional(readOnly = true)
	public List<Patient> getAllPatients() throws APIException {
		return Context.getPatientService().getAllPatients(false);
	}

	/**
	 * @see org.openmrs.api.PatientService#getAllPatients(boolean)
	 */
	@Override
	@Transactional(readOnly = true)
	@Deprecated(since = "3.0.0", forRemoval = true)
	public List<Patient> getAllPatients(boolean includeVoided) throws APIException {
		return dao.getAllPatients(includeVoided);
	}

	/**
	 * @see org.openmrs.api.PatientService#getAllPatientsIncludingVoided()
	 */
	@Override
	@Transactional(readOnly = true)
	public List<Patient> getAllPatientsIncludingVoided() throws APIException {
		return dao.getAllPatients(true);
	}

	/**
	 * @see org.openmrs.api.PatientService#getPatients(java.lang.String, java.lang.String,
	 *      java.util.List, boolean)
	 */
	@Override
	// TODO - search for usage with non-empty list of patient identifier types
	@Transactional(readOnly = true)
	public List<Patient> getPatients(String name, String identifier, List<PatientIdentifierType> identifierTypes,
	        boolean matchIdentifierExactly) throws APIException {

		return Context.getPatientService().getPatients(name, identifier, identifierTypes, matchIdentifierExactly, 0, null);
	}

	/**
	 * @see org.openmrs.api.PatientService#checkPatientIdentifiers(org.openmrs.Patient)
	 */
	@Override
	@Transactional(readOnly = true)
	public void checkPatientIdentifiers(Patient patient) throws PatientIdentifierException {
		identifierDelegate.checkPatientIdentifiers(patient);
	}

	/**
	 * @see org.openmrs.api.PatientService#voidPatient(org.openmrs.Patient, java.lang.String)
	 */
	@Override
	public Patient voidPatient(Patient patient, String reason) throws APIException {
		if (patient == null) {
			return null;
		}

		// patient and patientidentifier attributes taken care of by the BaseVoidHandler
		//call the DAO layer directly to avoid any further AOP around save*
		return dao.savePatient(patient);
	}

	/**
	 * @see org.openmrs.api.PatientService#unvoidPatient(org.openmrs.Patient)
	 */
	@Override
	public Patient unvoidPatient(Patient patient) throws APIException {
		if (patient == null) {
			return null;
		}

		// patient and patientidentifier attributes taken care of by the BaseUnvoidHandler

		return Context.getPatientService().savePatient(patient);
	}

	/**
	 * @see org.openmrs.api.PatientService#purgePatient(org.openmrs.Patient)
	 */
	@Override
	public void purgePatient(Patient patient) throws APIException {
		dao.deletePatient(patient);
	}

	// patient identifier section

	/**
	 * @see org.openmrs.api.PatientService#getPatientIdentifiers(java.lang.String, java.util.List,
	 *      java.util.List, java.util.List, java.lang.Boolean)
	 */
	@Override
	@Transactional(readOnly = true)
	public List<PatientIdentifier> getPatientIdentifiers(String identifier,
	        List<PatientIdentifierType> patientIdentifierTypes, List<Location> locations, List<Patient> patients,
	        Boolean isPreferred) throws APIException {

		if (patientIdentifierTypes == null) {
			patientIdentifierTypes = new ArrayList<>();
		}

		if (locations == null) {
			locations = new ArrayList<>();
		}

		if (patients == null) {
			patients = new ArrayList<>();
		}

		return dao.getPatientIdentifiers(identifier, patientIdentifierTypes, locations, patients, isPreferred);
	}
	// end patient identifier section

	// patient identifier _type_ section

	/**
	 * @see org.openmrs.api.PatientService#savePatientIdentifierType(org.openmrs.PatientIdentifierType)
	 */
	@Override
	public PatientIdentifierType savePatientIdentifierType(PatientIdentifierType patientIdentifierType) throws APIException {
		checkIfPatientIdentifierTypesAreLocked();
		return dao.savePatientIdentifierType(patientIdentifierType);
	}

	/**
	 * @see org.openmrs.api.PatientService#getAllPatientIdentifierTypes()
	 */
	@Override
	@Transactional(readOnly = true)
	public List<PatientIdentifierType> getAllPatientIdentifierTypes() throws APIException {
		return Context.getPatientService().getAllPatientIdentifierTypes(false);
	}

	/**
	 * @see org.openmrs.api.PatientService#getAllPatientIdentifierTypes(boolean)
	 */
	@Override
	@Transactional(readOnly = true)
	@Deprecated(since = "3.0.0", forRemoval = true)
	public List<PatientIdentifierType> getAllPatientIdentifierTypes(boolean includeRetired) throws APIException {
		return dao.getAllPatientIdentifierTypes(includeRetired);
	}

	/**
	 * @see org.openmrs.api.PatientService#getAllPatientIdentifierTypesIncludingRetired()
	 */
	@Override
	@Transactional(readOnly = true)
	public List<PatientIdentifierType> getAllPatientIdentifierTypesIncludingRetired() throws APIException {
		return dao.getAllPatientIdentifierTypes(true);
	}

	/**
	 * @see org.openmrs.api.PatientService#getPatientIdentifierTypes(java.lang.String, java.lang.String,
	 *      java.lang.Boolean, java.lang.Boolean)
	 */
	@Override
	@Transactional(readOnly = true)
	public List<PatientIdentifierType> getPatientIdentifierTypes(String name, String format, Boolean required,
	        Boolean hasCheckDigit) throws APIException {
		List<PatientIdentifierType> patientIdentifierTypes = dao.getPatientIdentifierTypes(name, format, required,
		    hasCheckDigit);
		if (patientIdentifierTypes == null) {
			return new ArrayList<>();
		}
		return patientIdentifierTypes;
	}

	/**
	 * @see org.openmrs.api.PatientService#getPatientIdentifierType(java.lang.Integer)
	 */
	@Override
	@Transactional(readOnly = true)
	public PatientIdentifierType getPatientIdentifierType(Integer patientIdentifierTypeId) throws APIException {
		return dao.getPatientIdentifierType(patientIdentifierTypeId);
	}

	/**
	 * @see org.openmrs.api.PatientService#getPatientIdentifierTypeByName(java.lang.String)
	 */
	@Override
	@Transactional(readOnly = true)
	public PatientIdentifierType getPatientIdentifierTypeByName(String name) throws APIException {
		List<PatientIdentifierType> types = getPatientIdentifierTypes(name, null, null, null);

		if (!types.isEmpty()) {
			return types.getFirst();
		}

		return null;
	}

	/**
	 * @see org.openmrs.api.PatientService#retirePatientIdentifierType(org.openmrs.PatientIdentifierType,
	 *      String)
	 */
	@Override
	public PatientIdentifierType retirePatientIdentifierType(PatientIdentifierType patientIdentifierType, String reason)
	        throws APIException {
		checkIfPatientIdentifierTypesAreLocked();
		if (reason == null || reason.length() < 1) {
			throw new APIException("Patient.identifier.retire.reason", (Object[]) null);
		}

		patientIdentifierType.setRetired(true);
		patientIdentifierType.setRetiredBy(Context.getAuthenticatedUser());
		patientIdentifierType.setDateRetired(new Date());
		patientIdentifierType.setRetireReason(reason);
		return Context.getPatientService().savePatientIdentifierType(patientIdentifierType);
	}

	/**
	 * @see org.openmrs.api.PatientService#unretirePatientIdentifierType(org.openmrs.PatientIdentifierType)
	 */
	@Override
	public PatientIdentifierType unretirePatientIdentifierType(PatientIdentifierType patientIdentifierType)
	        throws APIException {
		checkIfPatientIdentifierTypesAreLocked();
		patientIdentifierType.setRetired(false);
		patientIdentifierType.setRetiredBy(null);
		patientIdentifierType.setDateRetired(null);
		patientIdentifierType.setRetireReason(null);
		return Context.getPatientService().savePatientIdentifierType(patientIdentifierType);
	}

	/**
	 * @see org.openmrs.api.PatientService#purgePatientIdentifierType(org.openmrs.PatientIdentifierType)
	 */
	@Override
	public void purgePatientIdentifierType(PatientIdentifierType patientIdentifierType) throws APIException {
		checkIfPatientIdentifierTypesAreLocked();
		dao.deletePatientIdentifierType(patientIdentifierType);
	}

	// end patient identifier _type_ section

	/**
	 * @see org.openmrs.api.PatientService#getPatients(java.lang.String)
	 */
	@Override
	@Transactional(readOnly = true)
	public List<Patient> getPatients(String query) throws APIException {
		return Context.getPatientService().getPatients(query, 0, null);
	}

	/**
	 * This default implementation simply looks at the OpenMRS internal id (patient_id). If the id is
	 * null, assume this patient isn't found. If the patient_id is not null, try and find that id in the
	 * database
	 *
	 * @see org.openmrs.api.PatientService#getPatientByExample(org.openmrs.Patient)
	 */
	@Override
	@Transactional(readOnly = true)
	public Patient getPatientByExample(Patient patientToMatch) throws APIException {
		if (patientToMatch == null || patientToMatch.getPatientId() == null) {
			return null;
		}

		return Context.getPatientService().getPatient(patientToMatch.getPatientId());
	}

	/**
	 * @see org.openmrs.api.PatientService#getDuplicatePatientsByAttributes(java.util.List)
	 */
	@Override
	@Transactional(readOnly = true)
	public List<Patient> getDuplicatePatientsByAttributes(List<String> attributes) throws APIException {

		if (attributes == null || attributes.isEmpty()) {
			throw new APIException("Patient.no.attribute", (Object[]) null);
		}

		return dao.getDuplicatePatientsByAttributes(attributes);
	}

	/**
	 * generate a relationship hash for use in mergePatients; follows the convention:
	 * [relationshipType][A|B][relativeId]
	 *
	 * @param rel relationship under consideration
	 * @param primary the focus of the hash
	 * @return hash depicting relevant information to avoid duplicates
	 */

	/**
	 * 1) Moves object (encounters/obs) pointing to <code>nonPreferred</code> to <code>preferred</code>
	 * 2) Copies data (gender/birthdate/names/ids/etc) from <code>nonPreferred</code> to
	 * <code>preferred</code> iff the data is missing or null in <code>preferred</code> 3)
	 * <code>notPreferred</code> is marked as voided
	 *
	 * @param preferred
	 * @param notPreferred
	 * @throws APIException
	 * @see org.openmrs.api.PatientService#mergePatients(org.openmrs.Patient, org.openmrs.Patient)
	 */
	@Override
	public void mergePatients(Patient preferred, Patient notPreferred) throws APIException, SerializationException {
		mergeDelegate.mergePatients(preferred, notPreferred, this);
	}

	/**
	 * This is the way to establish that a patient has left the care center. This API call is
	 * responsible for:
	 * <ol>
	 * <li>Closing workflow statuses</li>
	 * <li>Terminating programs</li>
	 * <li>Discontinuing orders</li>
	 * <li>Flagging patient table</li>
	 * <li>Creating any relevant observations about the patient (if applicable)</li>
	 * </ol>
	 *
	 * @param patient - the patient who has exited care
	 * @param dateExited - the declared date/time of the patient's exit
	 * @param reasonForExit - the concept that corresponds with why the patient has been declared as
	 *            exited
	 * @throws APIException
	 */
	public void exitFromCare(Patient patient, Date dateExited, Concept reasonForExit) throws APIException {
		identifierDelegate.exitFromCare(patient, dateExited, reasonForExit);
	}

	@Override
	public void processDeath(Patient patient, Date dateDied, Concept causeOfDeath, String otherReason) throws APIException {
		identifierDelegate.processDeath(patient, dateDied, causeOfDeath, otherReason, this);
	}

	/**
	 * @see org.openmrs.api.PatientService#saveCauseOfDeathObs(org.openmrs.Patient, java.util.Date,
	 *      org.openmrs.Concept, java.lang.String)
	 */
	@Override
	public void saveCauseOfDeathObs(Patient patient, Date deathDate, Concept cause, String otherReason) throws APIException {
		identifierDelegate.saveCauseOfDeathObs(patient, deathDate, cause, otherReason);
	}

	/**
	 * @see org.openmrs.api.PatientService#getPatientByUuid(java.lang.String)
	 */
	@Override
	@Transactional(readOnly = true)
	public Patient getPatientByUuid(String uuid) throws APIException {
		return dao.getPatientByUuid(uuid);
	}

	@Override
	@Transactional(readOnly = true)
	public PatientIdentifier getPatientIdentifierByUuid(String uuid) throws APIException {
		return dao.getPatientIdentifierByUuid(uuid);
	}

	/**
	 * @see org.openmrs.api.PatientService#getPatientIdentifierTypeByUuid(java.lang.String)
	 */
	@Override
	@Transactional(readOnly = true)
	public PatientIdentifierType getPatientIdentifierTypeByUuid(String uuid) throws APIException {
		return dao.getPatientIdentifierTypeByUuid(uuid);
	}

	/**
	 * @see org.openmrs.api.PatientService#getDefaultIdentifierValidator()
	 */
	@Override
	@Transactional(readOnly = true)
	public IdentifierValidator getDefaultIdentifierValidator() {
		String defaultPIV = Context.getAdministrationService()
		        .getGlobalProperty(OpenmrsConstants.GLOBAL_PROPERTY_DEFAULT_PATIENT_IDENTIFIER_VALIDATOR, "");

		try {
			return identifierValidators.get(Class.forName(defaultPIV));
		} catch (ClassNotFoundException e) {
			log.error("Global Property " + OpenmrsConstants.GLOBAL_PROPERTY_DEFAULT_PATIENT_IDENTIFIER_VALIDATOR
			        + " not set to an actual class.",
			    e);
			return identifierValidators.get(LuhnIdentifierValidator.class);
		}
	}

	/**
	 * @see org.openmrs.api.PatientService#getIdentifierValidator(java.lang.String)
	 */
	@Override
	public IdentifierValidator getIdentifierValidator(Class<IdentifierValidator> identifierValidator) {
		return identifierValidators.get(identifierValidator);
	}

	public Map<Class<? extends IdentifierValidator>, IdentifierValidator> getIdentifierValidators() {
		if (identifierValidators == null) {
			identifierValidators = new LinkedHashMap<>();
		}
		return identifierValidators;
	}

	/**
	 * ADDs identifierValidators, doesn't replace them
	 *
	 * @param identifierValidators
	 */
	public void setIdentifierValidators(
	        Map<Class<? extends IdentifierValidator>, IdentifierValidator> identifierValidators) {
		if (identifierValidators == null) {
			this.identifierValidators = null;
			return;
		}
		for (Map.Entry<Class<? extends IdentifierValidator>, IdentifierValidator> entry : identifierValidators.entrySet()) {
			getIdentifierValidators().put(entry.getKey(), entry.getValue());
		}
	}

	/**
	 * @see org.openmrs.api.PatientService#getAllIdentifierValidators()
	 */
	@Override
	public Collection<IdentifierValidator> getAllIdentifierValidators() {
		return identifierValidators.values();
	}

	/**
	 * @see org.openmrs.api.PatientService#getIdentifierValidator(java.lang.String)
	 */
	@Override
	@SuppressWarnings("unchecked") // Type-checked dispatch: each branch casts after verifying Class<T> type
	@Transactional(readOnly = true)
	public IdentifierValidator getIdentifierValidator(String pivClassName) {
		if (StringUtils.isBlank(pivClassName)) {
			return null;
		}

		try {
			return getIdentifierValidator((Class<IdentifierValidator>) Context.loadClass(pivClassName));
		} catch (ClassNotFoundException e) {
			throw new PatientIdentifierException("Could not find patient identifier validator " + pivClassName, e);
		}
	}

	/**
	 * @see org.openmrs.api.PatientService#isIdentifierInUseByAnotherPatient(org.openmrs.PatientIdentifier)
	 */
	@Override
	@Transactional(readOnly = true)
	public boolean isIdentifierInUseByAnotherPatient(PatientIdentifier patientIdentifier) {
		return dao.isIdentifierInUseByAnotherPatient(patientIdentifier);
	}

	/**
	 * @see org.openmrs.api.PatientService#getPatientIdentifier(java.lang.Integer)
	 */
	@Override
	@Transactional(readOnly = true)
	public PatientIdentifier getPatientIdentifier(Integer patientIdentifierId) throws APIException {
		return dao.getPatientIdentifier(patientIdentifierId);
	}

	/**
	 * @see org.openmrs.api.PatientService#voidPatientIdentifier(org.openmrs.PatientIdentifier,
	 *      java.lang.String)
	 */
	@Override
	public PatientIdentifier voidPatientIdentifier(PatientIdentifier patientIdentifier, String reason) throws APIException {

		if (patientIdentifier == null || StringUtils.isBlank(reason)) {
			throw new APIException("Patient.identifier.cannot.be.null", (Object[]) null);
		}
		return Context.getPatientService().savePatientIdentifier(patientIdentifier);

	}

	/**
	 * @see org.openmrs.api.PatientService#mergePatients(org.openmrs.Patient, java.util.List)
	 */
	@Override
	public void mergePatients(Patient preferred, List<Patient> notPreferred) throws APIException, SerializationException {

		for (Patient nonPreferred : notPreferred) {
			mergePatients(preferred, nonPreferred);
		}
	}

	/**
	 * @see org.openmrs.api.PatientService#savePatientIdentifier(org.openmrs.PatientIdentifier)
	 */
	@Override
	public PatientIdentifier savePatientIdentifier(PatientIdentifier patientIdentifier) throws APIException {
		//if the argument or the following required fields are not specified
		PatientIdentifierType.LocationBehavior locationBehavior = null;
		if (patientIdentifier != null) {
			locationBehavior = patientIdentifier.getIdentifierType().getLocationBehavior();
		}

		if (patientIdentifier == null || patientIdentifier.getPatient() == null
		        || patientIdentifier.getIdentifierType() == null || StringUtils.isBlank(patientIdentifier.getIdentifier())
		        || (locationBehavior == PatientIdentifierType.LocationBehavior.REQUIRED
		                && patientIdentifier.getLocation() == null)) {
			throw new APIException("Patient.identifier.null", (Object[]) null);
		}
		if (patientIdentifier.getPatientIdentifierId() == null) {
			Context.requirePrivilege(PrivilegeConstants.ADD_PATIENT_IDENTIFIERS);
		} else {
			Context.requirePrivilege(PrivilegeConstants.EDIT_PATIENT_IDENTIFIERS);
		}

		return dao.savePatientIdentifier(patientIdentifier);
	}

	/**
	 * @see org.openmrs.api.PatientService#purgePatientIdentifier(org.openmrs.PatientIdentifier)
	 */
	@Override
	public void purgePatientIdentifier(PatientIdentifier patientIdentifier) throws APIException {

		dao.deletePatientIdentifier(patientIdentifier);

	}

	/**
	 * @see org.openmrs.api.PatientService#getAllergies(org.openmrs.Patient)
	 */
	@Override
	@Transactional(readOnly = true)
	public Allergies getAllergies(Patient patient) {
		if (patient == null) {
			throw new IllegalArgumentException("An existing (NOT NULL) patient is required to get allergies");
		}

		var allergies = new Allergies();
		List<Allergy> allergyList = dao.getAllergies(patient);
		if (!allergyList.isEmpty()) {
			allergies.addAll(allergyList);
		} else {
			String status = dao.getAllergyStatus(patient);
			if (Allergies.NO_KNOWN_ALLERGIES.equals(status)) {
				allergies.confirmNoKnownAllergies();
			}
		}
		return allergies;
	}

	/**
	 * @see org.openmrs.api.PatientService#setAllergies(org.openmrs.Patient, org.openmrs.Allergies)
	 */
	@Override
	public Allergies setAllergies(Patient patient, Allergies allergies) {
		//NOTE We neither delete nor edit allergies. We instead void them.
		//Because we shield the API users from this business logic,
		//we end up with the complicated code below. :)

		//get the current allergies as stored in the database
		List<Allergy> dbAllergyList = getAllergies(patient);
		for (Allergy originalAllergy : dbAllergyList) {
			//check if we still have each allergy, else it has just been deleted
			if (allergies.contains(originalAllergy)) {
				//we still have this allergy, check if it has been edited/changed
				Allergy potentiallyEditedAllergy = allergies.getAllergy(originalAllergy.getAllergyId());
				if (!potentiallyEditedAllergy.hasSameValues(originalAllergy)) {
					//allergy has been edited, so void it and create a new one with the current values
					var newAllergy = new Allergy();
					try {
						//remove the edited allergy from our current list, and void id
						allergies.remove(potentiallyEditedAllergy);

						//copy values from edited allergy, and add it to the current list
						newAllergy.copy(potentiallyEditedAllergy);
						allergies.add(newAllergy);

						//we void its original values, as came from the database,
						//instead the current ones which have just been copied
						//into the new allergy we have just created above
						voidAllergy(originalAllergy);
					} catch (Exception ex) {
						throw new APIException("Failed to copy edited values", ex);
					}
				}
				continue;
			}

			//void the allergy that has been deleted
			voidAllergy(originalAllergy);
		}

		for (Allergy allergy : allergies) {
			if (allergy.getAllergyId() == null && allergy.getAllergen().getCodedAllergen() == null
			        && StringUtils.isNotBlank(allergy.getAllergen().getNonCodedAllergen())) {

				Concept otherNonCoded = Context.getConceptService().getConceptByUuid(Allergen.getOtherNonCodedConceptUuid());
				if (otherNonCoded == null) {
					throw new APIException("Can't find concept with uuid:" + Allergen.getOtherNonCodedConceptUuid());
				}
				allergy.getAllergen().setCodedAllergen(otherNonCoded);
			}
		}

		return dao.saveAllergies(patient, allergies);
	}

	/**
	 * Voids a given allergy
	 *
	 * @param allergy the allergy to void
	 */
	private void voidAllergy(Allergy allergy) {
		allergy.setVoided(true);
		allergy.setVoidedBy(Context.getAuthenticatedUser());
		allergy.setDateVoided(new Date());
		allergy.setVoidReason("Voided by API");
		dao.saveAllergy(allergy);
	}

	/**
	 * @see org.openmrs.api.PatientService#getAllergy(java.lang.Integer)
	 */
	@Override
	@Transactional(readOnly = true)
	public Allergy getAllergy(Integer allergyId) throws APIException {
		return dao.getAllergy(allergyId);
	}

	/**
	 * @see org.openmrs.api.PatientService#getAllergyByUuid(java.lang.String)
	 */
	@Override
	@Transactional(readOnly = true)
	public Allergy getAllergyByUuid(String uuid) throws APIException {
		return dao.getAllergyByUuid(uuid);
	}

	/**
	 * @see org.openmrs.api.PatientService#saveAllergy(org.openmrs.Allergy)
	 */
	@Override
	public void saveAllergy(Allergy allergy) throws APIException {

		dao.saveAllergy(allergy);
	}

	/**
	 * @see org.openmrs.api.PatientService#removeAllergy(org.openmrs.Allergy, java.lang.String)
	 */
	@Override
	public void removeAllergy(Allergy allergy, String reason) throws APIException {
		voidAllergy(allergy, reason);
	}

	/**
	 * @see org.openmrs.api.PatientService#voidAllergy(org.openmrs.Allergy, java.lang.String)
	 */
	@Override
	public void voidAllergy(Allergy allergy, String reason) throws APIException {

		allergy.setVoided(true);
		allergy.setVoidedBy(Context.getAuthenticatedUser());
		allergy.setDateVoided(new Date());
		allergy.setVoidReason(reason);
		dao.saveAllergy(allergy);
	}

	/**
	 * @see PatientService#getCountOfPatients(String)
	 */
	@Override
	@Transactional(readOnly = true)
	public Integer getCountOfPatients(String query) {
		int count = 0;
		if (StringUtils.isBlank(query)) {
			return count;
		}

		return OpenmrsUtil.convertToInteger(dao.getCountOfPatients(query));
	}

	/**
	 * @see PatientService#getCountOfPatients(String, boolean)
	 */
	@Override
	@Transactional(readOnly = true)
	public Integer getCountOfPatients(String query, boolean includeVoided) {
		int count = 0;
		if (StringUtils.isBlank(query)) {
			return count;
		}

		return OpenmrsUtil.convertToInteger(dao.getCountOfPatients(query, includeVoided));
	}

	/**
	 * @see PatientService#getPatients(String, Integer, Integer)
	 */
	@Override
	@Transactional(readOnly = true)
	public List<Patient> getPatients(String query, Integer start, Integer length) throws APIException {
		var patients = new ArrayList<Patient>();
		if (StringUtils.isBlank(query)) {
			return patients;
		}

		return dao.getPatients(query, start, length);
	}

	/**
	 * @see PatientService#getPatients(String, boolean, Integer, Integer)
	 */
	@Override
	@Transactional(readOnly = true)
	public List<Patient> getPatients(String query, boolean includeVoided, Integer start, Integer length)
	        throws APIException {
		if (StringUtils.isBlank(query)) {
			return List.of();
		}

		return dao.getPatients(query, includeVoided, start, length);
	}

	/**
	 * @see PatientService#getPatients(String, String, List, boolean, Integer, Integer)
	 */
	// TODO - search for usage with non-empty list of patient identifier types - not used
	@Override
	@Transactional(readOnly = true)
	public List<Patient> getPatients(String name, String identifier, List<PatientIdentifierType> identifierTypes,
	        boolean matchIdentifierExactly, Integer start, Integer length) throws APIException {

		if (identifierTypes == null) {
			return dao.getPatients(name != null ? name : identifier, start, length);
		} else {
			return dao.getPatients(name != null ? name : identifier, identifierTypes, matchIdentifierExactly, start, length);
		}
	}

	/**
	 * @see PatientService#checkIfPatientIdentifierTypesAreLocked()
	 */
	@Override
	public void checkIfPatientIdentifierTypesAreLocked() {
		identifierDelegate.checkIfPatientIdentifierTypesAreLocked();
	}

	/**
	 * @see PatientService#getPatientIdentifiersByPatientProgram(org.openmrs.PatientProgram)
	 */
	public List<PatientIdentifier> getPatientIdentifiersByPatientProgram(PatientProgram patientProgram) {
		return dao.getPatientIdentifierByProgram(patientProgram);
	}

	@Override
	@SuppressWarnings("unchecked") // Type-checked dispatch: each branch casts after verifying Class<T> type
	public <T> T getRefByUuid(Class<T> type, String uuid) {
		if (PatientIdentifier.class.equals(type)) {
			return (T) getPatientIdentifierByUuid(uuid);
		}
		if (PatientIdentifierType.class.equals(type)) {
			return (T) getPatientIdentifierTypeByUuid(uuid);
		}
		if (Patient.class.equals(type)) {
			return (T) getPatientByUuid(uuid);
		}
		if (Allergy.class.equals(type)) {
			return (T) getAllergyByUuid(uuid);
		}
		throw new APIException("Unsupported type for getRefByUuid: " + type != null ? type.getName() : "null");
	}

	@Override
	public List<Class<?>> getRefTypes() {
		return Arrays.asList(PatientIdentifier.class, PatientIdentifierType.class, Patient.class, Allergy.class);
	}

	/**
	 * @see org.openmrs.api.PatientService#getPatients(PatientSearchCriteria)
	 */
	@Override
	@Transactional(readOnly = true)
	public List<Patient> getPatients(PatientSearchCriteria criteria) throws APIException {
		return this.getPatients(criteria.name(), criteria.identifier(), criteria.identifierTypes(),
		    criteria.matchIdentifierExactly(), criteria.start(), criteria.length());
	}

}
