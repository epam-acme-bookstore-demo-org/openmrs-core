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

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.openmrs.api.context.Context;
import org.openmrs.liquibase.ChangeLogDetective;
import org.openmrs.liquibase.ChangeLogVersionFinder;
import org.openmrs.liquibase.ChangeSetExecutorCallback;
import org.openmrs.util.DatabaseUpdater;
import org.openmrs.util.DatabaseUpdaterLiquibaseProvider;
import org.openmrs.util.DatabaseUtil;
import org.openmrs.util.OpenmrsConstants;
import org.openmrs.util.OpenmrsUtil;
import org.openmrs.util.Security;
import org.openmrs.web.filter.util.ErrorMessageConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import liquibase.changelog.ChangeSet;

/**
 * Handles all database-related operations during OpenMRS initialization: schema creation, database
 * user management, connection verification, table creation via Liquibase, and database updates to
 * the latest version.
 *
 * @since 2.8.0
 */
class DatabaseSetupHandler implements WizardStepHandler {

	private static final Logger log = LoggerFactory.getLogger(DatabaseSetupHandler.class);

	private static final String DATABASE_POSTGRESQL = "postgresql";

	private static final String DATABASE_MYSQL = "mysql";

	private static final String DATABASE_MARIADB = "mariadb";

	private static final String DATABASE_H2 = "h2";

	/**
	 * Creates the database schema if the wizard model indicates no existing database.
	 *
	 * @param wizardModel the wizard configuration model
	 * @param callback progress callback for reporting status
	 * @param errors the error map to populate on SQL failure
	 * @return true if schema creation succeeded (or was not needed), false on error
	 */
	boolean createSchema(InitializationWizardModel wizardModel, ProgressCallback callback, Map<String, Object[]> errors) {
		if (wizardModel.hasCurrentOpenmrsDatabase) {
			return true;
		}

		callback.setMessage("Create database");
		callback.setExecutingTask(WizardTask.CREATE_SCHEMA);

		String sql;
		if (isCurrentDatabase(wizardModel, DATABASE_MYSQL)) {
			sql = "create database if not exists `?` default character set utf8";
		} else if (isCurrentDatabase(wizardModel, DATABASE_POSTGRESQL)) {
			sql = "create database `?` encoding 'utf8'";
		} else if (isCurrentDatabase(wizardModel, DATABASE_H2)) {
			sql = null;
		} else {
			sql = "create database `?`";
		}

		int result;
		if (sql != null) {
			result = executeStatement(false, wizardModel.createDatabaseUsername, wizardModel.createDatabasePassword, sql,
			    wizardModel, errors, wizardModel.databaseName);
		} else {
			result = 1;
		}

		if (result < 0) {
			callback.reportError(ErrorMessageConstants.ERROR_DB_CREATE_NEW, null);
			return false;
		}

		wizardModel.workLog.add("Created database " + wizardModel.databaseName);
		callback.addExecutedTask(WizardTask.CREATE_SCHEMA);
		return true;
	}

