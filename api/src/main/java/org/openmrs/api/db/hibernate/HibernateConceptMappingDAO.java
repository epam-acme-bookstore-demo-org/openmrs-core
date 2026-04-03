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
import java.util.List;

import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.openmrs.Concept;
import org.openmrs.ConceptMap;
import org.openmrs.ConceptMapType;
import org.openmrs.ConceptReferenceTerm;
import org.openmrs.ConceptReferenceTermMap;
import org.openmrs.ConceptSource;
import org.openmrs.Drug;
import org.openmrs.DrugReferenceMap;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.DAOException;
import org.openmrs.util.ConceptMapTypeComparator;
import org.openmrs.util.OpenmrsConstants;

import static java.util.stream.Collectors.toList;

/**
 * Delegate class for concept mapping and reference term operations. Extracted from
 * {@link HibernateConceptDAO} to reduce class size.
 * <p>
 * This class is an internal implementation detail and should not be used directly.
 */
class HibernateConceptMappingDAO {

	private final SessionFactory sessionFactory;

	HibernateConceptMappingDAO(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	@Deprecated
	List<Concept> getConceptsByMapping(String code, String sourceName, boolean includeRetired) {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<Concept> cq = cb.createQuery(Concept.class);
		Root<ConceptMap> root = cq.from(ConceptMap.class);

		List<Predicate> predicates = createSearchConceptMapCriteria(cb, root, code, sourceName, includeRetired);

		cq.where(predicates.toArray(new Predicate[] {}));

		cq.select(root.get("concept"));

		Join<ConceptMap, Concept> conceptJoin = root.join("concept");
		if (includeRetired) {
			cq.orderBy(cb.asc(conceptJoin.get("retired")));
		}

		return session.createQuery(cq).getResultList().stream().distinct().collect(toList());
	}

	List<Integer> getConceptIdsByMapping(String code, String sourceName, boolean includeRetired) {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<Integer> cq = cb.createQuery(Integer.class);
		Root<ConceptMap> root = cq.from(ConceptMap.class);

		List<Predicate> predicates = createSearchConceptMapCriteria(cb, root, code, sourceName, includeRetired);

		cq.where(predicates.toArray(new Predicate[] {}));

		cq.select(root.get("concept").get("conceptId"));

		Join<ConceptMap, Concept> conceptJoin = root.join("concept");
		if (includeRetired) {
			cq.orderBy(cb.asc(conceptJoin.get("retired")));
		}

		return session.createQuery(cq).getResultList().stream().distinct().collect(toList());
	}

	List<ConceptMap> getConceptMapsBySource(ConceptSource conceptSource) throws DAOException {
		if (conceptSource == null) {
			return List.of();
		}
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<ConceptMap> cq = cb.createQuery(ConceptMap.class);

		Root<ConceptMap> root = cq.from(ConceptMap.class);
		Join<ConceptMap, ConceptReferenceTerm> conceptReferenceTermJoin = root.join("conceptReferenceTerm");

		cq.where(cb.equal(conceptReferenceTermJoin.get("conceptSource"), conceptSource));

		return session.createQuery(cq).getResultList();
	}

	List<ConceptMapType> getConceptMapTypes(boolean includeRetired, boolean includeHidden) throws DAOException {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<ConceptMapType> cq = cb.createQuery(ConceptMapType.class);
		Root<ConceptMapType> root = cq.from(ConceptMapType.class);

		var predicates = new ArrayList<Predicate>();
		if (!includeRetired) {
			predicates.add(cb.isFalse(root.get("retired")));
		}
		if (!includeHidden) {
			predicates.add(cb.isFalse(root.get("isHidden")));
		}

		cq.where(predicates.toArray(new Predicate[] {}));

		List<ConceptMapType> conceptMapTypes = session.createQuery(cq).getResultList();
		conceptMapTypes.sort(new ConceptMapTypeComparator());

		return conceptMapTypes;
	}

	ConceptMapType getConceptMapType(Integer conceptMapTypeId) throws DAOException {
		return sessionFactory.getCurrentSession().get(ConceptMapType.class, conceptMapTypeId);
	}

	ConceptMapType getConceptMapTypeByUuid(String uuid) throws DAOException {
		return HibernateUtil.getUniqueEntityByUUID(sessionFactory, ConceptMapType.class, uuid);
	}

	ConceptMapType getConceptMapTypeByName(String name) throws DAOException {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<ConceptMapType> cq = cb.createQuery(ConceptMapType.class);
		Root<ConceptMapType> root = cq.from(ConceptMapType.class);

		cq.where(cb.like(cb.lower(root.get("name")), MatchMode.EXACT.toLowerCasePattern(name)));

		return session.createQuery(cq).uniqueResult();
	}

	ConceptMapType saveConceptMapType(ConceptMapType conceptMapType) throws DAOException {
		return HibernateUtil.saveOrUpdate(sessionFactory.getCurrentSession(), conceptMapType);
	}

	void deleteConceptMapType(ConceptMapType conceptMapType) throws DAOException {
		sessionFactory.getCurrentSession().remove(conceptMapType);
	}

	List<ConceptReferenceTerm> getConceptReferenceTerms(boolean includeRetired) throws DAOException {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<ConceptReferenceTerm> cq = cb.createQuery(ConceptReferenceTerm.class);
		Root<ConceptReferenceTerm> root = cq.from(ConceptReferenceTerm.class);

		if (!includeRetired) {
			cq.where(cb.isFalse(root.get("retired")));
		}
		return session.createQuery(cq).getResultList();
	}

	ConceptReferenceTerm getConceptReferenceTerm(Integer conceptReferenceTermId) throws DAOException {
		return sessionFactory.getCurrentSession().get(ConceptReferenceTerm.class, conceptReferenceTermId);
	}

	ConceptReferenceTerm getConceptReferenceTermByUuid(String uuid) throws DAOException {
		return HibernateUtil.getUniqueEntityByUUID(sessionFactory, ConceptReferenceTerm.class, uuid);
	}

	List<ConceptReferenceTerm> getConceptReferenceTermsBySource(ConceptSource conceptSource) throws DAOException {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<ConceptReferenceTerm> cq = cb.createQuery(ConceptReferenceTerm.class);
		Root<ConceptReferenceTerm> root = cq.from(ConceptReferenceTerm.class);

		cq.where(cb.equal(root.get("conceptSource"), conceptSource));

		return session.createQuery(cq).getResultList();
	}

	ConceptReferenceTerm getConceptReferenceTermByName(String name, ConceptSource conceptSource) throws DAOException {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<ConceptReferenceTerm> cq = cb.createQuery(ConceptReferenceTerm.class);
		Root<ConceptReferenceTerm> root = cq.from(ConceptReferenceTerm.class);

		Predicate namePredicate = cb.like(cb.lower(root.get("name")), MatchMode.EXACT.toLowerCasePattern(name));
		Predicate sourcePredicate = cb.equal(root.get("conceptSource"), conceptSource);

		cq.where(cb.and(namePredicate, sourcePredicate));

		List<ConceptReferenceTerm> terms = session.createQuery(cq).getResultList();
		if (terms.isEmpty()) {
			return null;
		} else if (terms.size() > 1) {
			throw new APIException("ConceptReferenceTerm.foundMultipleTermsWithNameInSource",
			        new Object[] { name, conceptSource.getName() });
		}
		return terms.getFirst();
	}

	ConceptReferenceTerm getConceptReferenceTermByCode(String code, ConceptSource conceptSource) throws DAOException {
		List<ConceptReferenceTerm> conceptReferenceTerms = getConceptReferenceTermByCode(code, conceptSource, true);

		if (conceptReferenceTerms.isEmpty()) {
			return null;
		} else if (conceptReferenceTerms.size() > 1) {
			List<ConceptReferenceTerm> unretiredConceptReferenceTerms = conceptReferenceTerms.stream()
			        .filter(term -> !term.getRetired()).collect(toList());
			if (unretiredConceptReferenceTerms.size() == 1) {
				return unretiredConceptReferenceTerms.getFirst();
			}

			// either more than one unretired concept term or more than one retired concept term
			throw new APIException("ConceptReferenceTerm.foundMultipleTermsWithCodeInSource",
			        new Object[] { code, conceptSource.getName() });
		}

		return conceptReferenceTerms.getFirst();
	}

	List<ConceptReferenceTerm> getConceptReferenceTermByCode(String code, ConceptSource conceptSource,
	        boolean includeRetired) throws DAOException {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<ConceptReferenceTerm> cq = cb.createQuery(ConceptReferenceTerm.class);
		Root<ConceptReferenceTerm> root = cq.from(ConceptReferenceTerm.class);

		var predicates = new ArrayList<Predicate>();
		predicates.add(cb.equal(root.get("code"), code));
		predicates.add(cb.equal(root.get("conceptSource"), conceptSource));

		if (!includeRetired) {
			predicates.add(cb.isFalse(root.get("retired")));
		}
		cq.where(predicates.toArray(new Predicate[] {}));

		return session.createQuery(cq).getResultList();
	}

	ConceptReferenceTerm saveConceptReferenceTerm(ConceptReferenceTerm conceptReferenceTerm) throws DAOException {
		return HibernateUtil.saveOrUpdate(sessionFactory.getCurrentSession(), conceptReferenceTerm);
	}

	void deleteConceptReferenceTerm(ConceptReferenceTerm conceptReferenceTerm) throws DAOException {
		sessionFactory.getCurrentSession().remove(conceptReferenceTerm);
	}

	Long getCountOfConceptReferenceTerms(String query, ConceptSource conceptSource, boolean includeRetired)
	        throws DAOException {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<Long> cq = cb.createQuery(Long.class);
		Root<ConceptReferenceTerm> root = cq.from(ConceptReferenceTerm.class);

		List<Predicate> predicates = createConceptReferenceTermPredicates(cb, root, query, conceptSource, includeRetired);

		cq.where(predicates.toArray(new Predicate[] {})).select(cb.count(root));

		return session.createQuery(cq).getSingleResult();
	}

	List<ConceptReferenceTerm> getConceptReferenceTerms(String query, ConceptSource conceptSource, Integer start,
	        Integer length, boolean includeRetired) throws APIException {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<ConceptReferenceTerm> cq = cb.createQuery(ConceptReferenceTerm.class);
		Root<ConceptReferenceTerm> root = cq.from(ConceptReferenceTerm.class);

		List<Predicate> predicates = createConceptReferenceTermPredicates(cb, root, query, conceptSource, includeRetired);
		cq.where(predicates.toArray(new Predicate[] {}));

		TypedQuery<ConceptReferenceTerm> typedQuery = session.createQuery(cq);

		if (start != null) {
			typedQuery.setFirstResult(start);
		}
		if (length != null && length > 0) {
			typedQuery.setMaxResults(length);
		}

		return typedQuery.getResultList();
	}

	List<ConceptReferenceTermMap> getReferenceTermMappingsTo(ConceptReferenceTerm term) throws DAOException {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<ConceptReferenceTermMap> cq = cb.createQuery(ConceptReferenceTermMap.class);
		Root<ConceptReferenceTermMap> root = cq.from(ConceptReferenceTermMap.class);

		cq.where(cb.equal(root.get("termB"), term));

		return session.createQuery(cq).getResultList();
	}

	boolean isConceptReferenceTermInUse(ConceptReferenceTerm term) throws DAOException {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();

		// Check in ConceptMap table
		CriteriaQuery<Long> conceptMapQuery = cb.createQuery(Long.class);
		Root<ConceptMap> conceptMapRoot = conceptMapQuery.from(ConceptMap.class);
		conceptMapQuery.select(cb.count(conceptMapRoot));
		conceptMapQuery.where(cb.equal(conceptMapRoot.get("conceptReferenceTerm"), term));

		Long conceptMapCount = session.createQuery(conceptMapQuery).uniqueResult();
		if (conceptMapCount > 0) {
			return true;
		}

		// Check in ConceptReferenceTermMap table
		CriteriaQuery<Long> conceptReferenceTermMapQuery = cb.createQuery(Long.class);
		Root<ConceptReferenceTermMap> conceptReferenceTermMapRoot = conceptReferenceTermMapQuery
		        .from(ConceptReferenceTermMap.class);
		conceptReferenceTermMapQuery.select(cb.count(conceptReferenceTermMapRoot));
		conceptReferenceTermMapQuery.where(cb.equal(conceptReferenceTermMapRoot.get("termB"), term));

		Long conceptReferenceTermMapCount = session.createQuery(conceptReferenceTermMapQuery).uniqueResult();
		return conceptReferenceTermMapCount > 0;
	}

	boolean isConceptMapTypeInUse(ConceptMapType mapType) throws DAOException {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();

		// Check in ConceptMap table
		CriteriaQuery<Long> conceptQuery = cb.createQuery(Long.class);
		Root<ConceptMap> conceptRoot = conceptQuery.from(ConceptMap.class);
		conceptQuery.select(cb.count(conceptRoot));
		conceptQuery.where(cb.equal(conceptRoot.get("conceptMapType"), mapType));

		Long conceptCount = session.createQuery(conceptQuery).uniqueResult();
		if (conceptCount > 0) {
			return true;
		}

		// Check in ConceptReferenceTermMap table
		CriteriaQuery<Long> conceptReferenceTermMapQuery = cb.createQuery(Long.class);
		Root<ConceptReferenceTermMap> conceptReferenceTermMapRoot = conceptReferenceTermMapQuery
		        .from(ConceptReferenceTermMap.class);
		conceptReferenceTermMapQuery.select(cb.count(conceptReferenceTermMapRoot));
		conceptReferenceTermMapQuery.where(cb.equal(conceptReferenceTermMapRoot.get("conceptMapType"), mapType));

		Long conceptReferenceTermMapCount = session.createQuery(conceptReferenceTermMapQuery).uniqueResult();
		return conceptReferenceTermMapCount > 0;
	}

	ConceptMapType getDefaultConceptMapType() throws DAOException {
		FlushMode previousFlushMode = sessionFactory.getCurrentSession().getHibernateFlushMode();
		sessionFactory.getCurrentSession().setHibernateFlushMode(FlushMode.MANUAL);
		try {
			//Defaults to same-as if the gp is not set.
			String defaultConceptMapType = Context.getAdministrationService()
			        .getGlobalProperty(OpenmrsConstants.GP_DEFAULT_CONCEPT_MAP_TYPE);
			if (defaultConceptMapType == null) {
				throw new DAOException("The default concept map type is not set. You need to set the '"
				        + OpenmrsConstants.GP_DEFAULT_CONCEPT_MAP_TYPE + "' global property.");
			}

			ConceptMapType conceptMapType = getConceptMapTypeByName(defaultConceptMapType);
			if (conceptMapType == null) {
				throw new DAOException("The default concept map type (name: " + defaultConceptMapType
				        + ") does not exist! You need to set the '" + OpenmrsConstants.GP_DEFAULT_CONCEPT_MAP_TYPE
				        + "' global property.");
			}
			return conceptMapType;
		} finally {
			sessionFactory.getCurrentSession().setHibernateFlushMode(previousFlushMode);
		}
	}

	List<Drug> getDrugsByMapping(String code, ConceptSource conceptSource, Collection<ConceptMapType> withAnyOfTheseTypes,
	        boolean includeRetired) throws DAOException {

		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<Drug> cq = cb.createQuery(Drug.class);
		Root<Drug> drugRoot = cq.from(Drug.class);

		Join<Drug, DrugReferenceMap> drugReferenceMapJoin = drugRoot.join("drugReferenceMaps");
		Join<DrugReferenceMap, ConceptReferenceTerm> termJoin = drugReferenceMapJoin.join("conceptReferenceTerm");
		List<Predicate> basePredicates = createSearchDrugByMappingPredicates(cb, drugRoot, drugReferenceMapJoin, termJoin,
		    code, conceptSource, includeRetired);

		if (!withAnyOfTheseTypes.isEmpty()) {
			// Create a predicate to check if the ConceptMapType is in the provided collection
			Predicate mapTypePredicate = drugReferenceMapJoin.get("conceptMapType").in(withAnyOfTheseTypes);
			basePredicates.add(mapTypePredicate);
		}

		cq.where(basePredicates.toArray(new Predicate[] {}));

		return session.createQuery(cq).getResultList().stream().distinct().collect(toList());
	}

	Drug getDrugByMapping(String code, ConceptSource conceptSource,
	        Collection<ConceptMapType> withAnyOfTheseTypesOrOrderOfPreference) throws DAOException {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();

		if (!withAnyOfTheseTypesOrOrderOfPreference.isEmpty()) {

			for (ConceptMapType conceptMapType : withAnyOfTheseTypesOrOrderOfPreference) {
				CriteriaQuery<Drug> cq = cb.createQuery(Drug.class);
				Root<Drug> drugRoot = cq.from(Drug.class);

				Join<Drug, DrugReferenceMap> drugReferenceMapJoin = drugRoot.join("drugReferenceMaps");
				Join<DrugReferenceMap, ConceptReferenceTerm> termJoin = drugReferenceMapJoin.join("conceptReferenceTerm");

				List<Predicate> basePredicates = createSearchDrugByMappingPredicates(cb, drugRoot, drugReferenceMapJoin,
				    termJoin, code, conceptSource, true);

				var predicates = new ArrayList<Predicate>(basePredicates);
				predicates.add(cb.equal(drugReferenceMapJoin.get("conceptMapType"), conceptMapType));
				cq.where(predicates.toArray(new Predicate[] {}));

				TypedQuery<Drug> query = session.createQuery(cq);
				List<Drug> drugs = query.getResultList();
				if (drugs.size() > 1) {
					throw new DAOException("There are multiple matches for the highest-priority ConceptMapType");
				} else if (drugs.size() == 1) {
					return drugs.getFirst();
				}
			}
		} else {
			CriteriaQuery<Drug> cq = cb.createQuery(Drug.class);
			Root<Drug> drugRoot = cq.from(Drug.class);

			Join<Drug, DrugReferenceMap> drugReferenceMapJoin = drugRoot.join("drugReferenceMaps");
			Join<DrugReferenceMap, ConceptReferenceTerm> termJoin = drugReferenceMapJoin.join("conceptReferenceTerm");

			List<Predicate> basePredicates = createSearchDrugByMappingPredicates(cb, drugRoot, drugReferenceMapJoin,
			    termJoin, code, conceptSource, true);

			cq.where(basePredicates.toArray(new Predicate[] {}));

			TypedQuery<Drug> query = session.createQuery(cq);
			List<Drug> drugs = query.getResultList();
			if (drugs.size() > 1) {
				throw new DAOException("There are multiple matches for the highest-priority ConceptMapType");
			} else if (drugs.size() == 1) {
				return drugs.getFirst();
			}
		}
		return null;
	}

	// ---- Private helper methods ----

	private List<Predicate> createConceptReferenceTermPredicates(CriteriaBuilder cb, Root<ConceptReferenceTerm> root,
	        String query, ConceptSource conceptSource, boolean includeRetired) {
		var predicates = new ArrayList<Predicate>();

		if (conceptSource != null) {
			predicates.add(cb.equal(root.get("conceptSource"), conceptSource));
		}
		if (!includeRetired) {
			predicates.add(cb.isFalse(root.get("retired")));
		}
		if (query != null) {
			Predicate namePredicate = cb.like(cb.lower(root.get("name")), MatchMode.ANYWHERE.toLowerCasePattern(query));
			Predicate codePredicate = cb.like(cb.lower(root.get("code")), MatchMode.ANYWHERE.toLowerCasePattern(query));

			predicates.add(cb.or(namePredicate, codePredicate));
		}

		return predicates;
	}

	private List<Predicate> createSearchDrugByMappingPredicates(CriteriaBuilder cb, Root<Drug> drugRoot,
	        Join<Drug, DrugReferenceMap> drugReferenceMapJoin, Join<DrugReferenceMap, ConceptReferenceTerm> termJoin,
	        String code, ConceptSource conceptSource, boolean includeRetired) {
		var predicates = new ArrayList<Predicate>();

		if (code != null) {
			predicates.add(cb.equal(termJoin.get("code"), code));
		}
		if (conceptSource != null) {
			predicates.add(cb.equal(termJoin.get("conceptSource"), conceptSource));
		}
		if (!includeRetired) {
			predicates.add(cb.isFalse(drugRoot.get("retired")));
		}

		return predicates;
	}

	private List<Predicate> createSearchConceptMapCriteria(CriteriaBuilder cb, Root<ConceptMap> root, String code,
	        String sourceName, boolean includeRetired) {
		var predicates = new ArrayList<Predicate>();

		Join<ConceptMap, ConceptReferenceTerm> termJoin = root.join("conceptReferenceTerm");

		// Match the source code to the passed code
		if (Context.getAdministrationService().isDatabaseStringComparisonCaseSensitive()) {
			predicates.add(cb.equal(cb.lower(termJoin.get("code")), code.toLowerCase()));
		} else {
			predicates.add(cb.equal(termJoin.get("code"), code));
		}

		// Join to concept reference source and match to the hl7Code or source name
		Join<ConceptReferenceTerm, ConceptSource> sourceJoin = termJoin.join("conceptSource");

		Predicate namePredicate = Context.getAdministrationService().isDatabaseStringComparisonCaseSensitive()
		        ? cb.equal(cb.lower(sourceJoin.get("name")), sourceName.toLowerCase())
		        : cb.equal(sourceJoin.get("name"), sourceName);
		Predicate hl7CodePredicate = Context.getAdministrationService().isDatabaseStringComparisonCaseSensitive()
		        ? cb.equal(cb.lower(sourceJoin.get("hl7Code")), sourceName.toLowerCase())
		        : cb.equal(sourceJoin.get("hl7Code"), sourceName);

		predicates.add(cb.or(namePredicate, hl7CodePredicate));

		// Join to concept and filter retired ones if necessary
		Join<ConceptMap, Concept> conceptJoin = root.join("concept");
		if (!includeRetired) {
			predicates.add(cb.isFalse(conceptJoin.get("retired")));
		}
		return predicates;
	}
}
