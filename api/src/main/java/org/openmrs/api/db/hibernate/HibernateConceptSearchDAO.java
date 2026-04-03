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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import jakarta.persistence.Query;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.search.engine.search.predicate.SearchPredicate;
import org.hibernate.search.engine.search.predicate.dsl.BooleanPredicateClausesStep;
import org.hibernate.search.engine.search.predicate.dsl.SearchPredicateFactory;
import org.hibernate.search.engine.search.query.SearchQuery;
import org.hibernate.search.mapper.orm.scope.SearchScope;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.openmrs.Concept;
import org.openmrs.ConceptAnswer;
import org.openmrs.ConceptClass;
import org.openmrs.ConceptDatatype;
import org.openmrs.ConceptName;
import org.openmrs.ConceptSearchResult;
import org.openmrs.Drug;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.DAOException;
import org.openmrs.api.db.hibernate.search.SearchQueryUnique;
import org.openmrs.api.db.hibernate.search.session.SearchSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

/**
 * Delegate class for concept and drug search/lookup operations. Extracted from
 * {@link HibernateConceptDAO} to reduce class size.
 * <p>
 * This class is an internal implementation detail and should not be used directly.
 */
class HibernateConceptSearchDAO {

	private static final Logger log = LoggerFactory.getLogger(HibernateConceptSearchDAO.class);

	private final SessionFactory sessionFactory;

	private final SearchSessionFactory searchSessionFactory;

	HibernateConceptSearchDAO(SessionFactory sessionFactory, SearchSessionFactory searchSessionFactory) {
		this.sessionFactory = sessionFactory;
		this.searchSessionFactory = searchSessionFactory;
	}

	List<Concept> getConcepts(final String name, final Locale loc, final boolean searchOnPhrase,
	        final List<ConceptClass> classes, final List<ConceptDatatype> datatypes) throws DAOException {

		final Locale locale;
		if (loc == null) {
			locale = Context.getLocale();
		} else {
			locale = loc;
		}

		return SearchQueryUnique.search(searchSessionFactory,
		    SearchQueryUnique.newQuery(ConceptName.class, f -> newConceptNamePredicate(f, name, !searchOnPhrase,
		        List.of(locale), false, false, classes, null, datatypes, null, null), "concept.conceptId",
		        ConceptName::getConcept));
	}

	LinkedHashSet<Concept> transformNamesToConcepts(List<ConceptName> names) {
		var concepts = new LinkedHashSet<Concept>();

		for (ConceptName name : names) {
			concepts.add(name.getConcept());
		}

		return concepts;
	}

	@SuppressWarnings("unchecked") // Hibernate HQL query returns raw List
	List<Concept> getConceptsByAnswer(Concept concept) {
		String q = "select c from Concept c join c.answers ca where ca.answerConcept = :answer";
		Query query = sessionFactory.getCurrentSession().createQuery(q);
		query.setParameter("answer", concept);

		return query.getResultList();
	}

	Concept getPrevConcept(Concept c) {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<Concept> cq = cb.createQuery(Concept.class);
		Root<Concept> root = cq.from(Concept.class);

		Integer i = c.getConceptId();

		cq.where(cb.lessThan(root.get("conceptId"), i));
		cq.orderBy(cb.desc(root.get("conceptId")));

		List<Concept> concepts = session.createQuery(cq).setMaxResults(1).getResultList();

		if (concepts.isEmpty()) {
			return null;
		}

		return concepts.getFirst();
	}

	Concept getNextConcept(Concept c) {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<Concept> cq = cb.createQuery(Concept.class);
		Root<Concept> root = cq.from(Concept.class);

		Integer i = c.getConceptId();

		cq.where(cb.greaterThan(root.get("conceptId"), i));
		cq.orderBy(cb.asc(root.get("conceptId")));

		List<Concept> concepts = session.createQuery(cq).setMaxResults(1).getResultList();

		if (concepts.isEmpty()) {
			return null;
		}

		return concepts.getFirst();
	}

