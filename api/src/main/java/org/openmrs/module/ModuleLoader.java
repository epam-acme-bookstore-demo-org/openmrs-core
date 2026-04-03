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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.openmrs.api.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles module loading and parsing from files into the module registry.
 * <p>
 * This is an internal class extracted from {@link ModuleFactory} and should not be used directly.
 * All public access should go through {@link ModuleFactory}.
 */
final class ModuleLoader {

	private ModuleLoader() {
	}

	private static final Logger log = LoggerFactory.getLogger(ModuleLoader.class);

	/**
	 * Add a module (in the form of a jar file) to the list of openmrs modules Returns null if an error
	 * occurred and/or module was not successfully loaded
	 *
	 * @param moduleFile
	 * @return Module
	 */
	static Module loadModule(File moduleFile) throws ModuleException {

		return loadModule(moduleFile, true);

	}

	/**
	 * Add a module (in the form of a jar file) to the list of openmrs modules Returns null if an error
	 * occurred and/or module was not successfully loaded
	 *
	 * @param moduleFile
	 * @param replaceIfExists unload a module that has the same moduleId if one is loaded already
	 * @return Module
	 */
	static Module loadModule(File moduleFile, Boolean replaceIfExists) throws ModuleException {
		Module module = new ModuleFileParser(Context.getMessageSourceService()).parse(moduleFile);

		if (module != null) {
			loadModule(module, replaceIfExists);
		}

		return module;
	}

	/**
	 * Add a module to the list of openmrs modules
	 * <p>
	 * <strong>Should</strong> load module if it is currently not loaded<br/>
	 * <strong>Should</strong> not load module if already loaded <strong>Should</strong>
	 *
	 * @param module
	 * @param replaceIfExists unload a module that has the same moduleId if one is loaded already always
	 *            load module if replacement is wanted <strong>Should</strong> not load an older version
	 *            of the same module <strong>Should</strong> load a newer version of the same module
	 * @return module the module that was loaded or if the module exists already with the same version,
	 *         the old module
	 */
	static Module loadModule(Module module, Boolean replaceIfExists) throws ModuleException {

		log.debug("Adding module {} to the module queue", module.getName());

		Module oldModule = ModuleFactory.getLoadedModulesMap().get(module.getModuleId());
		if (oldModule != null) {
			int versionComparison = ModuleUtil.compareVersion(oldModule.getVersion(), module.getVersion());
			if (versionComparison < 0) {
				// if oldModule version is lower, unload it and use the new
				ModuleStopper.unloadModule(oldModule);
			} else if (versionComparison == 0) {
				if (replaceIfExists) {
					// if the versions are the same and we're told to replaceIfExists, use the new
					ModuleStopper.unloadModule(oldModule);
				} else {
					// if the versions are equal and we're not told to replaceIfExists, jump out of here in a bad way
					throw new ModuleException("A module with the same id and version already exists", module.getModuleId());
				}
			} else {
				// if the older (already loaded) module is newer, keep that original one that was loaded. return that one.
				return oldModule;
			}
		}

		ModuleFactory.getLoadedModulesMap().put(module.getModuleId(), module);

		return module;
	}

	/**
	 * Load OpenMRS modules from <code>OpenmrsUtil.getModuleRepository()</code>
	 */
	static void loadModules() {

		// load modules from the user's module repository directory
		File modulesFolder = ModuleUtil.getModuleRepository();

		log.debug("Loading modules from: {}", modulesFolder.getAbsolutePath());

		File[] files = modulesFolder.listFiles();
		if (modulesFolder.isDirectory() && files != null) {
			loadModules(Arrays.asList(files));
		} else {
			log.error("modules folder: '" + modulesFolder.getAbsolutePath() + "' is not a directory or IO error occurred");
		}
	}

	/**
	 * Attempt to load the given files as OpenMRS modules
	 *
	 * @param modulesToLoad the list of files to try and load <strong>Should</strong> not crash when
	 *            file is not found or broken <strong>Should</strong> setup requirement mappings for
	 *            every module <strong>Should</strong> not start the loaded modules
	 */
	static void loadModules(List<File> modulesToLoad) {
		// loop over the modules and load all the modules that we can
		for (File f : modulesToLoad) {
			if (f.exists()) {
				// ignore .svn folder and the like
				if (!f.getName().startsWith(".")) {
					try {
						// last module loaded wins
						Module mod = loadModule(f, true);
						log.debug("Loaded module: " + mod + " successfully");
					} catch (Exception e) {
						log.error("Unable to load file in module directory: " + f + ". Skipping file.", e);
					}
				}
			} else {
				log.error("Could not find file in module directory: " + f);
			}
		}

		//inform modules, that they can't start before other modules

		Map<String, Module> loadedModulesMap = ModuleFactory.getLoadedModulesMapPackage();
		for (Module m : loadedModulesMap.values()) {
			Map<String, String> startBeforeModules = m.getStartBeforeModulesMap();
			if (startBeforeModules.size() > 0) {
				for (String s : startBeforeModules.keySet()) {
					Module mod = loadedModulesMap.get(s);
					if (mod != null) {
						mod.addRequiredModule(m.getPackageName(), m.getVersion());
					}
				}
			}
		}
	}
}
