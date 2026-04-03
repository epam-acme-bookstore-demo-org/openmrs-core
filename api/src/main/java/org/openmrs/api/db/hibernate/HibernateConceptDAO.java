/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.api.db.hibernate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import jakarta.persistence.Query;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.MutationQuery;
import org.hibernate.query.NativeQuery;
import org.openmrs.Concept;
import org.openmrs.ConceptAnswer;
import org.openmrs.ConceptAttribute;
import org.openmrs.ConceptAttributeType;
import org.openmrs.ConceptClass;
import org.openmrs.ConceptComplex;
import org.openmrs.ConceptDatatype;
import org.openmrs.ConceptDescription;
import org.openmrs.ConceptMap;
import org.openmrs.ConceptMapType;
import org.openmrs.ConceptName;
import org.openmrs.ConceptNameTag;
import org.openmrs.ConceptNumeric;
import org.openmrs.ConceptProposal;
import org.openmrs.ConceptReferenceRange;
import org.openmrs.ConceptReferenceTerm;
import org.openmrs.ConceptReferenceTermMap;
import org.openmrs.ConceptSearchResult;
import org.openmrs.ConceptSet;
import org.openmrs.ConceptSource;
import org.openmrs.ConceptStopWord;
import org.openmrs.Drug;
import org.openmrs.DrugIngredient;
import org.openmrs.api.APIException;
import org.openmrs.api.ConceptNameType;
import org.openmrs.api.ConceptService;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.ConceptDAO;
import org.openmrs.api.db.DAOException;
import org.openmrs.api.db.hibernate.search.session.SearchSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * The Hibernate class for Concepts, Drugs, and related classes. <br>
 * <br>
 * Use the {@link ConceptService} to access these methods
 * <p>
 * Delegates search operations to {@link HibernateConceptSearchDAO}, mapping operations to
 * {@link HibernateConceptMappingDAO}, and set/tag/proposal operations to
 * {@link HibernateConceptSetDAO}.
 *
 * @see ConceptService
 */
@Repository("conceptDAO")
public class HibernateConceptDAO implements ConceptDAO {

	private static final Logger log = LoggerFactory.getLogger(HibernateConceptDAO.class);

	private final SessionFactory sessionFactory;

	private final HibernateConceptSearchDAO conceptSearchDAO;

	private final HibernateConceptMappingDAO conceptMappingDAO;

	private final HibernateConceptSetDAO conceptSetDAO;