	@SuppressWarnings("unchecked") // Hibernate HQL query returns raw List
	List<Concept> getConceptsWithDrugsInFormulary() {
		Query query = sessionFactory.getCurrentSession()
		        .createQuery("select distinct concept from Drug d where d.retired = false");
		return query.getResultList();
	}

	Long getCountOfDrugs(String drugName, Concept concept, boolean searchKeywords, boolean searchDrugConceptNames,
	        boolean includeRetired) throws DAOException {
		SearchQuery<Drug> drugsQuery = newDrugQuery(drugName, searchKeywords, searchDrugConceptNames, Context.getLocale(),
		    false, concept, includeRetired);

		if (drugsQuery == null) {
			return 0L;
		}

		return drugsQuery.fetchTotalHitCount();
	}

	List<Drug> getDrugs(String drugName, Concept concept, boolean searchKeywords, boolean searchDrugConceptNames,
	        boolean includeRetired, Integer start, Integer length) throws DAOException {
		SearchQuery<Drug> drugsQuery = newDrugQuery(drugName, searchKeywords, searchDrugConceptNames, Context.getLocale(),
		    false, concept, includeRetired);

		if (drugsQuery == null) {
			return List.of();
		}

		return drugsQuery.fetchHits(start, length);
	}

	List<Drug> getDrugs(final String phrase) throws DAOException {
		SearchQuery<Drug> drugQuery = newDrugQuery(phrase, true, false, Context.getLocale(), false, null, false);

		if (drugQuery == null) {
			return List.of();
		}

		return drugQuery.fetchAllHits();
	}

	List<ConceptSearchResult> getConcepts(final String phrase, final List<Locale> locales, final boolean includeRetired,
	        final List<ConceptClass> requireClasses, final List<ConceptClass> excludeClasses,
	        final List<ConceptDatatype> requireDatatypes, final List<ConceptDatatype> excludeDatatypes,
	        final Concept answersToConcept, final Integer start, final Integer size) throws DAOException {

		return SearchQueryUnique.search(searchSessionFactory,
		    SearchQueryUnique.newQuery(ConceptName.class,
		        f -> newConceptNamePredicate(f, phrase, true, locales, false, includeRetired, requireClasses, excludeClasses,
		            requireDatatypes, excludeDatatypes, answersToConcept),
		        "concept.conceptId", n -> new ConceptSearchResult(phrase, n.getConcept(), n)),
		    start, size);
	}

	Integer getCountOfConcepts(final String phrase, List<Locale> locales, boolean includeRetired,
	        List<ConceptClass> requireClasses, List<ConceptClass> excludeClasses, List<ConceptDatatype> requireDatatypes,
	        List<ConceptDatatype> excludeDatatypes, Concept answersToConcept) throws DAOException {

		return Math.toIntExact(SearchQueryUnique.searchCount(searchSessionFactory,
		    SearchQueryUnique.newQuery(
		        ConceptName.class, f -> newConceptNamePredicate(f, phrase, true, locales, false, includeRetired,
		            requireClasses, excludeClasses, requireDatatypes, excludeDatatypes, answersToConcept),
		        "concept.conceptId")));
	}

	List<Concept> getConceptsByName(final String name, final Locale locale, final Boolean exactLocale) {

		var locales = new ArrayList<Locale>();
		if (locale == null) {
			locales.add(Context.getLocale());
		} else {
			locales.add(locale);
		}

		boolean searchExactLocale = (exactLocale == null) ? false : exactLocale;

		return SearchQueryUnique.search(searchSessionFactory,
		    SearchQueryUnique.newQuery(ConceptName.class,
		        f -> newConceptNamePredicate(f, name, true, locales, searchExactLocale, false, null, null, null, null, null),
		        "concept.conceptId", ConceptName::getConcept));
	}

