/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import org.openmrs.api.AdministrationService;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.Daemon;
import org.openmrs.util.CycleException;
import org.openmrs.util.OpenmrsConstants;
import org.openmrs.util.PrivilegeConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.AbstractRefreshableApplicationContext;
import org.springframework.util.StringUtils;

/**
 * Handles module startup logic including starting individual modules and batch startup.
 * <p>
 * This is an internal class extracted from {@link ModuleFactory} and should not be used directly.
 * All public access should go through {@link ModuleFactory}.
 */
final class ModuleStarter {

	private ModuleStarter() {
	}

	private static final Logger log = LoggerFactory.getLogger(ModuleStarter.class);

	/**
	 * Try to start all of the loaded modules that have the global property <i>moduleId</i>.started is
	 * set to "true" or the property does not exist. Otherwise, leave it as only "loaded"<br>
	 * <br>
	 * Modules that are already started will be skipped.
	 */
	static void startModules() {

		// loop over and try starting each of the loaded modules
		if (!ModuleFactory.getLoadedModules().isEmpty()) {

			List<Module> modules = getModulesThatShouldStart();

			try {
				modules = ModuleDependencyResolver.getModulesInStartupOrder(modules);
			} catch (CycleException ex) {
				String message = getCyclicDependenciesMessage(ex.getMessage());
				log.error(message, ex);
				notifySuperUsersAboutCyclicDependencies(ex);
				modules = (List<Module>) ex.getExtraData();
			}

			// try and start the modules that should be started
			for (Module mod : modules) {

				if (mod.isStarted()) {
					// skip over modules that are already started
					continue;
				}

				// Skip module if required ones are not started
				if (!ModuleDependencyResolver.requiredModulesStarted(mod)) {
					String message = getFailedToStartModuleMessage(mod);
					log.error(message);
					mod.setStartupErrorMessage(message);
					notifySuperUsersAboutModuleFailure(mod);
					continue;
				}

				try {
					log.debug("starting module: {}", mod.getModuleId());
					startModule(mod);
				} catch (Exception e) {
					log.error("Error while starting module: " + mod.getName(), e);
					mod.setStartupErrorMessage("Error while starting module", e);
					notifySuperUsersAboutModuleFailure(mod);
				}
			}
		}
	}

	/**
	 * Obtain the list of modules that should be started
	 *
	 * @return list of modules
	 */
	private static List<Module> getModulesThatShouldStart() {
		var modules = new ArrayList<Module>();

		AdministrationService adminService = Context.getAdministrationService();

		for (Module mod : ModuleFactory.getLoadedModules()) {

			String key = mod.getModuleId() + ".started";
			String startedProp = adminService.getGlobalProperty(key, null);
			String mandatoryProp = adminService.getGlobalProperty(mod.getModuleId() + ".mandatory", null);

			// if a 'moduleid.started' property doesn't exist, start the module anyway
			// as this is probably the first time they are loading it
			if (startedProp == null || "true".equals(startedProp) || "true".equalsIgnoreCase(mandatoryProp)
			        || mod.isMandatory()) {
				modules.add(mod);
			}
		}
		return modules;
	}

	/**
	 * @see ModuleFactory#startModule(Module)
	 */
	static Module startModule(Module module) throws ModuleException {
		return startModule(module, false, null);
	}