	/**
	 * Creates a database user with a random password and grants appropriate privileges.
	 *
	 * @param wizardModel the wizard configuration model
	 * @param callback progress callback for reporting status
	 * @return a two-element array {@code [username, password]} on success, or {@code null} on error
	 */
	String[] createDatabaseUser(InitializationWizardModel wizardModel, ProgressCallback callback) {
		if (!wizardModel.createDatabaseUser) {
			return new String[] { wizardModel.currentDatabaseUsername, wizardModel.currentDatabasePassword };
		}

		callback.setMessage("Create database user");
		callback.setExecutingTask(WizardTask.CREATE_DB_USER);

		String connectionUsername = wizardModel.databaseName + "_user";
		if (connectionUsername.length() > 16) {
			connectionUsername = wizardModel.databaseName.substring(0, 11) + "_user";
		}

		// generate random password from this subset of alphabet
		// intentionally left out these characters: ufsb$() to prevent certain words forming randomly
		String chars = "acdeghijklmnopqrtvwxyzACDEGHIJKLMNOPQRTVWXYZ0123456789.|~@#^&";
		SecureRandom r = new SecureRandom();
		StringBuilder connectionPassword = new StringBuilder();
		for (int x = 0; x < 12; x++) {
			connectionPassword.append(chars.charAt(r.nextInt(chars.length())));
		}

		// connect via jdbc with root user and create an openmrs user
		String host = "'%'";
		if (wizardModel.databaseConnection.contains("localhost") || wizardModel.databaseConnection.contains("127.0.0.1")) {
			host = "'localhost'";
		}

		String sql = "";
		if (isCurrentDatabase(wizardModel, DATABASE_MYSQL)) {
			sql = "drop user '?'@" + host;
		} else if (isCurrentDatabase(wizardModel, DATABASE_POSTGRESQL)) {
			sql = "drop user `?`";
		}

		executeStatement(true, wizardModel.createUserUsername, wizardModel.createUserPassword, sql, wizardModel, null,
		    connectionUsername);

		if (isCurrentDatabase(wizardModel, DATABASE_MYSQL)) {
			sql = "create user '?'@" + host + " identified by '?'";
		} else if (isCurrentDatabase(wizardModel, DATABASE_POSTGRESQL)) {
			sql = "create user `?` with password '?'";
		}

		if (-1 != executeStatement(false, wizardModel.createUserUsername, wizardModel.createUserPassword, sql, wizardModel,
		    null, connectionUsername, connectionPassword.toString())) {
			wizardModel.workLog.add("Created user " + connectionUsername);
		} else {
			callback.reportError(ErrorMessageConstants.ERROR_DB_CREATE_DB_USER, null);
			return null;
		}

		// grant the roles
		int result = 1;
		if (isCurrentDatabase(wizardModel, DATABASE_MYSQL)) {
			sql = "GRANT ALL ON `?`.* TO '?'@" + host;
			result = executeStatement(false, wizardModel.createUserUsername, wizardModel.createUserPassword, sql,
			    wizardModel, null, wizardModel.databaseName, connectionUsername);
		} else if (isCurrentDatabase(wizardModel, DATABASE_POSTGRESQL)) {
			sql = "ALTER USER `?` WITH SUPERUSER";
			result = executeStatement(false, wizardModel.createUserUsername, wizardModel.createUserPassword, sql,
			    wizardModel, null, connectionUsername);
		}

		if (result < 0) {
			callback.reportError(ErrorMessageConstants.ERROR_DB_GRANT_PRIV, null);
			return null;
		}

		wizardModel.workLog
		        .add("Granted user " + connectionUsername + " all privileges to database " + wizardModel.databaseName);
		callback.addExecutedTask(WizardTask.CREATE_DB_USER);

		return new String[] { connectionUsername, connectionPassword.toString() };
	}

	/**
	 * Builds the final database connection string from the wizard model, replacing placeholders.
	 *
	 * @param wizardModel the wizard configuration model
	 * @return the resolved database connection URL
	 */
	String buildFinalConnectionString(InitializationWizardModel wizardModel) {
		String result = wizardModel.databaseConnection.replace("@DBNAME@", wizardModel.databaseName);
		return result.replace("@APPLICATIONDATADIR@", OpenmrsUtil.getApplicationDataDirectory().replace("\\", "/"));
	}

	/**
	 * Verifies that a database connection can be established with the given credentials.
	 *
	 * @param connectionUsername the database username
	 * @param connectionPassword the database password
	 * @param databaseConnectionFinalUrl the full JDBC connection URL
	 * @param loadedDriverString the driver class name to load
	 * @param errors the error map to populate on failure
	 * @return true if the connection succeeds, false otherwise
	 */
	boolean verifyConnection(String connectionUsername, String connectionPassword, String databaseConnectionFinalUrl,
	        String loadedDriverString, Map<String, Object[]> errors) {
		try {
			DatabaseUtil.loadDatabaseDriver(databaseConnectionFinalUrl, loadedDriverString);
			try (Connection ignored = DriverManager.getConnection(databaseConnectionFinalUrl, connectionUsername,
			    connectionPassword)) {
				return true;
			}
		} catch (Exception e) {
			errors.put("User account " + connectionUsername + " does not work. " + e.getMessage()
			        + " See the error log for more details",
			    null);
			log.warn("Error while checking the connection user account", e);
			return false;
		}
	}