	Concept getConceptByName(final String name) {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<ConceptName> cq = cb.createQuery(ConceptName.class);
		Root<ConceptName> root = cq.from(ConceptName.class);
		Join<ConceptName, Concept> conceptJoin = root.join("concept");

		Locale locale = Context.getLocale();
		Locale language = Locale.of(locale.getLanguage() + "%");
		var predicates = new ArrayList<Predicate>();

		predicates.add(
		    cb.or(cb.equal(root.get("locale"), locale), cb.like(root.get("locale").as(String.class), language.toString())));
		if (Context.getAdministrationService().isDatabaseStringComparisonCaseSensitive()) {
			predicates.add(cb.like(cb.lower(root.get("name")), name.toLowerCase()));
		} else {
			predicates.add(cb.equal(root.get("name"), name));
		}
		predicates.add(cb.isFalse(root.get("voided")));
		predicates.add(cb.isFalse(conceptJoin.get("retired")));

		cq.where(predicates.toArray(new Predicate[0]));

		List<ConceptName> list = session.createQuery(cq).getResultList();
		LinkedHashSet<Concept> concepts = transformNamesToConcepts(list);

		if (concepts.size() == 1) {
			return concepts.getFirst();
		}
		if (list.isEmpty()) {
			log.warn("No concept found for '" + name + "'");
			return null;
		}

		log.warn("Multiple concepts found for '" + name + "'");
		return findConceptByExactName(concepts, name, locale);
	}

	boolean isConceptNameDuplicate(ConceptName name) {
		if (name.getVoided()) {
			return false;
		}
		if (name.getConcept() != null) {
			if (name.getConcept().getRetired()) {
				return false;
			}

			//If it is not a default name of a concept, it cannot be a duplicate.
			//Note that a concept may not have a default name for the given locale, if just a short name or
			//a search term is set.
			if (!name.equals(name.getConcept().getName(name.getLocale()))) {
				return false;
			}
		}

		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<ConceptName> cq = cb.createQuery(ConceptName.class);
		Root<ConceptName> root = cq.from(ConceptName.class);

		var predicates = new ArrayList<Predicate>();

		predicates.add(cb.isFalse(root.get("voided")));
		predicates.add(cb.or(cb.equal(root.get("locale"), name.getLocale()),
		    cb.equal(root.get("locale"), Locale.of(name.getLocale().getLanguage()))));

		if (Context.getAdministrationService().isDatabaseStringComparisonCaseSensitive()) {
			predicates.add(cb.equal(cb.lower(root.get("name")), name.getName().toLowerCase()));
		} else {
			predicates.add(cb.equal(root.get("name"), name.getName()));
		}

		cq.where(predicates.toArray(new Predicate[0]));

		List<ConceptName> candidateNames = session.createQuery(cq).getResultList();

		for (ConceptName candidateName : candidateNames) {
			if (candidateName.getConcept().getRetired()) {
				continue;
			}
			if (candidateName.getConcept().equals(name.getConcept())) {
				continue;
			}
			// If it is a default name for a concept
			if (candidateName.getConcept().getName(candidateName.getLocale()).equals(candidateName)) {
				return true;
			}
		}

		return false;
	}

	List<Drug> getDrugs(String searchPhrase, Locale locale, boolean exactLocale, boolean includeRetired) {
		SearchQuery<Drug> drugQuery = newDrugQuery(searchPhrase, true, true, locale, exactLocale, null, includeRetired);

		return drugQuery.fetchAllHits();
	}

	List<Concept> getConceptsByClass(ConceptClass conceptClass) {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<Concept> cq = cb.createQuery(Concept.class);
		Root<Concept> root = cq.from(Concept.class);

		cq.where(cb.equal(root.get("conceptClass"), conceptClass));

		return session.createQuery(cq).getResultList();
	}

	// ---- Private helper methods ----