	/**
	 * Runs through extensionPoints and then calls {@link BaseModuleActivator#willStart()} on the
	 * Module's activator. This method is run in a new thread and is authenticated as the Daemon user.
	 * If a non null application context is passed in, it gets refreshed to make the module's services
	 * available
	 *
	 * @param module Module to start
	 * @param isOpenmrsStartup Specifies whether this module is being started at application startup or
	 *            not, this argument is ignored if a null application context is passed in
	 * @param applicationContext the spring application context instance to refresh
	 * @throws ModuleException if the module throws any kind of error at startup or in an activator
	 * @see #startModuleInternal(Module, boolean, AbstractRefreshableApplicationContext)
	 * @see Daemon#startModule(Module, boolean, AbstractRefreshableApplicationContext)
	 */
	static Module startModule(Module module, boolean isOpenmrsStartup,
	        AbstractRefreshableApplicationContext applicationContext) throws ModuleException {

		if (!ModuleDependencyResolver.requiredModulesStarted(module)) {
			int missingModules = 0;

			for (String packageName : module.getRequiredModulesMap().keySet()) {
				Module mod = ModuleFactory.getModuleByPackage(packageName);

				// mod not installed
				if (mod == null) {
					missingModules++;
					continue;
				}

				if (!mod.isStarted()) {
					startModule(mod);
				}
			}

			if (missingModules > 0) {
				String message = getFailedToStartModuleMessage(module);
				log.error(message);
				module.setStartupErrorMessage(message);
				notifySuperUsersAboutModuleFailure(module);
				// instead of return null, i realized that Daemon.startModule() always returns a Module
				// object,irrespective of whether the startup succeeded
				return module;
			}
		}
		return Daemon.startModule(module, isOpenmrsStartup, applicationContext);
	}

	/**
	 * @see ModuleFactory#startModuleInternal(Module)
	 */
	static Module startModuleInternal(Module module) throws ModuleException {
		return startModuleInternal(module, false, null);
	}

