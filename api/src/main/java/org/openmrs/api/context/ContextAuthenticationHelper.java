/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.api.context;

import java.util.Locale;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.Role;
import org.openmrs.User;
import org.openmrs.messagesource.MessageSourceService;
import org.openmrs.util.LocaleUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;

/**
 * Static helper that encapsulates authentication and authorization logic extracted from
 * {@link Context}.
 * <p>
 * Every method is package-private and static so that only {@link Context} calls them. Collaborators
 * ({@link UserContext}, {@link ServiceContext}) are received as parameters; this class never
 * touches {@code Context}'s thread-local state directly.
 *
 * @since 2.8.0
 */
final class ContextAuthenticationHelper {

	private static final Logger log = LoggerFactory.getLogger(ContextAuthenticationHelper.class);

	private ContextAuthenticationHelper() {
	}

	// ---- authentication scheme resolution ----

	/**
	 * Resolves the {@link AuthenticationScheme} to use. Looks for a Spring-wired override; falls back
	 * to {@link UsernamePasswordAuthenticationScheme}.
	 */
	static AuthenticationScheme initAuthenticationScheme(ServiceContext serviceContext) {
		AuthenticationScheme scheme = new UsernamePasswordAuthenticationScheme();
		try {
			scheme = serviceContext.getApplicationContext().getBean(AuthenticationScheme.class);
			log.info(
			    "An authentication scheme override was provided. Using this one in place of the OpenMRS default authentication scheme.");
		} catch (NoUniqueBeanDefinitionException e) {
			log.error(
			    "Multiple authentication schemes overrides are being provided, this is currently not supported. Sticking to OpenMRS default authentication scheme.");
		} catch (NoSuchBeanDefinitionException e) {
			log.debug("No authentication scheme override was provided. Sticking to OpenMRS default authentication scheme.");
		} catch (BeansException e) {
			log.error(
			    "Fatal error encountered when injecting the authentication scheme override. Sticking to OpenMRS default authentication scheme.");
		}
		return scheme;
	}

	// ---- authentication ----

	/**
	 * Authenticates with the given credentials against the supplied {@link UserContext}. Daemon threads
	 * are rejected with a log message and receive a pre-built {@link BasicAuthenticated} wrapper.
	 *
	 * @param credentials the credentials to authenticate (may be {@code null})
	 * @param userContext the current thread's user context (may be {@code null} for daemon threads)
	 * @return authentication result
	 * @throws ContextAuthenticationException on authentication failure or {@code null} credentials
	 */
	static Authenticated authenticate(Credentials credentials, UserContext userContext) {
		if (Daemon.isDaemonThread()) {
			log.error("Authentication attempted while operating on a "
			        + "daemon thread, authenticating is not necessary or allowed");
			return new BasicAuthenticated(Daemon.getDaemonThreadUser(),
			        "No auth scheme used by Context - Daemon user is always authenticated.");
		}

		if (credentials == null) {
			throw new ContextAuthenticationException("Context cannot authenticate with null credentials.");
		}

		return userContext.authenticate(credentials);
	}

	/**
	 * Refreshes the authenticated user object from the database.
	 */
	static void refreshAuthenticatedUser(UserContext userContext) {
		log.debug("Refreshing authenticated user");
		userContext.refreshAuthenticatedUser();
	}

	/**
	 * Switches the current session to operate as a different user.
	 */
	static void becomeUser(String systemId, UserContext userContext) {
		log.info("systemId: {}", systemId);
		userContext.becomeUser(systemId);
	}

	/**
	 * Returns the authenticated user from the given user context.
	 */
	static User getAuthenticatedUser(UserContext userContext) {
		return userContext.getAuthenticatedUser();
	}

	/**
	 * Checks whether the given user context holds an authenticated user.
	 */
	static boolean isAuthenticated(UserContext userContext) {
		return userContext.getAuthenticatedUser() != null;
	}

	// ---- logout ----

	/**
	 * Logs out the current user within the given user context.
	 */
	static void logout(UserContext userContext) {
		log.debug("Logging out : {}", userContext.getAuthenticatedUser());
		userContext.logout();
	}

	// ---- authorization / privileges ----

	/**
	 * Returns all roles for the authenticated user in the given context.
	 */
	static Set<Role> getAllRoles(UserContext userContext) throws Exception {
		return userContext.getAllRoles();
	}

	/**
	 * Tests whether the user context holds the specified privilege. Daemon threads are always
	 * considered privileged.
	 */
	static boolean hasPrivilege(String privilege, UserContext userContext) {
		if (Daemon.isDaemonThread()) {
			return true;
		}
		return userContext.hasPrivilege(privilege);
	}

	/**
	 * Throws {@link ContextAuthenticationException} when the caller lacks the required privilege.
	 */
	static void requirePrivilege(String privilege, boolean hasPrivilege, MessageSourceService messageSourceService) {
		if (!hasPrivilege) {
			String errorMessage;
			if (StringUtils.isNotBlank(privilege)) {
				errorMessage = messageSourceService.getMessage("error.privilegesRequired", new Object[] { privilege },
				    Locale.getDefault());
			} else {
				errorMessage = messageSourceService.getMessage("error.privilegesRequiredNoArgs");
			}
			throw new ContextAuthenticationException(errorMessage);
		}
	}

	/**
	 * Adds a single proxy privilege to the user context.
	 */
	static void addProxyPrivilege(String privilege, UserContext userContext) {
		userContext.addProxyPrivilege(privilege);
	}

	/**
	 * Adds multiple proxy privileges to the user context.
	 */
	static void addProxyPrivilege(String[] privileges, UserContext userContext) {
		userContext.addProxyPrivilege(privileges);
	}

	/**
	 * Removes a single proxy privilege from the user context.
	 */
	static void removeProxyPrivilege(String privilege, UserContext userContext) {
		userContext.removeProxyPrivilege(privilege);
	}

	/**
	 * Removes multiple proxy privileges from the user context.
	 */
	static void removeProxyPrivilege(String[] privileges, UserContext userContext) {
		userContext.removeProxyPrivilege(privileges);
	}

	// ---- locale ----

	/**
	 * Sets the locale on the given user context.
	 */
	static void setLocale(Locale locale, UserContext userContext) {
		userContext.setLocale(locale);
	}

	/**
	 * Returns the locale from the given user context, or the system default when no user context is
	 * available (session not open).
	 *
	 * @param userContext the current user context, or {@code null} if no session is open
	 * @return the effective locale
	 */
	static Locale getLocale(UserContext userContext) {
		if (userContext == null) {
			return LocaleUtility.getDefaultLocale();
		}
		return userContext.getLocale();
	}
}