	/**
	 * Builds the runtime properties from the wizard model and connection credentials.
	 *
	 * @param wizardModel the wizard configuration model
	 * @param finalDatabaseConnectionString the resolved JDBC URL
	 * @param connectionUsername the database username
	 * @param connectionPassword the database password
	 * @return the assembled runtime properties
	 */
	Properties buildRuntimeProperties(InitializationWizardModel wizardModel, String finalDatabaseConnectionString,
	        String connectionUsername, String connectionPassword) {
		Properties runtimeProperties = new Properties();

		runtimeProperties.put("connection.url", finalDatabaseConnectionString);
		runtimeProperties.put("connection.username", connectionUsername);
		runtimeProperties.put("connection.password", connectionPassword);
		if (StringUtils.hasText(wizardModel.databaseDriver)) {
			runtimeProperties.put("connection.driver_class", wizardModel.databaseDriver);
		}
		runtimeProperties.put("module.allow_web_admin", "" + wizardModel.moduleWebAdmin);
		runtimeProperties.put("auto_update_database", "" + wizardModel.autoUpdateDatabase);
		final Base64.Encoder base64 = Base64.getEncoder();
		runtimeProperties.put(OpenmrsConstants.ENCRYPTION_VECTOR_RUNTIME_PROPERTY,
		    new String(base64.encode(Security.generateNewInitVector()), StandardCharsets.UTF_8));
		runtimeProperties.put(OpenmrsConstants.ENCRYPTION_KEY_RUNTIME_PROPERTY,
		    new String(base64.encode(Security.generateNewSecretKey()), StandardCharsets.UTF_8));

		runtimeProperties.putAll(wizardModel.additionalPropertiesFromInstallationScript);

		Properties properties = Context.getRuntimeProperties();
		properties.putAll(runtimeProperties);
		runtimeProperties = properties;

		Context.setRuntimeProperties(runtimeProperties);

		return runtimeProperties;
	}

	/**
	 * Creates database tables and adds core data using Liquibase.
	 *
	 * @param wizardModel the wizard configuration model
	 * @param callback progress callback for reporting status
	 * @return true if tables were created successfully (or creation was not needed), false on error
	 */
	boolean createTables(InitializationWizardModel wizardModel, ProgressCallback callback) {
		if (!wizardModel.createTables) {
			return true;
		}

		log.debug("Creating tables");
		var changeLogVersionFinder = new ChangeLogVersionFinder();

		try {
			String liquibaseSchemaFileName = changeLogVersionFinder.getLatestSchemaSnapshotFilename().get();
			String liquibaseCoreDataFileName = changeLogVersionFinder.getLatestCoreDataSnapshotFilename().get();

			callback.setMessage("Executing " + liquibaseSchemaFileName);
			callback.setExecutingTask(WizardTask.CREATE_TABLES);

			log.debug("executing Liquibase file '{}' ", liquibaseSchemaFileName);

			DatabaseUpdater.executeChangelog(liquibaseSchemaFileName,
			    new PrintingChangeSetExecutorCallback("OpenMRS schema file", callback));
			callback.addExecutedTask(WizardTask.CREATE_TABLES);

			// reset for this task
			callback.setCompletedPercentage(0);
			callback.setExecutingTask(WizardTask.ADD_CORE_DATA);

			log.debug("executing Liquibase file '{}' ", liquibaseCoreDataFileName);

			DatabaseUpdater.executeChangelog(liquibaseCoreDataFileName,
			    new PrintingChangeSetExecutorCallback("OpenMRS core data file", callback));
			wizardModel.workLog.add("Created database tables and added core data");
			callback.addExecutedTask(WizardTask.ADD_CORE_DATA);

			return true;
		} catch (Exception e) {
			callback.reportError(ErrorMessageConstants.ERROR_DB_CREATE_TABLES_OR_ADD_DEMO_DATA, null, e.getMessage());
			log.warn("Error while trying to create tables and demo data", e);
			return false;
		}
	}

	/**
	 * Updates the database to the latest version by running pending Liquibase changelogs.
	 *
	 * @param wizardModel the wizard configuration model
	 * @param callback progress callback for reporting status
	 * @return true if the update succeeded, false on error
	 */
	boolean updateToLatest(InitializationWizardModel wizardModel, ProgressCallback callback) {
		try {
			callback.setMessage("Updating the database to the latest version");
			callback.setCompletedPercentage(0);
			callback.setExecutingTask(WizardTask.UPDATE_TO_LATEST);

			ChangeLogDetective changeLogDetective = ChangeLogDetective.getInstance();
			var changeLogVersionFinder = new ChangeLogVersionFinder();

			String version;
			if (wizardModel.createTables) {
				version = changeLogVersionFinder.getLatestSnapshotVersion().get();
			} else {
				version = changeLogDetective.getInitialLiquibaseSnapshotVersion(DatabaseUpdater.CONTEXT,
				    new DatabaseUpdaterLiquibaseProvider());
			}

			log.debug("updating the database with versions of liquibase-update-to-latest files greater than '{}'", version);

			List<String> changelogs = changeLogVersionFinder
			        .getUpdateFileNames(changeLogVersionFinder.getUpdateVersionsGreaterThan(version));

			for (String changelog : changelogs) {
				log.debug("applying Liquibase changelog '{}'", changelog);

				DatabaseUpdater.executeChangelog(changelog,
				    new PrintingChangeSetExecutorCallback("executing Liquibase changelog " + changelog, callback));
			}
			callback.addExecutedTask(WizardTask.UPDATE_TO_LATEST);
			return true;
		} catch (Exception e) {
			callback.reportError(ErrorMessageConstants.ERROR_DB_UPDATE_TO_LATEST, null, e.getMessage());
			log.warn("Error while trying to update to the latest database version", e);
			return false;
		}
	}