	private Concept findConceptByExactName(LinkedHashSet<Concept> concepts, String name, Locale locale) {
		for (Concept concept : concepts) {
			for (ConceptName conceptName : concept.getNames(locale)) {
				if (conceptName.getName().equalsIgnoreCase(name)) {
					return concept;
				}
			}
			for (ConceptName indexTerm : concept.getIndexTermsForLocale(locale)) {
				if (indexTerm.getName().equalsIgnoreCase(name)) {
					return concept;
				}
			}
		}
		return null;
	}

	private SearchQuery<Drug> newDrugQuery(String drugName, boolean searchKeywords, boolean searchDrugConceptNames,
	        Locale locale, boolean exactLocale, Concept concept, boolean includeRetired) {
		if (StringUtils.isBlank(drugName) && concept == null) {
			return null;
		}
		final Collection<Locale> locales = List.of(locale == null ? Context.getLocale() : locale);

		final List<Object> conceptIds;
		if (searchDrugConceptNames) {
			SearchSession searchSession = searchSessionFactory.getSearchSession();
			SearchScope<ConceptName> scope = searchSession.scope(ConceptName.class);

			SearchPredicate conceptNamePredicate = newConceptNamePredicate(scope.predicate(), drugName, searchKeywords,
			    locales, exactLocale, includeRetired, null, null, null, null, null);

			conceptIds = SearchQueryUnique.findUniqueKeys(searchSession, scope, conceptNamePredicate, "concept.conceptId");
		} else {
			conceptIds = List.of();
		}

		return searchSessionFactory.getSearchSession().search(Drug.class).where(f -> f.bool().with(b -> {
			b.minimumShouldMatchNumber(1);
			List<String> tokenizedName = tokenizeName(drugName, locales);
			BooleanPredicateClausesStep<?, ?> nameQuery = newNameQuery(f, tokenizedName, drugName, searchKeywords);
			b.should(f.match().field("drugReferenceMaps.conceptReferenceTerm.code").matching(drugName).boost(10f));
			b.should(nameQuery.boost(0.5f));
			if (concept != null) {
				b.should(f.match().field("concept.conceptId").matching(concept.getId()).boost(0.1f));
			}
			if (!conceptIds.isEmpty()) {
				float boost = 0.1f;
				int boostItems = 10; // boost first items in order
				int i = 0;
				for (Object conceptId : conceptIds) {
					b.should(f.match().field("concept.conceptId").matching(conceptId).boost(boost));
					boost = boost * 0.9f;
					i++;
					if (boostItems == i) {
						break;
					}
				}
				if (conceptIds.size() > boostItems) {
					b.should(f.terms().field("concept.conceptId")
					        .matchingAny(conceptIds.subList(boostItems, conceptIds.size())).boost(boost));
				}
			}
			if (!includeRetired) {
				b.filter(f.match().field("retired").matching(Boolean.FALSE));
			}
		})).toQuery();
	}

