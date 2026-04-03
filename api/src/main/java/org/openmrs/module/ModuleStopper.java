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

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.aopalliance.aop.Advice;
import org.openmrs.api.OpenmrsService;
import org.openmrs.api.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.Advisor;

/**
 * Handles module shutdown logic including stopping individual modules and unloading.
 * <p>
 * This is an internal class extracted from {@link ModuleFactory} and should not be used directly.
 * All public access should go through {@link ModuleFactory}.
 */
final class ModuleStopper {

	private ModuleStopper() {
	}

	private static final Logger log = LoggerFactory.getLogger(ModuleStopper.class);

	/**
	 * Runs through the advice and extension points and removes from api. <br>
	 * Also calls mod.Activator.shutdown()
	 *
	 * @param mod module to stop
	 * @see #stopModule(Module, boolean, boolean)
	 */
	static void stopModule(Module mod) {
		stopModule(mod, false, false);
	}

	/**
	 * Runs through the advice and extension points and removes from api.<br>
	 * Also calls mod.Activator.shutdown()
	 *
	 * @param mod the module to stop
	 * @param isShuttingDown true if this is called during the process of shutting down openmrs
	 * @see #stopModule(Module, boolean, boolean)
	 */
	static void stopModule(Module mod, boolean isShuttingDown) {
		stopModule(mod, isShuttingDown, false);
	}

	/**
	 * Runs through the advice and extension points and removes from api.<br>
	 * <code>skipOverStartedProperty</code> should only be true when openmrs is stopping modules because
	 * it is shutting down. When normally stopping a module, use {@link #stopModule(Module)} (or leave
	 * value as false). This property controls whether the globalproperty is set for startup/shutdown.
	 * <br>
	 * Also calls module's {@link ModuleActivator#stopped()}
	 *
	 * @param mod module to stop
	 * @param skipOverStartedProperty true if we don't want to set &lt;moduleid&gt;.started to false
	 * @param isFailedStartup true if this is being called as a cleanup because of a failed module
	 *            startup
	 * @return list of dependent modules that were stopped because this module was stopped. This will
	 *         never be null.
	 */
	static List<Module> stopModule(Module mod, boolean skipOverStartedProperty, boolean isFailedStartup)
	        throws ModuleMustStartException {

		var dependentModulesStopped = new ArrayList<Module>();

		if (mod == null) {
			return dependentModulesStopped;
		}

		if (!ModuleFactory.isModuleStarted(mod)) {
			return dependentModulesStopped;
		}

		try {
			if (mod.getModuleActivator() != null) {
				mod.getModuleActivator().willStop();
			}
		} catch (Exception t) {
			log.warn("Unable to call module's Activator.willStop() method", t);
		}

		String moduleId = mod.getModuleId();

		if (!isFailedStartup && mod.isMandatory()) {
			throw new MandatoryModuleException(moduleId);
		}

		String modulePackage = mod.getPackageName();

		List<Module> startedModulesCopy = new ArrayList<>(ModuleFactory.getStartedModules());
		for (Module dependentModule : startedModulesCopy) {
			if (dependentModule != null && !dependentModule.equals(mod)
			        && isModuleRequiredByAnother(dependentModule, modulePackage)) {
				dependentModulesStopped.add(dependentModule);
				dependentModulesStopped.addAll(stopModule(dependentModule, skipOverStartedProperty, isFailedStartup));
			}
		}

		ModuleFactory.getStartedModulesMap().remove(moduleId);
		if (ModuleFactory.actualStartupOrder != null) {
			ModuleFactory.actualStartupOrder.remove(moduleId);
			for (Module depModule : dependentModulesStopped) {
				ModuleFactory.actualStartupOrder.remove(depModule.getModuleId());
			}
		}

		if (!skipOverStartedProperty && !Context.isRefreshingContext()) {
			ModuleFactory.saveGlobalProperty(moduleId + ".started", "false",
			    ModuleFactory.getGlobalPropertyStartedDescription(moduleId));
		}

		ModuleClassLoader moduleClassLoader = ModuleFactory.getModuleClassLoaderMap().get(mod);
		if (moduleClassLoader != null) {
			unregisterProvidedPackages(moduleClassLoader);
			log.debug("Mod was in classloader map.  Removing advice and extensions.");
			removeModuleAdvice(mod, moduleId);
			removeModuleExtensions(mod, moduleId);
		}

		List<OpenmrsService> services = Context.getModuleOpenmrsServices(modulePackage);
		if (services != null) {
			for (OpenmrsService service : services) {
				service.onShutdown();
			}
		}

		try {
			if (mod.getModuleActivator() != null) {
				mod.getModuleActivator().stopped();
			}
		} catch (Exception t) {
			log.warn("Unable to call module's Activator.shutdown() method", t);
		}

		mod.getExtensions().clear();
		mod.setModuleActivator(null);
		mod.disposeAdvicePointsClassInstance();

		ModuleClassLoader cl = removeClassLoader(mod);
		if (cl != null) {
			cl.dispose();
		}

		return dependentModulesStopped;
	}

