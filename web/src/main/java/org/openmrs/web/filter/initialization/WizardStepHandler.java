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

/**
 * Common interface for all wizard step handlers used during OpenMRS initialization. Each handler
 * encapsulates a distinct phase of the installation wizard, keeping the
 * {@link InitializationFilter} focused on servlet lifecycle and step routing.
 *
 * @since 2.8.0
 */
public interface WizardStepHandler {

	/**
	 * Callback interface for reporting installation progress during wizard execution. Implementations
	 * allow handlers to communicate status updates, task transitions, and error conditions back to the
	 * installation orchestrator.
	 */
	interface ProgressCallback {

		/**
		 * Reports a progress message for the current installation step.
		 *
		 * @param message the progress message
		 */
		void setMessage(String message);

		/**
		 * Sets the currently executing wizard task.
		 *
		 * @param task the task being executed, or {@code null} when no task is active
		 */
		void setExecutingTask(WizardTask task);

		/**
		 * Records a wizard task as completed.
		 *
		 * @param task the completed task
		 */
		void addExecutedTask(WizardTask task);

		/**
		 * Updates the completion percentage for the current task.
		 *
		 * @param percentage completion percentage (0-100)
		 */
		void setCompletedPercentage(int percentage);

		/**
		 * Reports an error that occurred during installation.
		 *
		 * @param error the error message key
		 * @param errorPage the page to redirect to on error
		 * @param params optional parameters for the error message
		 */
		void reportError(String error, String errorPage, Object... params);

		/**
		 * @return true if any errors have been reported
		 */
		boolean hasErrors();
	}
}