	/**
	 * This method should not be called directly.<br>
	 * <br>
	 * The {@link ModuleFactory#startModule(Module)} (and hence {@link Daemon#startModule(Module)})
	 * calls this method in a new Thread and is authenticated as the {@link Daemon} user<br>
	 * <br>
	 * Runs through extensionPoints and then calls {@link BaseModuleActivator#willStart()} on the
	 * Module's activator. <br>
	 * <br>
	 * If a non null application context is passed in, it gets refreshed to make the module's services
	 * available
	 *
	 * @param module Module to start
	 * @param isOpenmrsStartup Specifies whether this module is being started at application startup or
	 *            not, this argument is ignored if a null application context is passed in
	 * @param applicationContext the spring application context instance to refresh
	 */
	static Module startModuleInternal(Module module, boolean isOpenmrsStartup,
	        AbstractRefreshableApplicationContext applicationContext) throws ModuleException {

		if (module != null) {
			String moduleId = module.getModuleId();

			try {

				// check to be sure this module can run with our current version
				// of OpenMRS code
				String requireVersion = module.getRequireOpenmrsVersion();
				ModuleUtil.checkRequiredVersion(OpenmrsConstants.OPENMRS_VERSION_SHORT, requireVersion);

				// check for required modules
				if (!ModuleDependencyResolver.requiredModulesStarted(module)) {
					throw new ModuleException(getFailedToStartModuleMessage(module));
				}

				// fire up the classloader for this module
				log.debug("Prepare module classloader: {}", module.getModuleId());
				ModuleClassLoader moduleClassLoader = new ModuleClassLoader(module, ModuleFactory.class.getClassLoader());
				ModuleFactory.getModuleClassLoaderMap().put(module, moduleClassLoader);
				registerProvidedPackages(moduleClassLoader);

				// don't load the advice objects into the Context
				// At startup, the spring context isn't refreshed until all modules
				// have been loaded.  This causes errors if called here during a
				// module's startup if one of these advice points is on another
				// module because that other module's service won't have been loaded
				// into spring yet.  All advice for all modules must be reloaded
				// a spring context refresh anyway

				// map extension point to a list of extensions for this module only
				log.debug("Prepare module extensions: {}", module.getModuleId());
				Map<String, List<Extension>> moduleExtensionMap = new HashMap<>();
				for (Extension ext : module.getExtensions()) {

					String extId = ext.getExtensionId();
					List<Extension> tmpExtensions = moduleExtensionMap.computeIfAbsent(extId, k -> new ArrayList<>());

					tmpExtensions.add(ext);
				}

				// Sort this module's extensions, and merge them into the full extensions map
				Comparator<Extension> sortOrder = (e1, e2) -> Integer.valueOf(e1.getOrder()).compareTo(e2.getOrder());
				for (Map.Entry<String, List<Extension>> moduleExtensionEntry : moduleExtensionMap.entrySet()) {
					// Sort this module's extensions for current extension point
					List<Extension> sortedModuleExtensions = moduleExtensionEntry.getValue();
					sortedModuleExtensions.sort(sortOrder);

					// Get existing extensions, and append the ones from the new module
					List<Extension> extensions = ModuleFactory.getExtensionMap()
					        .computeIfAbsent(moduleExtensionEntry.getKey(), k -> new ArrayList<>());
					for (Extension ext : sortedModuleExtensions) {
						log.debug("Adding to mapping ext: " + ext.getExtensionId() + " ext.class: " + ext.getClass());
						extensions.add(ext);
					}
				}

				// run the module's sql update script
				// This and the property updates are the only things that can't
				// be undone at startup, so put these calls after any other
				// calls that might hinder startup
				log.debug("Run module sql update script: {}", module.getModuleId());
				SortedMap<String, String> diffs = SqlDiffFileParser.getSqlDiffs(module);

				try {
					// this method must check and run queries against the database.
					// to do this, it must be "authenticated".  Give the current
					// "user" the proxy privilege so this can be done. ("user" might
					// be nobody because this is being run at startup)
					Context.addProxyPrivilege("");

					for (Map.Entry<String, String> entry : diffs.entrySet()) {
						String version = entry.getKey();
						String sql = entry.getValue();
						if (StringUtils.hasText(sql)) {
							ModuleFactory.runDiff(module, version, sql);
						}
					}
				} finally {
					// take the "authenticated" privilege away from the current "user"
					Context.removeProxyPrivilege("");
				}

				// run module's optional liquibase.xml immediately after sqldiff.xml
				if (Context.getAdministrationService().isModuleSetupOnVersionChangeNeeded(module.getModuleId())) {
					log.info("Module {} changed, running setup.", module.getModuleId());
					Context.getAdministrationService().runModuleSetupOnVersionChange(module);
				}

				// effectively mark this module as started successfully
				ModuleFactory.getStartedModulesMap().put(moduleId, module);

				ModuleFactory.actualStartupOrder.add(moduleId);

				try {
					// save the state of this module for future restarts
					ModuleFactory.saveGlobalProperty(moduleId + ".started", "true",
					    ModuleFactory.getGlobalPropertyStartedDescription(moduleId));

					// save the mandatory status
					ModuleFactory.saveGlobalProperty(moduleId + ".mandatory", String.valueOf(module.isMandatory()),
					    getGlobalPropertyMandatoryModuleDescription(moduleId));
				} catch (Exception e) {
					// pass over errors because this doesn't really concern startup
					// passing over this also allows for multiple of the same-named modules
					// to be loaded in junit tests that are run within one session
					log.debug("Got an error when trying to set the global property on module startup", e);
				}

				// (this must be done after putting the module in the started
				// list)
				// if this module defined any privileges or global properties,
				// make sure they are added to the database
				// (Unfortunately, placing the call here will duplicate work
				// done at initial app startup)
				if (!module.getPrivileges().isEmpty() || !module.getGlobalProperties().isEmpty()) {
					log.debug("Updating core dataset");
					Context.checkCoreDataset();
					// checkCoreDataset() currently doesn't throw an error. If
					// it did, it needs to be
					// caught and the module needs to be stopped and given a
					// startup error
				}

				// should be near the bottom so the module has all of its stuff
				// set up for it already.
				log.debug("Run module willStart: {}", module.getModuleId());
				try {
					if (module.getModuleActivator() != null) {
						// if extends BaseModuleActivator
						module.getModuleActivator().willStart();
					}
				} catch (ModuleException e) {
					// just rethrow module exceptions. This should be used for a
					// module marking that it had trouble starting
					throw e;
				} catch (Exception e) {
					throw new ModuleException("Error while calling module's Activator.startup()/willStart() method", e);
				}

				// erase any previous startup error
				module.clearStartupError();
			} catch (Exception e) {
				log.error("Error while trying to start module: {}", moduleId, e);
				module.setStartupErrorMessage("Error while trying to start module", e);
				notifySuperUsersAboutModuleFailure(module);
				// undo all of the actions in startup
				try {
					boolean skipOverStartedProperty = false;

					if (e instanceof ModuleMustStartException) {
						skipOverStartedProperty = true;
					}

					ModuleStopper.stopModule(module, skipOverStartedProperty, true);
				} catch (Exception e2) {
					// this will probably occur about the same place as the
					// error in startup
					log.debug("Error while stopping module: {}", moduleId, e2);
				}
			}

		}

		if (applicationContext != null) {
			log.debug("Run module refresh application context: {}", module.getModuleId());
			ModuleUtil.refreshApplicationContext(applicationContext, isOpenmrsStartup, module);
		}

		return module;
	}

