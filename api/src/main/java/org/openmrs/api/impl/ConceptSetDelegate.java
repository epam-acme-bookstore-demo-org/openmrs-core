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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.openmrs.Concept;
import org.openmrs.ConceptSet;
import org.openmrs.api.db.ConceptDAO;

/**
 * Delegate for concept set operations, extracted from {@link ConceptServiceImpl}.
 * <p>
 * Handles concept set CRUD, membership lookups, and recursive set expansion. This class is not a
 * Spring bean — it receives the DAO via constructor and is called internally by
 * {@link ConceptServiceImpl}.
 *
 * @since 2.9.0
 */
class ConceptSetDelegate {

	private final ConceptDAO dao;

	ConceptSetDelegate(ConceptDAO dao) {
		this.dao = dao;
	}

	/**
	 * @see org.openmrs.api.ConceptService#getConceptSetsByConcept(org.openmrs.Concept)
	 */
	public List<ConceptSet> getConceptSetsByConcept(Concept concept) {
		return dao.getConceptSetsByConcept(concept);
	}

	/**
	 * @see org.openmrs.api.ConceptService#getConceptsByConceptSet(org.openmrs.Concept)
	 */
	public List<Concept> getConceptsByConceptSet(Concept c) {
		var alreadySeen = new HashSet<Integer>();
		var ret = new ArrayList<Concept>();
		explodeConceptSetHelper(c, ret, alreadySeen);
		return ret;
	}

	/**
	 * @see org.openmrs.api.ConceptService#getSetsContainingConcept(org.openmrs.Concept)
	 */
	public List<ConceptSet> getSetsContainingConcept(Concept concept) {
		if (concept.getConceptId() == null) {
			return List.of();
		}

		return dao.getSetsContainingConcept(concept);
	}

	/**
	 * @see org.openmrs.api.ConceptService#getConceptSetByUuid(String)
	 */
	public ConceptSet getConceptSetByUuid(String uuid) {
		return dao.getConceptSetByUuid(uuid);
	}

	/**
	 * Utility method used by {@link #getConceptsByConceptSet(Concept)} to recursively expand concept
	 * sets.
	 *
	 * @param concept the concept set to expand
	 * @param ret accumulator for discovered concepts
	 * @param alreadySeen guard against cycles
	 */
	private void explodeConceptSetHelper(Concept concept, Collection<Concept> ret, Collection<Integer> alreadySeen) {
		if (alreadySeen.contains(concept.getConceptId())) {
			return;
		}
		alreadySeen.add(concept.getConceptId());
		List<ConceptSet> cs = getConceptSetsByConcept(concept);
		for (ConceptSet set : cs) {
			Concept c = set.getConcept();
			if (c.getSet()) {
				ret.add(c);
				explodeConceptSetHelper(c, ret, alreadySeen);
			} else {
				ret.add(c);
			}
		}
	}
}