	private SearchPredicate newConceptNamePredicate(SearchPredicateFactory f, final String phrase, boolean searchKeywords,
	        Collection<Locale> locales, boolean searchExactLocale, boolean includeRetired, List<ConceptClass> requireClasses,
	        List<ConceptClass> excludeClasses, List<ConceptDatatype> requireDatatypes,
	        List<ConceptDatatype> excludeDatatypes, Concept answersToConcept) {
		return f.bool().with(b -> {
			if (!StringUtils.isBlank(phrase)) {
				final Collection<Locale> searchLocales;

				if (locales == null) {
					searchLocales = List.of(Context.getLocale());
				} else {
					searchLocales = new HashSet<>(locales);
				}

				b.must(newConceptNameQuery(f, phrase, searchKeywords, searchLocales, searchExactLocale));
			}

			if (!CollectionUtils.isEmpty(requireClasses)) {
				b.filter(f.terms().field("concept.conceptClass").matchingAny(requireClasses));
			}
			if (!CollectionUtils.isEmpty(excludeClasses)) {
				b.filter(f.not(f.terms().field("concept.conceptClass").matchingAny(excludeClasses)));
			}
			if (!CollectionUtils.isEmpty(requireDatatypes)) {
				b.filter(f.terms().field("concept.datatype").matchingAny(requireDatatypes));
			}
			if (!CollectionUtils.isEmpty(excludeDatatypes)) {
				b.filter(f.not(f.terms().field("concept.datatype").matchingAny(excludeDatatypes)));
			}

			if (answersToConcept != null) {
				Collection<ConceptAnswer> answers = answersToConcept.getAnswers(false);

				if (answers != null && !answers.isEmpty()) {
					var ids = new ArrayList<Integer>();
					for (ConceptAnswer conceptAnswer : answersToConcept.getAnswers(false)) {
						ids.add(conceptAnswer.getAnswerConcept().getId());
					}
					b.filter(f.terms().field("concept.conceptId").matchingAny(ids));
				}
			}

			if (!includeRetired) {
				b.filter(f.match().field("concept.retired").matching(Boolean.FALSE));
			}
		}).toPredicate();
	}

	private SearchPredicate newConceptNameQuery(SearchPredicateFactory f, final String name, final boolean searchKeywords,
	        final Collection<Locale> locales, final boolean searchExactLocale) {
		return f.bool().with(b -> {
			if (searchExactLocale) {
				b.must(f.terms().field("locale").matchingAny(locales));
			} else {
				b.must(f.bool().with(bb -> {
					for (Locale locale : locales) {
						bb.should(f.wildcard().field("locale").matching(locale.getLanguage() + "*"));
						if (!StringUtils.isBlank(locale.getCountry())) {
							bb.should(f.match().field("locale").matching(locale).boost(2f));
						}
					}
				}));
			}
			b.filter(f.match().field("voided").matching(false));

			b.must(f.bool().with(bb -> {
				List<String> tokenizedName = tokenizeName(name, locales);
				BooleanPredicateClausesStep<?, ?> nameQuery = newNameQuery(f, tokenizedName, name, searchKeywords);

				bb.should(f.match().field("concept.conceptMappings.conceptReferenceTerm.code").matching(name).boost(10f));
				bb.should(f.and().add(nameQuery).add(f.match().field("localePreferred").matching(true)).boost(2f));
				bb.should(nameQuery);
			}));
		}).toPredicate();
	}

	private BooleanPredicateClausesStep<?, ?> newNameQuery(SearchPredicateFactory f, final List<String> tokenizedName,
	        final String name, final boolean searchKeywords) {
		return f.bool().with(b -> {
			b.minimumShouldMatchNumber(1);
			b.should(f.phrase().field("name").matching(name).boost(8f));
			if (searchKeywords) {
				if (!tokenizedName.isEmpty()) {
					b.should(f.bool().with(bb -> {
						for (String token : tokenizedName) {
							bb.must(f.bool().with(bbb -> {
								bbb.minimumShouldMatchNumber(1);
								bbb.should(f.match().field("name").matching(token).boost(3f));
								bbb.should(f.wildcard().field("name").matching(token + "*").boost(2f));
								bbb.should(f.match().field("name").matching(token).fuzzy(1, 3));
							}));
						}
					}));
				}
			}
		});
	}

	private List<String> tokenizeName(final String escapedName, final Collection<Locale> locales) {
		List<String> words = new ArrayList<>(Arrays.asList(escapedName.strip().split(" ")));

		var stopWords = new HashSet<String>();
		for (Locale locale : locales) {
			stopWords.addAll(Context.getConceptService().getConceptStopWords(locale));
		}

		var tokenizedName = new ArrayList<String>();

		for (String word : words) {
			word = word.strip();

			if (!word.isEmpty() && !stopWords.contains(word.toUpperCase())) {
				tokenizedName.add(word);
			}
		}

		return tokenizedName;
	}
}