	private static void registerProvidedPackages(ModuleClassLoader moduleClassLoader) {
		for (String providedPackage : moduleClassLoader.getProvidedPackages()) {
			var newSet = new HashSet<ModuleClassLoader>();

			Set<ModuleClassLoader> set = ModuleFactory.providedPackages.get(providedPackage);
			if (set != null) {
				newSet.addAll(set);
			}

			newSet.add(moduleClassLoader);
			ModuleFactory.providedPackages.put(providedPackage, newSet);
		}
	}

	/**
	 * Send an Alert to all super users that the given module did not start successfully.
	 *
	 * @param mod The Module that failed
	 */
	private static void notifySuperUsersAboutModuleFailure(Module mod) {
		try {
			// Add the privileges necessary for notifySuperUsers
			Context.addProxyPrivilege(PrivilegeConstants.MANAGE_ALERTS);

			// Send an alert to all administrators
			Context.getAlertService().notifySuperUsers("Module.startupError.notification.message", null, mod.getName());
		} catch (Exception e) {
			log.error("Unable to send an alert to the super users", e);
		} finally {
			// Remove added privileges
			Context.removeProxyPrivilege(PrivilegeConstants.MANAGE_ALERTS);
		}
	}

	/**
	 * Send an Alert to all super users that modules did not start due to cyclic dependencies
	 */
	private static void notifySuperUsersAboutCyclicDependencies(Exception ex) {
		try {
			Context.addProxyPrivilege(PrivilegeConstants.MANAGE_ALERTS);
			Context.getAlertService().notifySuperUsers("Module.error.cyclicDependencies", ex, ex.getMessage());
		} catch (Exception e) {
			log.error("Unable to send an alert to the super users", e);
		} finally {
			Context.removeProxyPrivilege(PrivilegeConstants.MANAGE_ALERTS);
		}
	}

	/**
	 * Gets the error message of a module which fails to start.
	 *
	 * @param module the module that has failed to start.
	 * @return the message text.
	 */
	private static String getFailedToStartModuleMessage(Module module) {
		String[] params = { module.getName(), String.join(",", ModuleDependencyResolver.getMissingRequiredModules(module)) };
		return Context.getMessageSourceService().getMessage("Module.error.moduleCannotBeStarted", params,
		    Context.getLocale());
	}

	/**
	 * Gets the error message of cyclic dependencies between modules
	 *
	 * @return the message text.
	 */
	private static String getCyclicDependenciesMessage(String message) {
		return Context.getMessageSourceService().getMessage("Module.error.cyclicDependencies", new Object[] { message },
		    Context.getLocale());
	}

	/**
	 * Returns the description for the [moduleId].mandatory global property
	 *
	 * @param moduleId
	 * @return description to use for .mandatory property
	 */
	private static String getGlobalPropertyMandatoryModuleDescription(String moduleId) {
		String ret = "true/false whether or not the " + moduleId;
		ret += " module MUST start when openmrs starts.  This is used to make sure that mission critical";
		ret += " modules are always running if openmrs is running.";

		return ret;
	}
}
