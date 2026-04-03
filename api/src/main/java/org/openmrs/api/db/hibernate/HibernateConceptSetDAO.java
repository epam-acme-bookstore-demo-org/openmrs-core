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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import jakarta.persistence.Query;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.openmrs.Concept;
import org.openmrs.ConceptNameTag;
import org.openmrs.ConceptProposal;
import org.openmrs.ConceptSet;
import org.openmrs.ConceptStopWord;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.DAOException;
import org.openmrs.util.OpenmrsConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Delegate class for concept set, name tag, stop word, and proposal operations. Extracted from
 * {@link HibernateConceptDAO} to reduce class size.
 * <p>
 * This class is an internal implementation detail and should not be used directly.
 */
class HibernateConceptSetDAO {

	private static final Logger log = LoggerFactory.getLogger(HibernateConceptSetDAO.class);

	private final SessionFactory sessionFactory;

	HibernateConceptSetDAO(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	void deleteConceptNameTag(ConceptNameTag cnt) throws DAOException {
		sessionFactory.getCurrentSession().remove(cnt);
	}

	ConceptProposal saveConceptProposal(ConceptProposal cp) throws DAOException {
		return HibernateUtil.saveOrUpdate(sessionFactory.getCurrentSession(), cp);
	}

	void purgeConceptProposal(ConceptProposal cp) throws DAOException {
		sessionFactory.getCurrentSession().remove(cp);
	}

	List<ConceptProposal> getAllConceptProposals(boolean includeCompleted) throws DAOException {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<ConceptProposal> cq = cb.createQuery(ConceptProposal.class);
		Root<ConceptProposal> root = cq.from(ConceptProposal.class);

		if (!includeCompleted) {
			cq.where(cb.equal(root.get("state"), OpenmrsConstants.CONCEPT_PROPOSAL_UNMAPPED));
		}
		cq.orderBy(cb.asc(root.get("originalText")));
		return session.createQuery(cq).getResultList();
	}

	ConceptProposal getConceptProposal(Integer conceptProposalId) throws DAOException {
		return sessionFactory.getCurrentSession().get(ConceptProposal.class, conceptProposalId);
	}

	List<ConceptProposal> getConceptProposals(String text) throws DAOException {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<ConceptProposal> cq = cb.createQuery(ConceptProposal.class);
		Root<ConceptProposal> root = cq.from(ConceptProposal.class);

		Predicate stateCondition = cb.equal(root.get("state"), OpenmrsConstants.CONCEPT_PROPOSAL_UNMAPPED);
		Predicate textCondition = cb.equal(root.get("originalText"), text);

		cq.where(cb.and(stateCondition, textCondition));

		return session.createQuery(cq).getResultList();
	}

	List<Concept> getProposedConcepts(String text) throws DAOException {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<Concept> cq = cb.createQuery(Concept.class);
		Root<ConceptProposal> root = cq.from(ConceptProposal.class);

		Predicate stateNotEqual = cb.notEqual(root.get("state"), OpenmrsConstants.CONCEPT_PROPOSAL_UNMAPPED);
		Predicate originalTextEqual = cb.equal(root.get("originalText"), text);
		Predicate mappedConceptNotNull = cb.isNotNull(root.get("mappedConcept"));

		cq.select(root.get("mappedConcept")).distinct(true);
		cq.where(stateNotEqual, originalTextEqual, mappedConceptNotNull);

		return session.createQuery(cq).getResultList();
	}

	List<ConceptSet> getConceptSetsByConcept(Concept concept) {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<ConceptSet> cq = cb.createQuery(ConceptSet.class);
		Root<ConceptSet> root = cq.from(ConceptSet.class);

		cq.where(cb.equal(root.get("conceptSet"), concept));
		cq.orderBy(cb.asc(root.get("sortWeight")));

		return session.createQuery(cq).getResultList();
	}

	List<ConceptSet> getSetsContainingConcept(Concept concept) {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<ConceptSet> cq = cb.createQuery(ConceptSet.class);
		Root<ConceptSet> root = cq.from(ConceptSet.class);

		cq.where(cb.equal(root.get("concept"), concept));

		return session.createQuery(cq).getResultList();
	}

	/**
	 * returns a list of n-generations of parents of a concept in a concept set
	 *
	 * @param current
	 * @return List&lt;Concept&gt;
	 * @throws DAOException
	 */
	@SuppressWarnings("unchecked") // Hibernate HQL query returns raw List
	private List<Concept> getParents(Concept current) throws DAOException {
		var parents = new ArrayList<Concept>();
		if (current != null) {
			Query query = sessionFactory.getCurrentSession()
			        .createQuery("from Concept c join c.conceptSets sets where sets.concept = ?").setParameter(0, current);
			List<Concept> immedParents = query.getResultList();
			for (Concept c : immedParents) {
				parents.addAll(getParents(c));
			}
			parents.add(current);
			if (log.isDebugEnabled()) {
				log.debug("parents found: ");
				for (Concept c : parents) {
					log.debug("id: {}", c.getConceptId());
				}
			}
		}
		return parents;
	}

	Set<Locale> getLocalesOfConceptNames() {
		var locales = new HashSet<Locale>();

		Query query = sessionFactory.getCurrentSession().createQuery("select distinct locale from ConceptName");

		for (Object locale : query.getResultList()) {
			locales.add((Locale) locale);
		}

		return locales;
	}

	ConceptNameTag getConceptNameTag(Integer i) {
		return sessionFactory.getCurrentSession().get(ConceptNameTag.class, i);
	}

	ConceptNameTag getConceptNameTagByName(String name) {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<ConceptNameTag> cq = cb.createQuery(ConceptNameTag.class);
		Root<ConceptNameTag> root = cq.from(ConceptNameTag.class);

		cq.where(cb.equal(root.get("tag"), name));

		List<ConceptNameTag> conceptNameTags = session.createQuery(cq).getResultList();
		if (conceptNameTags.isEmpty()) {
			return null;
		}

		return conceptNameTags.getFirst();
	}

	@SuppressWarnings("unchecked") // Hibernate HQL query returns raw List
	List<ConceptNameTag> getAllConceptNameTags() {
		return sessionFactory.getCurrentSession().createQuery("from ConceptNameTag cnt order by cnt.tag").list();
	}

	ConceptNameTag saveConceptNameTag(ConceptNameTag nameTag) {
		if (nameTag == null) {
			return null;
		}

		return HibernateUtil.saveOrUpdate(sessionFactory.getCurrentSession(), nameTag);
	}

	List<String> getConceptStopWords(Locale locale) throws DAOException {

		locale = (locale == null ? Context.getLocale() : locale);

		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<String> cq = cb.createQuery(String.class);
		Root<ConceptStopWord> root = cq.from(ConceptStopWord.class);

		cq.select(root.get("value"));
		cq.where(cb.equal(root.get("locale"), locale));

		return session.createQuery(cq).getResultList();
	}

	ConceptStopWord saveConceptStopWord(ConceptStopWord conceptStopWord) throws DAOException {
		if (conceptStopWord != null) {
			Session session = sessionFactory.getCurrentSession();
			CriteriaBuilder cb = session.getCriteriaBuilder();
			CriteriaQuery<ConceptStopWord> cq = cb.createQuery(ConceptStopWord.class);
			Root<ConceptStopWord> root = cq.from(ConceptStopWord.class);

			cq.where(cb.and(cb.equal(root.get("value"), conceptStopWord.getValue()),
			    cb.equal(root.get("locale"), conceptStopWord.getLocale())));

			List<ConceptStopWord> stopWordList = session.createQuery(cq).getResultList();

			if (!stopWordList.isEmpty()) {
				throw new DAOException("Duplicate ConceptStopWord Entry");
			}
			conceptStopWord = HibernateUtil.saveOrUpdate(session, conceptStopWord);
		}
		return conceptStopWord;
	}

	void deleteConceptStopWord(Integer conceptStopWordId) throws DAOException {
		if (conceptStopWordId == null) {
			throw new DAOException("conceptStopWordId is null");
		}

		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<ConceptStopWord> cq = cb.createQuery(ConceptStopWord.class);
		Root<ConceptStopWord> root = cq.from(ConceptStopWord.class);

		cq.where(cb.equal(root.get("conceptStopWordId"), conceptStopWordId));

		ConceptStopWord csw = session.createQuery(cq).uniqueResult();
		if (csw == null) {
			throw new DAOException("Concept Stop Word not found or already deleted");
		}
		session.remove(csw);
	}

	List<ConceptStopWord> getAllConceptStopWords() {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<ConceptStopWord> cq = cb.createQuery(ConceptStopWord.class);
		cq.from(ConceptStopWord.class);

		return session.createQuery(cq).getResultList();
	}
}