	/**
	 * Executes a SQL statement against the database configured in the wizard model.
	 *
	 * @param silent if true, suppresses error logging and error map entries
	 * @param user the database username
	 * @param pw the database password
	 * @param sql the SQL statement with {@code ?} placeholders
	 * @param wizardModel the wizard configuration model
	 * @param errors optional error map to populate on failure (may be {@code null} when silent)
	 * @param args the values to substitute for {@code ?} placeholders
	 * @return the result of {@link Statement#executeUpdate(String)}, or -1 on error
	 */
	int executeStatement(boolean silent, String user, String pw, String sql, InitializationWizardModel wizardModel,
	        Map<String, Object[]> errors, String... args) {

		Connection connection = null;
		Statement statement = null;
		try {
			String replacedSql = sql;

			if (isCurrentDatabase(wizardModel, DATABASE_MYSQL)) {
				Class.forName("com.mysql.cj.jdbc.Driver").newInstance();
			} else if (isCurrentDatabase(wizardModel, DATABASE_MARIADB)) {
				Class.forName("org.mariadb.jdbc.Driver").newInstance();
			} else if (isCurrentDatabase(wizardModel, DATABASE_POSTGRESQL)) {
				Class.forName("org.postgresql.Driver").newInstance();
				replacedSql = replacedSql.replaceAll("`", "\"");
			} else {
				replacedSql = replacedSql.replaceAll("`", "\"");
			}

			String tempDatabaseConnection;
			if (sql.contains("create database")) {
				tempDatabaseConnection = wizardModel.databaseConnection.replace("@DBNAME@", "");
			} else {
				tempDatabaseConnection = wizardModel.databaseConnection.replace("@DBNAME@", wizardModel.databaseName);
			}

			connection = DriverManager.getConnection(tempDatabaseConnection, user, pw);

			for (String arg : args) {
				arg = arg.replace(";", "&#094");
				replacedSql = replacedSql.replaceFirst("\\?", arg);
			}

			statement = connection.createStatement();

			return statement.executeUpdate(replacedSql);

		} catch (SQLException sqlex) {
			if (!silent) {
				log.warn("error executing sql: " + sql, sqlex);
				if (errors != null) {
					errors.put("Error executing sql: " + sql + " - " + sqlex.getMessage(), null);
				}
			}
		} catch (InstantiationException | ClassNotFoundException | IllegalAccessException e) {
			log.error("Error generated", e);
		} finally {
			try {
				if (statement != null) {
					statement.close();
				}
			} catch (SQLException e) {
				log.warn("Error while closing statement");
			}
			try {
				if (connection != null) {
					connection.close();
				}
			} catch (Exception e) {
				log.warn("Error while closing connection", e);
			}
		}

		return -1;
	}

	/**
	 * Checks whether the wizard model's database connection string contains the given database type.
	 */
	private boolean isCurrentDatabase(InitializationWizardModel wizardModel, String database) {
		return wizardModel.databaseConnection.contains(database);
	}

	/**
	 * A callback that prints Liquibase changeset execution progress through the
	 * {@link WizardStepHandler.ProgressCallback}.
	 */
	private static class PrintingChangeSetExecutorCallback implements ChangeSetExecutorCallback {

		private int i = 1;

		private final String message;

		private final ProgressCallback callback;

		PrintingChangeSetExecutorCallback(String message, ProgressCallback callback) {
			this.message = message;
			this.callback = callback;
		}

		@Override
		public void executing(ChangeSet changeSet, int numChangeSetsToRun) {
			callback.setMessage(message + " (" + i++ + "/" + numChangeSetsToRun + "): Author: " + changeSet.getAuthor()
			        + " Comments: " + changeSet.getComments() + " Description: " + changeSet.getDescription());
			float numChangeSetsToRunFloat = (float) numChangeSetsToRun;
			float j = (float) i;
			callback.setCompletedPercentage(Math.round(j * 100 / numChangeSetsToRunFloat));
		}
	}
}