	/**
	 * Removes module from module repository
	 *
	 * @param mod module to unload
	 */
	static void unloadModule(Module mod) {

		// remove this module's advice and extensions
		if (ModuleFactory.isModuleStarted(mod)) {
			stopModule(mod, true);
		}

		// remove from list of loaded modules
		if (mod != null) {
			ModuleFactory.getLoadedModulesMap().remove(mod.getModuleId());

			// remove the file from the module repository
			File file = mod.getFile();

			boolean deleted = file.delete();
			if (!deleted) {
				file.deleteOnExit();
				log.warn("Could not delete " + file.getAbsolutePath());
			}
		}
	}

	private static void removeModuleAdvice(Module mod, String moduleId) {
		try {
			for (AdvicePoint advice : mod.getAdvicePoints()) {
				try {
					Class cls = Context.loadClass(advice.getPoint());
					Object aopObject = advice.getClassInstance();
					if (aopObject instanceof Advisor advisor) {
						log.debug("removing advisor: " + aopObject.getClass());
						Context.removeAdvisor(cls, advisor);
					} else {
						log.debug("Removing advice: " + aopObject.getClass());
						Context.removeAdvice(cls, (Advice) aopObject);
					}
				} catch (Exception t) {
					log.warn("Could not remove advice point: " + advice.getPoint(), t);
				}
			}
		} catch (Exception t) {
			log.warn("Error while getting advicePoints from module: " + moduleId, t);
		}
	}

	private static void removeModuleExtensions(Module mod, String moduleId) {
		try {
			for (Extension ext : mod.getExtensions()) {
				String extId = ext.getExtensionId();
				try {
					List<Extension> tmpExtensions = ModuleFactory.getExtensions(extId);
					tmpExtensions.remove(ext);
					ModuleFactory.getExtensionMap().put(extId, tmpExtensions);
				} catch (Exception exterror) {
					log.warn("Error while getting extension: " + ext, exterror);
				}
			}
		} catch (Exception t) {
			log.warn("Error while getting extensions from module: " + moduleId, t);
		}
	}

	/**
	 * Checks if a module is required by another
	 *
	 * @param dependentModule the module whose required modules are to be checked
	 * @param modulePackage the package of the module to check if required by another
	 * @return true if the module is required, else false
	 */
	private static boolean isModuleRequiredByAnother(Module dependentModule, String modulePackage) {
		return dependentModule.getRequiredModules() != null && dependentModule.getRequiredModules().contains(modulePackage);
	}

	private static ModuleClassLoader removeClassLoader(Module mod) {
		// create map if it is null
		ModuleClassLoader cl = ModuleFactory.moduleClassLoaders.getIfPresent(mod);
		if (cl == null) {
			log.warn("Module: " + mod.getModuleId() + " does not exist");
		}

		ModuleFactory.moduleClassLoaders.invalidate(mod);

		return cl;
	}

	static void unregisterProvidedPackages(ModuleClassLoader moduleClassLoader) {
		for (String providedPackage : moduleClassLoader.getProvidedPackages()) {
			var newSet = new HashSet<ModuleClassLoader>();

			Set<ModuleClassLoader> set = ModuleFactory.providedPackages.get(providedPackage);
			if (set != null) {
				newSet.addAll(set);
			}
			newSet.remove(moduleClassLoader);

			ModuleFactory.providedPackages.put(providedPackage, newSet);
		}
	}
}