	@Autowired
	public HibernateConceptDAO(SessionFactory sessionFactory, SearchSessionFactory searchSessionFactory) {
		this.sessionFactory = sessionFactory;
		this.conceptSearchDAO = new HibernateConceptSearchDAO(sessionFactory, searchSessionFactory);
		this.conceptMappingDAO = new HibernateConceptMappingDAO(sessionFactory);
		this.conceptSetDAO = new HibernateConceptSetDAO(sessionFactory);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptComplex(java.lang.Integer)
	 */
	@Override
	public ConceptComplex getConceptComplex(Integer conceptId) {
		ConceptComplex cc;
		Session session = sessionFactory.getCurrentSession();
		Object obj = session.get(ConceptComplex.class, conceptId);
		// If Concept has already been read & cached, we may get back a Concept instead of
		// ConceptComplex.  If this happens, we need to clear the object from the cache
		// and re-fetch it as a ConceptComplex
		if (obj != null && !obj.getClass().equals(ConceptComplex.class)) {
			// remove from cache
			session.detach(obj);

			// session.get() did not work here, we need to perform a query to get a ConceptComplex
			CriteriaBuilder cb = session.getCriteriaBuilder();
			CriteriaQuery<ConceptComplex> cq = cb.createQuery(ConceptComplex.class);
			Root<ConceptComplex> root = cq.from(ConceptComplex.class);

			cq.where(cb.equal(root.get("conceptId"), conceptId));

			obj = session.createQuery(cq).uniqueResult();
		}
		cc = (ConceptComplex) obj;

		return cc;
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#saveConcept(org.openmrs.Concept)
	 */
	@Override
	public Concept saveConcept(Concept concept) throws DAOException {
		if ((concept.getConceptId() != null) && (concept.getConceptId() > 0)) {
			// this method checks the concept_numeric, concept_derived, etc tables
			// to see if a row exists there or not.  This is needed because hibernate
			// doesn't like to insert into concept_numeric but update concept in the
			// same go.  It assumes that its either in both tables or no tables
			insertRowIntoSubclassIfNecessary(concept);

			// After insertRowIntoSubclassIfNecessary, the session may have been
			// cleared. Use merge directly since the concept row already exists.
			Session session = sessionFactory.getCurrentSession();
			if (!session.contains(concept)) {
				return (Concept) session.merge(concept);
			}
			return concept;
		}

		return HibernateUtil.saveOrUpdate(sessionFactory.getCurrentSession(), concept);
	}

	/**
	 * Convenience method that will check this concept for subtype values (ConceptNumeric,
	 * ConceptDerived, etc) and insert a line into that subtable if needed. This prevents a hibernate
	 * ConstraintViolationException
	 *
	 * @param concept the concept that will be inserted
	 */
	private void insertRowIntoSubclassIfNecessary(Concept concept) {

		// check the concept_numeric table
		if (concept instanceof ConceptNumeric) {

			String select = "SELECT 1 from concept_numeric WHERE concept_id = :conceptId";
			NativeQuery<Integer> selectQuery = sessionFactory.getCurrentSession().createNativeQuery(select, Integer.class);
			selectQuery.setParameter("conceptId", concept.getConceptId());

			// Converting to concept numeric:  A single concept row exists, but concept numeric has not been populated yet.
			if (JpaUtils.getSingleResultOrNull(selectQuery) == null) {
				// we have to evict the current concept out of the session because
				// the user probably had to change the class of this object to get it
				// to now be a numeric
				// (must be done before the "insert into...")
				sessionFactory.getCurrentSession().clear();

				//Just in case this was changed from concept_complex to numeric
				//We need to add a delete line for each concept sub class that is not concept_numeric
				deleteSubclassConcept("concept_complex", concept.getConceptId());

				String insert = "INSERT INTO concept_numeric (concept_id, allow_decimal) VALUES (:conceptId, false)";
				MutationQuery insertQuery = sessionFactory.getCurrentSession().createNativeMutationQuery(insert);
				insertQuery.setParameter("conceptId", concept.getConceptId());
				insertQuery.executeUpdate();

			} else {
				// Converting from concept numeric:  The concept and concept numeric rows both exist, so we need to delete concept_numeric.

				// concept is changed from numeric to something else
				// hence row should be deleted from the concept_numeric
				if (!concept.isNumeric()) {
					deleteSubclassConcept("concept_numeric", concept.getConceptId());
				}
			}
		}
		// check the concept complex table
		else if (concept instanceof ConceptComplex) {

			String select = "SELECT 1 FROM concept_complex WHERE concept_id = :conceptId";
			NativeQuery<Integer> selectQuery = sessionFactory.getCurrentSession().createNativeQuery(select, Integer.class);
			selectQuery.setParameter("conceptId", concept.getConceptId());

			// Converting to concept complex:  A single concept row exists, but concept complex has not been populated yet.
			if (JpaUtils.getSingleResultOrNull(selectQuery) == null) {
				// we have to evict the current concept out of the session because
				// the user probably had to change the class of this object to get it
				// to now be a ConceptComplex
				// (must be done before the "insert into...")
				sessionFactory.getCurrentSession().clear();

				//Just in case this was changed from concept_numeric to complex
				//We need to add a delete line for each concept sub class that is not concept_complex
				deleteSubclassConcept("concept_numeric", concept.getConceptId());

				// Add an empty row into the concept_complex table
				String insert = "INSERT INTO concept_complex (concept_id) VALUES (:conceptId)";
				MutationQuery insertQuery = sessionFactory.getCurrentSession().createNativeQuery(insert);
				insertQuery.setParameter("conceptId", concept.getConceptId());
				insertQuery.executeUpdate();

			} else {
				// Converting from concept complex:  The concept and concept complex rows both exist, so we need to delete the concept_complex row.
				// no stub insert is needed because either a concept row doesn't exist OR a concept_complex row does exist

				// concept is changed from complex to something else
				// hence row should be deleted from the concept_complex
				if (!concept.isComplex()) {
					deleteSubclassConcept("concept_complex", concept.getConceptId());
				}
			}
		} else {
			// Plain Concept (not a subclass): clean up any subclass rows that may exist
			deleteSubclassConcept("concept_numeric", concept.getConceptId());
			deleteSubclassConcept("concept_complex", concept.getConceptId());
		}
	}

	/**
	 * Deletes a concept from a sub class table
	 *
	 * @param tableName the sub class table name
	 * @param conceptId the concept id
	 */
	private void deleteSubclassConcept(String tableName, Integer conceptId) {
		String delete = "DELETE FROM " + tableName + " WHERE concept_id = :conceptId";
		MutationQuery query = sessionFactory.getCurrentSession().createNativeMutationQuery(delete);
		query.setParameter("conceptId", conceptId);
		query.executeUpdate();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#purgeConcept(org.openmrs.Concept)
	 */
	@Override
	public void purgeConcept(Concept concept) throws DAOException {
		sessionFactory.getCurrentSession().remove(concept);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConcept(java.lang.Integer)
	 */
	@Override
	public Concept getConcept(Integer conceptId) throws DAOException {
		return sessionFactory.getCurrentSession().get(Concept.class, conceptId);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptName(java.lang.Integer)
	 */
	@Override
	public ConceptName getConceptName(Integer conceptNameId) throws DAOException {
		return sessionFactory.getCurrentSession().get(ConceptName.class, conceptNameId);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptAnswer(java.lang.Integer)
	 */
	@Override
	public ConceptAnswer getConceptAnswer(Integer conceptAnswerId) throws DAOException {
		return sessionFactory.getCurrentSession().get(ConceptAnswer.class, conceptAnswerId);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getAllConcepts(java.lang.String, boolean, boolean)
	 */
	@Override
	public List<Concept> getAllConcepts(String sortBy, boolean asc, boolean includeRetired) throws DAOException {

		boolean isNameField = false;

		try {
			Concept.class.getDeclaredField(sortBy);
		} catch (NoSuchFieldException e) {
			try {
				ConceptName.class.getDeclaredField(sortBy);
				isNameField = true;
			} catch (NoSuchFieldException e2) {
				sortBy = "conceptId";
			}
		}

		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<Concept> cq = cb.createQuery(Concept.class);
		Root<Concept> root = cq.from(Concept.class);

		var predicates = new ArrayList<Predicate>();

		// When sorting by a ConceptName field, join to names filtered on FULLY_SPECIFIED
		// to avoid duplicates (each concept should have exactly one fully specified name)
		Join<Concept, ConceptName> namesJoin = null;
		if (isNameField) {
			namesJoin = root.join("names", JoinType.LEFT);
			predicates.add(cb.equal(namesJoin.get("conceptNameType"), ConceptNameType.FULLY_SPECIFIED));
		}

		if (!includeRetired) {
			predicates.add(cb.isFalse(root.get("retired")));
		}

		cq.select(root).where(predicates.toArray(new Predicate[0]));

		if (isNameField) {
			cq.orderBy(asc ? cb.asc(namesJoin.get(sortBy)) : cb.desc(namesJoin.get(sortBy)));
		} else {
			cq.orderBy(asc ? cb.asc(root.get(sortBy)) : cb.desc(root.get(sortBy)));
		}

		return session.createQuery(cq).getResultList();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#saveDrug(org.openmrs.Drug)
	 */
	@Override
	public Drug saveDrug(Drug drug) throws DAOException {
		return HibernateUtil.saveOrUpdate(sessionFactory.getCurrentSession(), drug);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getDrug(java.lang.Integer)
	 */
	@Override
	public Drug getDrug(Integer drugId) throws DAOException {
		return sessionFactory.getCurrentSession().get(Drug.class, drugId);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getDrugs(java.lang.String, org.openmrs.Concept, boolean)
	 */
	@Override
	public List<Drug> getDrugs(String drugName, Concept concept, boolean includeRetired) throws DAOException {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<Drug> cq = cb.createQuery(Drug.class);
		Root<Drug> drugRoot = cq.from(Drug.class);

		var predicates = new ArrayList<Predicate>();

		if (!includeRetired) {
			predicates.add(cb.isFalse(drugRoot.get("retired")));
		}

		if (concept != null) {
			predicates.add(cb.equal(drugRoot.get("concept"), concept));
		}

		if (drugName != null) {
			if (Context.getAdministrationService().isDatabaseStringComparisonCaseSensitive()) {
				predicates.add(cb.equal(cb.lower(drugRoot.get("name")), MatchMode.EXACT.toLowerCasePattern(drugName)));
			} else {
				predicates.add(cb.equal(drugRoot.get("name"), MatchMode.EXACT.toCaseSensitivePattern(drugName)));
			}
		}

		cq.where(predicates.toArray(new Predicate[] {}));

		return session.createQuery(cq).getResultList();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getDrugsByIngredient(org.openmrs.Concept)
	 */
	@Override
	public List<Drug> getDrugsByIngredient(Concept ingredient) {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<Drug> cq = cb.createQuery(Drug.class);
		Root<Drug> drugRoot = cq.from(Drug.class);

		Join<Drug, DrugIngredient> ingredientJoin = drugRoot.join("ingredients");

		Predicate rhs = cb.equal(drugRoot.get("concept"), ingredient);
		Predicate lhs = cb.equal(ingredientJoin.get("ingredient"), ingredient);

		cq.where(cb.or(lhs, rhs));

		return session.createQuery(cq).getResultList();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getDrugs(java.lang.String)
	 */
	@Override
	public List<Drug> getDrugs(final String phrase) throws DAOException {
		return conceptSearchDAO.getDrugs(phrase);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptClass(java.lang.Integer)
	 */
	@Override
	public ConceptClass getConceptClass(Integer i) throws DAOException {
		return sessionFactory.getCurrentSession().get(ConceptClass.class, i);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptClasses(java.lang.String)
	 */
	@Override
	public List<ConceptClass> getConceptClasses(String name) throws DAOException {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<ConceptClass> cq = cb.createQuery(ConceptClass.class);
		Root<ConceptClass> root = cq.from(ConceptClass.class);

		if (name != null) {
			cq.where(cb.equal(root.get("name"), name));
		}

		return session.createQuery(cq).getResultList();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getAllConceptClasses(boolean)
	 */
	@Override
	public List<ConceptClass> getAllConceptClasses(boolean includeRetired) throws DAOException {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<ConceptClass> cq = cb.createQuery(ConceptClass.class);
		Root<ConceptClass> root = cq.from(ConceptClass.class);

		// Minor bug - was assigning includeRetired instead of evaluating
		if (!includeRetired) {
			cq.where(cb.isFalse(root.get("retired")));
		}

		return session.createQuery(cq).getResultList();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#saveConceptClass(org.openmrs.ConceptClass)
	 */
	@Override
	public ConceptClass saveConceptClass(ConceptClass cc) throws DAOException {
		return HibernateUtil.saveOrUpdate(sessionFactory.getCurrentSession(), cc);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#purgeConceptClass(org.openmrs.ConceptClass)
	 */
	@Override
	public void purgeConceptClass(ConceptClass cc) throws DAOException {
		sessionFactory.getCurrentSession().remove(cc);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#deleteConceptNameTag(ConceptNameTag)
	 */
	@Override
	public void deleteConceptNameTag(ConceptNameTag cnt) throws DAOException {
		conceptSetDAO.deleteConceptNameTag(cnt);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptDatatype(java.lang.Integer)
	 */
	@Override
	public ConceptDatatype getConceptDatatype(Integer i) {
		return sessionFactory.getCurrentSession().get(ConceptDatatype.class, i);
	}

	@Override
	public List<ConceptDatatype> getAllConceptDatatypes(boolean includeRetired) throws DAOException {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<ConceptDatatype> cq = cb.createQuery(ConceptDatatype.class);
		Root<ConceptDatatype> root = cq.from(ConceptDatatype.class);

		if (!includeRetired) {
			cq.where(cb.isFalse(root.get("retired")));
		}

		return session.createQuery(cq).getResultList();
	}

	/**
	 * @param name the name of the ConceptDatatype
	 * @return a List of ConceptDatatype whose names start with the passed name
	 */
	public List<ConceptDatatype> getConceptDatatypes(String name) throws DAOException {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<ConceptDatatype> cq = cb.createQuery(ConceptDatatype.class);
		Root<ConceptDatatype> root = cq.from(ConceptDatatype.class);

		if (name != null) {
			cq.where(cb.like(root.get("name"), MatchMode.START.toCaseSensitivePattern(name)));
		}

		return session.createQuery(cq).getResultList();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptDatatypeByName(String)
	 */
	@Override
	public ConceptDatatype getConceptDatatypeByName(String name) throws DAOException {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<ConceptDatatype> cq = cb.createQuery(ConceptDatatype.class);
		Root<ConceptDatatype> root = cq.from(ConceptDatatype.class);

		if (name != null) {
			cq.where(cb.equal(root.get("name"), name));
		}
		return session.createQuery(cq).uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#saveConceptDatatype(org.openmrs.ConceptDatatype)
	 */
	@Override
	public ConceptDatatype saveConceptDatatype(ConceptDatatype cd) throws DAOException {
		return HibernateUtil.saveOrUpdate(sessionFactory.getCurrentSession(), cd);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#purgeConceptDatatype(org.openmrs.ConceptDatatype)
	 */
	@Override
	public void purgeConceptDatatype(ConceptDatatype cd) throws DAOException {
		sessionFactory.getCurrentSession().remove(cd);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptNumeric(java.lang.Integer)
	 */
	@Override
	public ConceptNumeric getConceptNumeric(Integer i) {
		ConceptNumeric cn;
		Object obj = sessionFactory.getCurrentSession().get(ConceptNumeric.class, i);
		// If Concept has already been read & cached, we may get back a Concept instead of
		// ConceptNumeric.  If this happens, we need to clear the object from the cache
		// and re-fetch it as a ConceptNumeric
		if (obj != null && !obj.getClass().equals(ConceptNumeric.class)) {
			// remove from cache
			sessionFactory.getCurrentSession().evict(obj);
			// session.get() did not work here, we need to perform a query to get a ConceptNumeric
			Query query = sessionFactory.getCurrentSession().createQuery("from ConceptNumeric where conceptId = :conceptId")
			        .setParameter("conceptId", i);
			obj = JpaUtils.getSingleResultOrNull(query);
		}
		cn = (ConceptNumeric) obj;

		return cn;
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConcepts(java.lang.String, java.util.Locale, boolean,
	 *      java.util.List, java.util.List)
	 */
	@Override
	public List<Concept> getConcepts(final String name, final Locale loc, final boolean searchOnPhrase,
	        final List<ConceptClass> classes, final List<ConceptDatatype> datatypes) throws DAOException {
		return conceptSearchDAO.getConcepts(name, loc, searchOnPhrase, classes, datatypes);
	}

	/**
	 * gets questions for the given answer concept
	 *
	 * @see org.openmrs.api.db.ConceptDAO#getConceptsByAnswer(org.openmrs.Concept)
	 */
	@Override
	public List<Concept> getConceptsByAnswer(Concept concept) {
		return conceptSearchDAO.getConceptsByAnswer(concept);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getPrevConcept(org.openmrs.Concept)
	 */
	@Override
	public Concept getPrevConcept(Concept c) {
		return conceptSearchDAO.getPrevConcept(c);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getNextConcept(org.openmrs.Concept)
	 */
	@Override
	public Concept getNextConcept(Concept c) {
		return conceptSearchDAO.getNextConcept(c);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptsWithDrugsInFormulary()
	 */
	@Override
	public List<Concept> getConceptsWithDrugsInFormulary() {
		return conceptSearchDAO.getConceptsWithDrugsInFormulary();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#purgeDrug(org.openmrs.Drug)
	 */
	@Override
	public void purgeDrug(Drug drug) throws DAOException {
		sessionFactory.getCurrentSession().remove(drug);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#saveConceptProposal(org.openmrs.ConceptProposal)
	 */
	@Override
	public ConceptProposal saveConceptProposal(ConceptProposal cp) throws DAOException {
		return conceptSetDAO.saveConceptProposal(cp);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#purgeConceptProposal(org.openmrs.ConceptProposal)
	 */
	@Override
	public void purgeConceptProposal(ConceptProposal cp) throws DAOException {
		conceptSetDAO.purgeConceptProposal(cp);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getAllConceptProposals(boolean)
	 */
	@Override
	public List<ConceptProposal> getAllConceptProposals(boolean includeCompleted) throws DAOException {
		return conceptSetDAO.getAllConceptProposals(includeCompleted);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptProposal(java.lang.Integer)
	 */
	@Override
	public ConceptProposal getConceptProposal(Integer conceptProposalId) throws DAOException {
		return conceptSetDAO.getConceptProposal(conceptProposalId);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptProposals(java.lang.String)
	 */
	@Override
	public List<ConceptProposal> getConceptProposals(String text) throws DAOException {
		return conceptSetDAO.getConceptProposals(text);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getProposedConcepts(java.lang.String)
	 */
	@Override
	public List<Concept> getProposedConcepts(String text) throws DAOException {
		return conceptSetDAO.getProposedConcepts(text);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptSetsByConcept(org.openmrs.Concept)
	 */
	@Override
	public List<ConceptSet> getConceptSetsByConcept(Concept concept) {
		return conceptSetDAO.getConceptSetsByConcept(concept);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getSetsContainingConcept(org.openmrs.Concept)
	 */
	@Override
	public List<ConceptSet> getSetsContainingConcept(Concept concept) {
		return conceptSetDAO.getSetsContainingConcept(concept);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getLocalesOfConceptNames()
	 */
	@Override
	public Set<Locale> getLocalesOfConceptNames() {
		return conceptSetDAO.getLocalesOfConceptNames();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptNameTag(java.lang.Integer)
	 */
	@Override
	public ConceptNameTag getConceptNameTag(Integer i) {
		return conceptSetDAO.getConceptNameTag(i);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptNameTagByName(java.lang.String)
	 */
	@Override
	public ConceptNameTag getConceptNameTagByName(String name) {
		return conceptSetDAO.getConceptNameTagByName(name);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getAllConceptNameTags()
	 */
	@Override
	public List<ConceptNameTag> getAllConceptNameTags() {
		return conceptSetDAO.getAllConceptNameTags();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptSource(java.lang.Integer)
	 */
	@Override
	public ConceptSource getConceptSource(Integer conceptSourceId) {
		return sessionFactory.getCurrentSession().get(ConceptSource.class, conceptSourceId);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getAllConceptSources(boolean)
	 */
	@Override
	public List<ConceptSource> getAllConceptSources(boolean includeRetired) throws DAOException {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<ConceptSource> cq = cb.createQuery(ConceptSource.class);
		Root<ConceptSource> root = cq.from(ConceptSource.class);

		if (!includeRetired) {
			cq.where(cb.isFalse(root.get("retired")));
		}

		return session.createQuery(cq).getResultList();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#deleteConceptSource(org.openmrs.ConceptSource)
	 */
	@Override
	public ConceptSource deleteConceptSource(ConceptSource cs) throws DAOException {
		sessionFactory.getCurrentSession().remove(cs);
		return cs;
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#saveConceptSource(org.openmrs.ConceptSource)
	 */
	@Override
	public ConceptSource saveConceptSource(ConceptSource conceptSource) throws DAOException {
		return HibernateUtil.saveOrUpdate(sessionFactory.getCurrentSession(), conceptSource);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#saveConceptNameTag(org.openmrs.ConceptNameTag)
	 */
	@Override
	public ConceptNameTag saveConceptNameTag(ConceptNameTag nameTag) {
		return conceptSetDAO.saveConceptNameTag(nameTag);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getMaxConceptId()
	 */
	public Integer getMinConceptId() {
		Query query = sessionFactory.getCurrentSession().createQuery("select min(conceptId) from Concept");
		return JpaUtils.getSingleResultOrNull(query);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getMaxConceptId()
	 */
	@Override
	public Integer getMaxConceptId() {
		Query query = sessionFactory.getCurrentSession().createQuery("select max(conceptId) from Concept");
		return JpaUtils.getSingleResultOrNull(query);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#conceptIterator()
	 */
	@Override
	public Iterator<Concept> conceptIterator() {
		return new ConceptIterator();
	}

	/**
	 * An iterator that loops over all concepts in the dictionary one at a time
	 */
	private class ConceptIterator implements Iterator<Concept> {

		Concept currentConcept = null;

		Concept nextConcept;

		public ConceptIterator() {
			final int firstConceptId = getMinConceptId();
			nextConcept = getConcept(firstConceptId);
		}

		/**
		 * @see java.util.Iterator#hasNext()
		 */
		@Override
		public boolean hasNext() {
			return nextConcept != null;
		}

		/**
		 * @see java.util.Iterator#next()
		 */
		@Override
		public Concept next() {
			if (currentConcept != null) {
				sessionFactory.getCurrentSession().evict(currentConcept);
			}

			currentConcept = nextConcept;
			nextConcept = getNextConcept(currentConcept);

			return currentConcept;
		}

		/**
		 * @see java.util.Iterator#remove()
		 */
		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}

	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptsByMapping(String, String, boolean)
	 */
	@Override
	@Deprecated
	public List<Concept> getConceptsByMapping(String code, String sourceName, boolean includeRetired) {
		return conceptMappingDAO.getConceptsByMapping(code, sourceName, includeRetired);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptIdsByMapping(String, String, boolean)
	 */
	@Override
	public List<Integer> getConceptIdsByMapping(String code, String sourceName, boolean includeRetired) {
		return conceptMappingDAO.getConceptIdsByMapping(code, sourceName, includeRetired);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptByUuid(java.lang.String)
	 */
	@Override
	public Concept getConceptByUuid(String uuid) {
		return HibernateUtil.getUniqueEntityByUUID(sessionFactory, Concept.class, uuid);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptClassByUuid(java.lang.String)
	 */
	@Override
	public ConceptClass getConceptClassByUuid(String uuid) {
		return HibernateUtil.getUniqueEntityByUUID(sessionFactory, ConceptClass.class, uuid);
	}

	@Override
	public ConceptAnswer getConceptAnswerByUuid(String uuid) {
		return HibernateUtil.getUniqueEntityByUUID(sessionFactory, ConceptAnswer.class, uuid);
	}

	@Override
	public ConceptName getConceptNameByUuid(String uuid) {
		return HibernateUtil.getUniqueEntityByUUID(sessionFactory, ConceptName.class, uuid);
	}

	@Override
	public ConceptSet getConceptSetByUuid(String uuid) {
		return HibernateUtil.getUniqueEntityByUUID(sessionFactory, ConceptSet.class, uuid);
	}

	@Override
	public ConceptSource getConceptSourceByUuid(String uuid) {
		return HibernateUtil.getUniqueEntityByUUID(sessionFactory, ConceptSource.class, uuid);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptDatatypeByUuid(java.lang.String)
	 */
	@Override
	public ConceptDatatype getConceptDatatypeByUuid(String uuid) {
		return HibernateUtil.getUniqueEntityByUUID(sessionFactory, ConceptDatatype.class, uuid);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptNumericByUuid(java.lang.String)
	 */
	@Override
	public ConceptNumeric getConceptNumericByUuid(String uuid) {
		return HibernateUtil.getUniqueEntityByUUID(sessionFactory, ConceptNumeric.class, uuid);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptProposalByUuid(java.lang.String)
	 */
	@Override
	public ConceptProposal getConceptProposalByUuid(String uuid) {
		return HibernateUtil.getUniqueEntityByUUID(sessionFactory, ConceptProposal.class, uuid);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getDrugByUuid(java.lang.String)
	 */
	@Override
	public Drug getDrugByUuid(String uuid) {
		return HibernateUtil.getUniqueEntityByUUID(sessionFactory, Drug.class, uuid);
	}

	@Override
	public DrugIngredient getDrugIngredientByUuid(String uuid) {
		return HibernateUtil.getUniqueEntityByUUID(sessionFactory, DrugIngredient.class, uuid);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptUuids()
	 */
	@Override
	@SuppressWarnings("unchecked") // Hibernate HQL query returns raw List
	public Map<Integer, String> getConceptUuids() {
		var ret = new HashMap<Integer, String>();
		Query q = sessionFactory.getCurrentSession().createQuery("select conceptId, uuid from Concept");
		List<Object[]> list = q.getResultList();
		for (Object[] o : list) {
			ret.put((Integer) o[0], (String) o[1]);
		}
		return ret;
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptDescriptionByUuid(java.lang.String)
	 */
	@Override
	public ConceptDescription getConceptDescriptionByUuid(String uuid) {
		return HibernateUtil.getUniqueEntityByUUID(sessionFactory, ConceptDescription.class, uuid);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptNameTagByUuid(java.lang.String)
	 */
	@Override
	public ConceptNameTag getConceptNameTagByUuid(String uuid) {
		return HibernateUtil.getUniqueEntityByUUID(sessionFactory, ConceptNameTag.class, uuid);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptMapsBySource(ConceptSource)
	 */
	@Override
	public List<ConceptMap> getConceptMapsBySource(ConceptSource conceptSource) throws DAOException {
		return conceptMappingDAO.getConceptMapsBySource(conceptSource);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptSourceByName(java.lang.String)
	 */
	@Override
	public ConceptSource getConceptSourceByName(String conceptSourceName) throws DAOException {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<ConceptSource> cq = cb.createQuery(ConceptSource.class);
		Root<ConceptSource> root = cq.from(ConceptSource.class);

		cq.where(cb.equal(root.get("name"), conceptSourceName));

		return session.createQuery(cq).uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptSourceByUniqueId(java.lang.String)
	 */
	@Override
	public ConceptSource getConceptSourceByUniqueId(String uniqueId) {
		if (StringUtils.isBlank(uniqueId)) {
			return null;
		}

		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<ConceptSource> cq = cb.createQuery(ConceptSource.class);
		Root<ConceptSource> root = cq.from(ConceptSource.class);

		cq.where(cb.equal(root.get("uniqueId"), uniqueId));

		return session.createQuery(cq).uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptSourceByHL7Code(java.lang.String)
	 */
	@Override
	public ConceptSource getConceptSourceByHL7Code(String hl7Code) {
		if (StringUtils.isBlank(hl7Code)) {
			return null;
		}

		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<ConceptSource> cq = cb.createQuery(ConceptSource.class);
		Root<ConceptSource> root = cq.from(ConceptSource.class);

		cq.where(cb.equal(root.get("hl7Code"), hl7Code));

		return session.createQuery(cq).uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getSavedConceptDatatype(org.openmrs.Concept)
	 */
	@Override
	public ConceptDatatype getSavedConceptDatatype(Concept concept) {
		Query sql = sessionFactory.getCurrentSession().createNativeQuery(
		    "select datatype.* from concept_datatype datatype, concept concept where "
		            + "datatype.concept_datatype_id = concept.datatype_id and concept.concept_id=:conceptId",
		    ConceptDatatype.class);
		sql.setParameter("conceptId", concept.getConceptId());

		return JpaUtils.getSingleResultOrNull(sql);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getSavedConceptName(org.openmrs.ConceptName)
	 */
	@Override
	public ConceptName getSavedConceptName(ConceptName conceptName) {
		sessionFactory.getCurrentSession().refresh(conceptName);
		return conceptName;
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptStopWords(java.util.Locale)
	 */
	@Override
	public List<String> getConceptStopWords(Locale locale) throws DAOException {
		return conceptSetDAO.getConceptStopWords(locale);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#saveConceptStopWord(org.openmrs.ConceptStopWord)
	 */
	@Override
	public ConceptStopWord saveConceptStopWord(ConceptStopWord conceptStopWord) throws DAOException {
		return conceptSetDAO.saveConceptStopWord(conceptStopWord);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#deleteConceptStopWord(java.lang.Integer)
	 */
	@Override
	public void deleteConceptStopWord(Integer conceptStopWordId) throws DAOException {
		conceptSetDAO.deleteConceptStopWord(conceptStopWordId);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getAllConceptStopWords()
	 */
	@Override
	public List<ConceptStopWord> getAllConceptStopWords() {
		return conceptSetDAO.getAllConceptStopWords();
	}

	/**
	 * @see ConceptService#getCountOfDrugs(String, Concept, boolean, boolean, boolean)
	 */
	@Override
	public Long getCountOfDrugs(String drugName, Concept concept, boolean searchKeywords, boolean searchDrugConceptNames,
	        boolean includeRetired) throws DAOException {
		return conceptSearchDAO.getCountOfDrugs(drugName, concept, searchKeywords, searchDrugConceptNames, includeRetired);
	}

	/**
	 * drug_name drug_name
	 * <p>
	 * <strong>Should</strong> return a drug if either the drug name or concept name matches the phase
	 * not both<br/>
	 * <strong>Should</strong> return distinct drugs<br/>
	 * <strong>Should</strong> return a drug, if phrase match concept_name No need to match both
	 * concept_name and<br/>
	 * <strong>Should</strong> return drug when phrase match drug_name even searchDrugConceptNames is
	 * false<br/>
	 * <strong>Should</strong> return a drug if phrase match drug_name No need to match both
	 * concept_name and
	 */
	@Override
	public List<Drug> getDrugs(String drugName, Concept concept, boolean searchKeywords, boolean searchDrugConceptNames,
	        boolean includeRetired, Integer start, Integer length) throws DAOException {
		return conceptSearchDAO.getDrugs(drugName, concept, searchKeywords, searchDrugConceptNames, includeRetired, start,
		    length);
	}

	/**
	 * @see ConceptDAO#getConcepts(String, List, boolean, List, List, List, List, Concept, Integer,
	 *      Integer)
	 */
	@Override
	public List<ConceptSearchResult> getConcepts(final String phrase, final List<Locale> locales,
	        final boolean includeRetired, final List<ConceptClass> requireClasses, final List<ConceptClass> excludeClasses,
	        final List<ConceptDatatype> requireDatatypes, final List<ConceptDatatype> excludeDatatypes,
	        final Concept answersToConcept, final Integer start, final Integer size) throws DAOException {
		return conceptSearchDAO.getConcepts(phrase, locales, includeRetired, requireClasses, excludeClasses,
		    requireDatatypes, excludeDatatypes, answersToConcept, start, size);
	}

	@Override
	public Integer getCountOfConcepts(final String phrase, List<Locale> locales, boolean includeRetired,
	        List<ConceptClass> requireClasses, List<ConceptClass> excludeClasses, List<ConceptDatatype> requireDatatypes,
	        List<ConceptDatatype> excludeDatatypes, Concept answersToConcept) throws DAOException {
		return conceptSearchDAO.getCountOfConcepts(phrase, locales, includeRetired, requireClasses, excludeClasses,
		    requireDatatypes, excludeDatatypes, answersToConcept);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptMapTypes(boolean, boolean)
	 */
	@Override
	public List<ConceptMapType> getConceptMapTypes(boolean includeRetired, boolean includeHidden) throws DAOException {
		return conceptMappingDAO.getConceptMapTypes(includeRetired, includeHidden);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptMapType(java.lang.Integer)
	 */
	@Override
	public ConceptMapType getConceptMapType(Integer conceptMapTypeId) throws DAOException {
		return conceptMappingDAO.getConceptMapType(conceptMapTypeId);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptMapTypeByUuid(java.lang.String)
	 */
	@Override
	public ConceptMapType getConceptMapTypeByUuid(String uuid) throws DAOException {
		return conceptMappingDAO.getConceptMapTypeByUuid(uuid);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptMapTypeByName(java.lang.String)
	 */
	@Override
	public ConceptMapType getConceptMapTypeByName(String name) throws DAOException {
		return conceptMappingDAO.getConceptMapTypeByName(name);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#saveConceptMapType(org.openmrs.ConceptMapType)
	 */
	@Override
	public ConceptMapType saveConceptMapType(ConceptMapType conceptMapType) throws DAOException {
		return conceptMappingDAO.saveConceptMapType(conceptMapType);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#deleteConceptMapType(org.openmrs.ConceptMapType)
	 */
	@Override
	public void deleteConceptMapType(ConceptMapType conceptMapType) throws DAOException {
		conceptMappingDAO.deleteConceptMapType(conceptMapType);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptReferenceTerms(boolean)
	 */
	@Override
	public List<ConceptReferenceTerm> getConceptReferenceTerms(boolean includeRetired) throws DAOException {
		return conceptMappingDAO.getConceptReferenceTerms(includeRetired);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptReferenceTerm(java.lang.Integer)
	 */
	@Override
	public ConceptReferenceTerm getConceptReferenceTerm(Integer conceptReferenceTermId) throws DAOException {
		return conceptMappingDAO.getConceptReferenceTerm(conceptReferenceTermId);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptReferenceTermByUuid(java.lang.String)
	 */
	@Override
	public ConceptReferenceTerm getConceptReferenceTermByUuid(String uuid) throws DAOException {
		return conceptMappingDAO.getConceptReferenceTermByUuid(uuid);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptReferenceTermsBySource(ConceptSource)
	 */
	@Override
	public List<ConceptReferenceTerm> getConceptReferenceTermsBySource(ConceptSource conceptSource) throws DAOException {
		return conceptMappingDAO.getConceptReferenceTermsBySource(conceptSource);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptReferenceTermByName(java.lang.String,
	 *      org.openmrs.ConceptSource)
	 */
	@Override
	public ConceptReferenceTerm getConceptReferenceTermByName(String name, ConceptSource conceptSource) throws DAOException {
		return conceptMappingDAO.getConceptReferenceTermByName(name, conceptSource);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptReferenceTermByCode(java.lang.String,
	 *      org.openmrs.ConceptSource)
	 */
	@Override
	public ConceptReferenceTerm getConceptReferenceTermByCode(String code, ConceptSource conceptSource) throws DAOException {
		return conceptMappingDAO.getConceptReferenceTermByCode(code, conceptSource);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptReferenceTermByCode(java.lang.String,
	 *      org.openmrs.ConceptSource, boolean)
	 */
	@Override
	public List<ConceptReferenceTerm> getConceptReferenceTermByCode(String code, ConceptSource conceptSource,
	        boolean includeRetired) throws DAOException {
		return conceptMappingDAO.getConceptReferenceTermByCode(code, conceptSource, includeRetired);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#saveConceptReferenceTerm(org.openmrs.ConceptReferenceTerm)
	 */
	@Override
	public ConceptReferenceTerm saveConceptReferenceTerm(ConceptReferenceTerm conceptReferenceTerm) throws DAOException {
		return conceptMappingDAO.saveConceptReferenceTerm(conceptReferenceTerm);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#deleteConceptReferenceTerm(org.openmrs.ConceptReferenceTerm)
	 */
	@Override
	public void deleteConceptReferenceTerm(ConceptReferenceTerm conceptReferenceTerm) throws DAOException {
		conceptMappingDAO.deleteConceptReferenceTerm(conceptReferenceTerm);
	}

	@Override
	public Long getCountOfConceptReferenceTerms(String query, ConceptSource conceptSource, boolean includeRetired)
	        throws DAOException {
		return conceptMappingDAO.getCountOfConceptReferenceTerms(query, conceptSource, includeRetired);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptReferenceTerms(String, ConceptSource, Integer,
	 *      Integer, boolean)
	 */
	@Override
	public List<ConceptReferenceTerm> getConceptReferenceTerms(String query, ConceptSource conceptSource, Integer start,
	        Integer length, boolean includeRetired) throws APIException {
		return conceptMappingDAO.getConceptReferenceTerms(query, conceptSource, start, length, includeRetired);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getReferenceTermMappingsTo(ConceptReferenceTerm)
	 */
	@Override
	public List<ConceptReferenceTermMap> getReferenceTermMappingsTo(ConceptReferenceTerm term) throws DAOException {
		return conceptMappingDAO.getReferenceTermMappingsTo(term);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#isConceptReferenceTermInUse(org.openmrs.ConceptReferenceTerm)
	 */
	@Override
	public boolean isConceptReferenceTermInUse(ConceptReferenceTerm term) throws DAOException {
		return conceptMappingDAO.isConceptReferenceTermInUse(term);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#isConceptMapTypeInUse(org.openmrs.ConceptMapType)
	 */
	@Override
	public boolean isConceptMapTypeInUse(ConceptMapType mapType) throws DAOException {
		return conceptMappingDAO.isConceptMapTypeInUse(mapType);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptsByName(java.lang.String, java.util.Locale,
	 *      java.lang.Boolean)
	 */
	@Override
	public List<Concept> getConceptsByName(final String name, final Locale locale, final Boolean exactLocale) {
		return conceptSearchDAO.getConceptsByName(name, locale, exactLocale);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptByName(java.lang.String)
	 */
	@Override
	public Concept getConceptByName(final String name) {
		return conceptSearchDAO.getConceptByName(name);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getDefaultConceptMapType()
	 */
	@Override
	public ConceptMapType getDefaultConceptMapType() throws DAOException {
		return conceptMappingDAO.getDefaultConceptMapType();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#isConceptNameDuplicate(org.openmrs.ConceptName)
	 */
	@Override
	public boolean isConceptNameDuplicate(ConceptName name) {
		return conceptSearchDAO.isConceptNameDuplicate(name);
	}

	/**
	 * @see ConceptDAO#getDrugs(String, java.util.Locale, boolean, boolean)
	 */
	@Override
	public List<Drug> getDrugs(String searchPhrase, Locale locale, boolean exactLocale, boolean includeRetired) {
		return conceptSearchDAO.getDrugs(searchPhrase, locale, exactLocale, includeRetired);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getDrugsByMapping(String, ConceptSource, Collection, boolean)
	 */
	@Override
	public List<Drug> getDrugsByMapping(String code, ConceptSource conceptSource,
	        Collection<ConceptMapType> withAnyOfTheseTypes, boolean includeRetired) throws DAOException {
		return conceptMappingDAO.getDrugsByMapping(code, conceptSource, withAnyOfTheseTypes, includeRetired);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getDrugs
	 */
	@Override
	public Drug getDrugByMapping(String code, ConceptSource conceptSource,
	        Collection<ConceptMapType> withAnyOfTheseTypesOrOrderOfPreference) throws DAOException {
		return conceptMappingDAO.getDrugByMapping(code, conceptSource, withAnyOfTheseTypesOrOrderOfPreference);
	}

	/**
	 * @see ConceptDAO#getAllConceptAttributeTypes()
	 */
	@Override
	public List<ConceptAttributeType> getAllConceptAttributeTypes() {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<ConceptAttributeType> cq = cb.createQuery(ConceptAttributeType.class);
		cq.from(ConceptAttributeType.class);

		return session.createQuery(cq).getResultList();
	}

	/**
	 * @see ConceptDAO#saveConceptAttributeType(ConceptAttributeType)
	 */
	@Override
	public ConceptAttributeType saveConceptAttributeType(ConceptAttributeType conceptAttributeType) {
		return HibernateUtil.saveOrUpdate(sessionFactory.getCurrentSession(), conceptAttributeType);
	}

	/**
	 * @see ConceptDAO#getConceptAttributeType(Integer)
	 */
	@Override
	public ConceptAttributeType getConceptAttributeType(Integer id) {
		return sessionFactory.getCurrentSession().get(ConceptAttributeType.class, id);
	}

	/**
	 * @see ConceptDAO#getConceptAttributeTypeByUuid(String)
	 */
	@Override
	public ConceptAttributeType getConceptAttributeTypeByUuid(String uuid) {
		return HibernateUtil.getUniqueEntityByUUID(sessionFactory, ConceptAttributeType.class, uuid);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#deleteConceptAttributeType(org.openmrs.ConceptAttributeType)
	 */
	@Override
	public void deleteConceptAttributeType(ConceptAttributeType conceptAttributeType) {
		sessionFactory.getCurrentSession().remove(conceptAttributeType);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptAttributeTypes(String)
	 */
	@Override
	public List<ConceptAttributeType> getConceptAttributeTypes(String name) {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<ConceptAttributeType> cq = cb.createQuery(ConceptAttributeType.class);
		Root<ConceptAttributeType> root = cq.from(ConceptAttributeType.class);

		//match name anywhere and case insensitive
		if (name != null) {
			cq.where(cb.like(cb.lower(root.get("name")), MatchMode.ANYWHERE.toLowerCasePattern(name)));
		}

		return session.createQuery(cq).getResultList();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptAttributeTypeByName(String)
	 */
	@Override
	public ConceptAttributeType getConceptAttributeTypeByName(String exactName) {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<ConceptAttributeType> cq = cb.createQuery(ConceptAttributeType.class);
		Root<ConceptAttributeType> root = cq.from(ConceptAttributeType.class);

		cq.where(cb.equal(root.get("name"), exactName));

		return session.createQuery(cq).uniqueResult();
	}

	/**
	 * @see ConceptDAO#getConceptAttributeByUuid(String)
	 */
	@Override
	public ConceptAttribute getConceptAttributeByUuid(String uuid) {
		return HibernateUtil.getUniqueEntityByUUID(sessionFactory, ConceptAttribute.class, uuid);
	}

	/**
	 * @see ConceptDAO#getConceptAttributeCount(ConceptAttributeType)
	 */
	@Override
	public long getConceptAttributeCount(ConceptAttributeType conceptAttributeType) {
		if (conceptAttributeType == null) {
			return 0;
		}
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<Long> cq = cb.createQuery(Long.class);
		Root<ConceptAttribute> root = cq.from(ConceptAttribute.class);

		cq.select(cb.count(root)).where(cb.equal(root.get("attributeType"), conceptAttributeType));

		return session.createQuery(cq).getSingleResult();
	}

	@Override
	public List<Concept> getConceptsByClass(ConceptClass conceptClass) {
		return conceptSearchDAO.getConceptsByClass(conceptClass);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#saveConceptReferenceRange(ConceptReferenceRange)
	 */
	@Override
	public ConceptReferenceRange saveConceptReferenceRange(ConceptReferenceRange conceptReferenceRange) {
		return HibernateUtil.saveOrUpdate(sessionFactory.getCurrentSession(), conceptReferenceRange);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptReferenceRangesByConceptId(Integer)
	 */
	@Override
	public List<ConceptReferenceRange> getConceptReferenceRangesByConceptId(Integer conceptId) {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<ConceptReferenceRange> cq = cb.createQuery(ConceptReferenceRange.class);
		Root<ConceptReferenceRange> root = cq.from(ConceptReferenceRange.class);

		cq.where(cb.equal(root.get("conceptNumeric").get("conceptId"), conceptId));

		return session.createQuery(cq).getResultList();
	}

	/**
	 * @see ConceptDAO#getConceptReferenceRangeByUuid(String)
	 */
	@Override
	public ConceptReferenceRange getConceptReferenceRangeByUuid(String uuid) {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<ConceptReferenceRange> cq = cb.createQuery(ConceptReferenceRange.class);
		Root<ConceptReferenceRange> root = cq.from(ConceptReferenceRange.class);

		cq.where(cb.equal(root.get("uuid"), uuid));

		return session.createQuery(cq).uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#purgeConceptReferenceRange(org.openmrs.ConceptReferenceRange)
	 */
	@Override
	public void purgeConceptReferenceRange(ConceptReferenceRange conceptReferenceRange) {
		sessionFactory.getCurrentSession().remove(conceptReferenceRange);
	}
}
