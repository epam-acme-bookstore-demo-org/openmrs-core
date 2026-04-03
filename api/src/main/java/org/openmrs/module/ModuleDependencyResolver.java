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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.openmrs.util.CycleException;
import org.openmrs.util.Graph;

/**
 * Handles module dependency checking and ordering.
 * <p>
 * This is an internal class extracted from {@link ModuleFactory} and should not be used directly.
 * All public access should go through {@link ModuleFactory}.
 */
final class ModuleDependencyResolver {

	private ModuleDependencyResolver() {
	}

	/**
	 * Sort modules in startup order based on required and aware-of dependencies
	 *
	 * @param modules list of modules to sort
	 * @return list of modules sorted by dependencies
	 * @throws CycleException
	 */
	static List<Module> getModulesInStartupOrder(Collection<Module> modules) throws CycleException {
		Graph<Module> graph = new Graph<>();

		for (Module mod : modules) {

			graph.addNode(mod);

			// Required dependencies
			for (String key : mod.getRequiredModules()) {
				Module module = ModuleFactory.getModuleByPackage(key);
				Module fromNode = graph.getNode(module);
				if (fromNode == null) {
					fromNode = module;
				}

				if (fromNode != null) {
					graph.addEdge(graph.new Edge(
					                             fromNode,
					                             mod));
				}
			}

			// Aware-of dependencies
			for (String key : mod.getAwareOfModules()) {
				Module module = ModuleFactory.getModuleByPackage(key);
				Module fromNode = graph.getNode(module);
				if (fromNode == null) {
					fromNode = module;
				}

				if (fromNode != null) {
					graph.addEdge(graph.new Edge(
					                             fromNode,
					                             mod));
				}
			}
		}

		return graph.topologicalSort();
	}

	/**
	 * Tests whether all modules mentioned in module.requiredModules are loaded and started already (by
	 * being in the startedModules list)
	 *
	 * @param module
	 * @return true/false boolean whether this module's required modules are all started
	 */
	static boolean requiredModulesStarted(Module module) {
		//required
		for (String reqModPackage : module.getRequiredModules()) {
			boolean started = false;
			for (Module mod : ModuleFactory.getStartedModules()) {
				if (mod.getPackageName().equals(reqModPackage)) {
					String reqVersion = module.getRequiredModuleVersion(reqModPackage);
					if (reqVersion == null || ModuleUtil.compareVersion(mod.getVersion(), reqVersion) >= 0) {
						started = true;
					}
					break;
				}
			}

			if (!started) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Convenience method to return a List of Strings containing a description of which modules the
	 * passed module requires but which are not started. The returned description of each module is the
	 * moduleId followed by the required version if one is specified
	 *
	 * @param module the module to check required modules for
	 * @return List&lt;String&gt; of module names + optional required versions: "org.openmrs.formentry
	 *         1.8, org.rg.patientmatching"
	 */
	static List<String> getMissingRequiredModules(Module module) {
		var ret = new ArrayList<String>();
		for (String moduleName : module.getRequiredModules()) {
			boolean started = false;
			for (Module mod : ModuleFactory.getStartedModules()) {
				if (mod.getPackageName().equals(moduleName)) {
					String reqVersion = module.getRequiredModuleVersion(moduleName);
					if (reqVersion == null || ModuleUtil.compareVersion(mod.getVersion(), reqVersion) >= 0) {
						started = true;
					}
					break;
				}
			}

			if (!started) {
				String moduleVersion = module.getRequiredModuleVersion(moduleName);
				moduleName = moduleName.replace("org.openmrs.module.", "").replace("org.openmrs.", "");
				ret.add(moduleName + (moduleVersion != null ? " " + moduleVersion : ""));
			}
		}
		return ret;
	}

	/**
	 * Convenience method used to identify module interdependencies and alert the user before modules
	 * are shut down.
	 *
	 * @param moduleId the moduleId used to identify the module being validated
	 * @return List&lt;dependentModules&gt; the list of moduleId's which depend on the module about to
	 *         be shutdown.
	 * @since 1.10
	 */
	static List<String> getDependencies(String moduleId) {
		List<String> dependentModules = null;
		Module module = ModuleFactory.getModuleById(moduleId);

		Map<String, Module> startedModules = ModuleFactory.getStartedModulesMap();
		String modulePackage = module.getPackageName();

		for (Entry<String, Module> entry : startedModules.entrySet()) {
			if (!moduleId.equals(entry.getKey()) && entry.getValue().getRequiredModules().contains(modulePackage)) {
				if (dependentModules == null) {
					dependentModules = new ArrayList<>();
				}
				dependentModules.add(entry.getKey() + " " + entry.getValue().getVersion());
			}
		}
		return dependentModules;
	}
}
