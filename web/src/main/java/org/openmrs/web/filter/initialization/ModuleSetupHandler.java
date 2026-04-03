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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.IOUtils;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.web.filter.util.ErrorMessageConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles test data import and module installation from a remote server during OpenMRS
 * initialization (testing installation method).
 *
 * @since 2.8.0
 */
class ModuleSetupHandler implements WizardStepHandler {

	private static final Logger log = LoggerFactory.getLogger(ModuleSetupHandler.class);

	/**
	 * Imports test data and modules from a remote OpenMRS server. This is used when the testing
	 * installation method is selected.
	 *
	 * @param wizardModel the wizard configuration model
	 * @param finalDatabaseConnectionString the resolved JDBC connection URL
	 * @param connectionUsername the database username
	 * @param connectionPassword the database password
	 * @param releaseTestingModulePath the URL path for the release testing module
	 * @param callback progress callback for reporting status
	 * @return true if import succeeded, false on error
	 */
	boolean importTestData(InitializationWizardModel wizardModel, String finalDatabaseConnectionString,
	        String connectionUsername, String connectionPassword, String releaseTestingModulePath,
	        ProgressCallback callback) {
		if (!wizardModel.importTestData) {
			return true;
		}

		try {
			callback.setMessage("Importing test data");
			callback.setExecutingTask(WizardTask.IMPORT_TEST_DATA);
			callback.setCompletedPercentage(0);

			try {
				InputStream inData = TestInstallUtil.getResourceInputStream(
				    wizardModel.remoteUrl + releaseTestingModulePath + "generateTestDataSet.form",
				    wizardModel.remoteUsername, wizardModel.remotePassword);

				callback.setCompletedPercentage(40);
				callback.setMessage("Loading imported test data...");
				importTestDataSet(inData, finalDatabaseConnectionString, connectionUsername, connectionPassword,
				    wizardModel.databaseName);
				wizardModel.workLog.add("Imported test data");
				callback.addExecutedTask(WizardTask.IMPORT_TEST_DATA);

				// reset the progress for the next task
				callback.setCompletedPercentage(0);
				callback.setMessage("Importing modules from remote server...");
				callback.setExecutingTask(WizardTask.ADD_MODULES);

				InputStream inModules = TestInstallUtil.getResourceInputStream(
				    wizardModel.remoteUrl + releaseTestingModulePath + "getModules.htm", wizardModel.remoteUsername,
				    wizardModel.remotePassword);

				callback.setCompletedPercentage(90);
				callback.setMessage("Adding imported modules...");
				if (!TestInstallUtil.addZippedTestModules(inModules)) {
					callback.reportError(ErrorMessageConstants.ERROR_DB_UNABLE_TO_ADD_MODULES, null, "");
					return false;
				}

				wizardModel.workLog.add("Added Modules");
				callback.addExecutedTask(WizardTask.ADD_MODULES);

			} catch (APIAuthenticationException e) {
				log.warn("Unable to authenticate as a User with the System Developer role");
				callback.reportError(ErrorMessageConstants.UPDATE_ERROR_UNABLE_AUTHENTICATE, null, "");
				return false;
			}
		} catch (Exception e) {
			callback.reportError(ErrorMessageConstants.ERROR_DB_IMPORT_TEST_DATA, null, e.getMessage());
			log.warn("Error while trying to import test data", e);
			return false;
		}

		return true;
	}

	/**
	 * Imports a test data set from a zipped SQL dump into the database.
	 *
	 * @param in the input stream containing the zipped test data
	 * @param connectionUrl the JDBC connection URL
	 * @param connectionUsername the database username
	 * @param connectionPassword the database password
	 * @param databaseName the target database name
	 * @throws IOException if an I/O error occurs during import
	 */
	void importTestDataSet(InputStream in, String connectionUrl, String connectionUsername, String connectionPassword,
	        String databaseName) throws IOException {
		File tempFile = null;
		FileOutputStream fileOut = null;
		try {
			ZipInputStream zipIn = new ZipInputStream(in);
			zipIn.getNextEntry();

			tempFile = File.createTempFile("testDataSet", "dump");
			fileOut = new FileOutputStream(tempFile);

			IOUtils.copy(zipIn, fileOut);

			fileOut.close();
			zipIn.close();

			// Cater for the stand-alone connection url with has :mxj:
			if (connectionUrl.contains(":mxj:")) {
				connectionUrl = connectionUrl.replace(":mxj:", ":");
			}

			URI uri = URI.create(connectionUrl.substring(5)); // remove 'jdbc:' prefix to conform to the URI format
			String host = uri.getHost();
			int port = uri.getPort();

			TestInstallUtil.addTestData(host, port, databaseName, connectionUsername, connectionPassword,
			    tempFile.getAbsolutePath());
		} finally {
			IOUtils.closeQuietly(in);
			IOUtils.closeQuietly(fileOut);

			if (tempFile != null) {
				tempFile.delete();
			}
		}
	}
}
