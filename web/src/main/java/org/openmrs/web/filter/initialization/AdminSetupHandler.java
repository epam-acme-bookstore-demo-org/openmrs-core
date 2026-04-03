/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.web.filter.initialization;

import java.util.Properties;

import org.openmrs.ImplementationId;
import org.openmrs.api.UserService;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.ContextAuthenticationException;
import org.openmrs.api.context.UsernamePasswordCredentials;
import org.openmrs.util.PrivilegeConstants;
import org.openmrs.web.filter.util.ErrorMessageConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.openmrs.util.PrivilegeConstants.GET_GLOBAL_PROPERTIES;

/**
 * Handles admin user creation, password changes, implementation ID configuration, and PostgreSQL
 * sequence updates during OpenMRS initialization.
 *
 * @since 2.8.0
 */
class AdminSetupHandler implements WizardStepHandler {

	private static final Logger log = LoggerFactory.getLogger(AdminSetupHandler.class);

	/**
	 * Sets the implementation ID in the OpenMRS administration service if configured in the wizard
	 * model. Requires an active OpenMRS session with appropriate proxy privileges.
	 *
	 * @param wizardModel the wizard configuration model
	 * @param callback progress callback for reporting errors
	 * @return true if the implementation ID was set successfully (or was not configured), false on
	 *         error
	 */
	boolean setImplementationId(InitializationWizardModel wizardModel, ProgressCallback callback) {
		if ("".equals(wizardModel.implementationId)) {
			return true;
		}

		try {
			Context.addProxyPrivilege(PrivilegeConstants.MANAGE_GLOBAL_PROPERTIES);
			Context.addProxyPrivilege(GET_GLOBAL_PROPERTIES);
			Context.addProxyPrivilege(PrivilegeConstants.MANAGE_CONCEPT_SOURCES);
			Context.addProxyPrivilege(PrivilegeConstants.GET_CONCEPT_SOURCES);
			Context.addProxyPrivilege(PrivilegeConstants.MANAGE_IMPLEMENTATION_ID);

			ImplementationId implId = new ImplementationId();
			implId.setName(wizardModel.implementationIdName);
			implId.setImplementationId(wizardModel.implementationId);
			implId.setPassphrase(wizardModel.implementationIdPassPhrase);
			implId.setDescription(wizardModel.implementationIdDescription);

			Context.getAdministrationService().setImplementationId(implId);
			return true;
		} catch (Exception e) {
			callback.reportError(ErrorMessageConstants.ERROR_SET_INPL_ID, null, e.getMessage());
			log.warn("Implementation ID could not be set.", e);
			return false;
		} finally {
			Context.removeProxyPrivilege(PrivilegeConstants.MANAGE_GLOBAL_PROPERTIES);
			Context.removeProxyPrivilege(GET_GLOBAL_PROPERTIES);
			Context.removeProxyPrivilege(PrivilegeConstants.MANAGE_CONCEPT_SOURCES);
			Context.removeProxyPrivilege(PrivilegeConstants.GET_CONCEPT_SOURCES);
			Context.removeProxyPrivilege(PrivilegeConstants.MANAGE_IMPLEMENTATION_ID);
		}
	}

	/**
	 * Changes the admin user password from the default "test" to the value specified in the wizard
	 * model. Only applies when tables were freshly created. Requires an active OpenMRS session.
	 *
	 * @param wizardModel the wizard configuration model
	 * @param callback progress callback for reporting errors
	 * @return true if the password was changed successfully (or change was not needed), false on error
	 */
	boolean changeAdminPassword(InitializationWizardModel wizardModel, ProgressCallback callback) {
		if (!wizardModel.createTables) {
			return true;
		}

		try {
			Context.addProxyPrivilege(GET_GLOBAL_PROPERTIES);
			Context.authenticate(new UsernamePasswordCredentials("admin", "test"));

			Properties props = Context.getRuntimeProperties();
			String initValue = props.getProperty(UserService.ADMIN_PASSWORD_LOCKED_PROPERTY);
			props.setProperty(UserService.ADMIN_PASSWORD_LOCKED_PROPERTY, "false");
			Context.setRuntimeProperties(props);

			Context.getUserService().changePassword("test", wizardModel.adminUserPassword);

			if (initValue == null) {
				props.remove(UserService.ADMIN_PASSWORD_LOCKED_PROPERTY);
			} else {
				props.setProperty(UserService.ADMIN_PASSWORD_LOCKED_PROPERTY, initValue);
			}
			Context.setRuntimeProperties(props);
			Context.logout();
		} catch (ContextAuthenticationException ex) {
			log.info("No need to change admin password.", ex);
		} finally {
			Context.removeProxyPrivilege(GET_GLOBAL_PROPERTIES);
		}

		return true;
	}

	/**
	 * Updates PostgreSQL sequences after insertion of core data. This is a no-op for non-PostgreSQL
	 * databases. Requires an active OpenMRS session.
	 *
	 * @param callback progress callback for reporting errors
	 * @return true if the update succeeded, false on error
	 */
	boolean updatePostgresSequence(ProgressCallback callback) {
		try {
			Context.getAdministrationService().updatePostgresSequence();
			return true;
		} catch (Exception e) {
			log.warn("Not able to update PostgreSQL sequence. Startup failed for PostgreSQL", e);
			callback.reportError(ErrorMessageConstants.ERROR_COMPLETE_STARTUP, null, e.getMessage());
			return false;
		}
	}
}
