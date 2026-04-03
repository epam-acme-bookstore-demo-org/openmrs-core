/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.parameter;

import java.util.List;

import org.openmrs.Role;

/**
 * An immutable search parameter object for users. A convenience builder for constructing
 * instances is available via {@link #builder()}.
 *
 * @since 3.0.0
 * @see org.openmrs.api.UserService#getUsers(UserSearchCriteria)
 */
public record UserSearchCriteria(String name,List<Role>roles,boolean includeRetired,Integer start,Integer length){

/**
 * @return a new {@link Builder} instance
 */
public static Builder builder(){return new Builder();}

/**
 * A convenience builder for {@link UserSearchCriteria}.
 */
public static class Builder {

	private String name;

	private List<Role> roles;

	private boolean includeRetired;

	private Integer start;

	private Integer length;

	public Builder name(String name) {
		this.name = name;
		return this;
	}

	public Builder roles(List<Role> roles) {
		this.roles = roles;
		return this;
	}

	public Builder includeRetired(boolean includeRetired) {
		this.includeRetired = includeRetired;
		return this;
	}

	public Builder start(Integer start) {
		this.start = start;
		return this;
	}

	public Builder length(Integer length) {
		this.length = length;
		return this;
	}

	/**
	 * Create a {@link UserSearchCriteria} with the properties of this builder instance.
	 *
	 * @return a new search criteria instance
	 */
	public UserSearchCriteria build() {
		return new UserSearchCriteria(name, roles, includeRetired, start, length);
	}
}}
