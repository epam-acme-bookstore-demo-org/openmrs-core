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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarFile;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.GlobalProperty;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.ServiceContext;
import org.openmrs.util.OpenmrsClassLoader;
import org.openmrs.util.OpenmrsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.context.support.AbstractRefreshableApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;

/**
 * Utility methods for working and manipulating modules
 */
public class ModuleUtil {

	private ModuleUtil() {
	}

	private static final Logger log = LoggerFactory.getLogger(ModuleUtil.class);

	/**
	 * Start up the module system with the given properties.
	 *
	 * @param props Properties (OpenMRS runtime properties)
	 */
	public static void startup(Properties props) throws ModuleMustStartException {

		String moduleListString = props.getProperty(ModuleConstants.RUNTIMEPROPERTY_MODULE_LIST_TO_LOAD);

		if (moduleListString == null || moduleListString.isEmpty()) {
			// Attempt to get all of the modules from the modules folder
			// and store them in the modules list
			log.debug("Starting all modules");
			ModuleFactory.loadModules();
		} else {
			// use the list of modules and load only those
			log.debug("Starting all modules in this list: " + moduleListString);

			String[] moduleArray = moduleListString.split(" ");
			var modulesToLoad = new ArrayList<File>();

			for (String modulePath : moduleArray) {
				if (modulePath == null || modulePath.length() == 0) {
					continue;
				}
				var file = new File(modulePath);
				if (file.exists()) {
					modulesToLoad.add(file);
				} else {
					expandClasspathModule(modulePath, file, modulesToLoad);
				}
			}

			ModuleFactory.loadModules(modulesToLoad);
		}

		// start all of the modules we just loaded
		ModuleFactory.startModules();

		// some debugging info
		if (log.isDebugEnabled()) {
			Collection<Module> modules = ModuleFactory.getStartedModules();
			if (modules == null || modules.isEmpty()) {
				log.debug("No modules loaded");
			} else {
				log.debug("Found and loaded {} module(s)", modules.size());
			}
		}

		// make sure all mandatory modules are loaded and started
		checkMandatoryModulesStarted();
	}

	private static void expandClasspathModule(String modulePath, File file, List<File> modulesToLoad) {
		try (InputStream stream = ModuleUtil.class.getClassLoader().getResourceAsStream(modulePath)) {
			if (stream == null) {
				log.error("Unable to load module at path: " + modulePath
				        + " because no file exists there and it is not found on the classpath. (absolute path tried: "
				        + file.getAbsolutePath() + ")");
				return;
			}
			String tmpDir = System.getProperty("java.io.tmpdir");
			File expandedFile = File.createTempFile(file.getName() + "-", ".omod", new File(tmpDir));
			var outStream = new FileOutputStream(expandedFile, false);
			OpenmrsUtil.copyFile(stream, outStream);
			modulesToLoad.add(expandedFile);
			expandedFile.deleteOnExit();
		} catch (IOException io) {
			log.error("Unable to expand classpath found module: " + modulePath, io);
		}
	}

	/**
	 * Stops the module system by calling stopModule for all modules that are currently started
	 */
	public static void shutdown() {

		List<Module> modules = new ArrayList<>(ModuleFactory.getStartedModules());

		for (Module mod : modules) {
			log.debug("stopping module: {}", mod.getModuleId());

			if (mod.isStarted()) {
				ModuleFactory.stopModule(mod, true, true);
			}
		}

		log.debug("done shutting down modules");

		// clean up the static variables just in case they weren't done before
		ModuleFactory.extensionMap.clear();
		ModuleFactory.loadedModules.invalidateAll();
		ModuleFactory.moduleClassLoaders.invalidateAll();
		ModuleFactory.startedModules.invalidateAll();
	}

	/**
	 * Add the <code>inputStream</code> as a file in the modules repository
	 *
	 * @param inputStream <code>InputStream</code> to load
	 * @return filename String of the file's name of the stream
	 */
	public static File insertModuleFile(InputStream inputStream, String filename) {
		return ModuleFileUtil.insertModuleFile(inputStream, filename);
	}

	/**
	 * Checks if the current OpenMRS version is in an array of versions.
	 * <p>
	 * This method calls {@link ModuleUtil#matchRequiredVersions(String, String)} internally.
	 * </p>
	 * <p>
	 * <strong>Should</strong> return false when versions is null<br/>
	 * <strong>Should</strong> return false when versions is empty<br/>
	 * <strong>Should</strong> return true if current openmrs version matches one element in
	 * versions<br/>
	 * <strong>Should</strong> return false if current openmrs version does not match any element in
	 * versions
	 *
	 * @param versions the openmrs versions to be checked against the current openmrs version
	 * @return true if the current openmrs version is in versions otherwise false
	 */
	public static boolean isOpenmrsVersionInVersions(String... versions) {
		return ModuleVersionUtil.isOpenmrsVersionInVersions(versions);
	}

	/**
	 * For testing of {@link #isOpenmrsVersionInVersions(String...)} only.
	 *
	 * @param version the version
	 * @param versions versions to match
	 * @return true if version matches any value from versions
	 */
	static boolean isVersionInVersions(String version, String... versions) {
		return ModuleVersionUtil.isVersionInVersions(version, versions);
	}

	/**
	 * This method is an enhancement of {@link #compareVersion(String, String)} and adds support for
	 * wildcard characters and upperbounds. <br>
	 * <br>
	 * This method calls {@link ModuleUtil#checkRequiredVersion(String, String)} internally. <br>
	 * <br>
	 * The require version number in the config file can be in the following format:
	 * <ul>
	 * <li>1.2.3</li>
	 * <li>1.2.*</li>
	 * <li>1.2.2 - 1.2.3</li>
	 * <li>1.2.* - 1.3.*</li>
	 * </ul>
	 * <p>
	 * Again the possible require version number formats with their interpretation:
	 * <ul>
	 * <li>1.2.3 means 1.2.3 and above</li>
	 * <li>1.2.* means any version of the 1.2.x branch. That is 1.2.0, 1.2.1, 1.2.2,... but not 1.3.0,
	 * 1.4.0</li>
	 * <li>1.2.2 - 1.2.3 means 1.2.2 and 1.2.3 (inclusive)</li>
	 * <li>1.2.* - 1.3.* means any version of the 1.2.x and 1.3.x branch</li>
	 * </ul>
	 * </p>
	 * <p>
	 * <strong>Should</strong> allow ranged required version<br/>
	 * <strong>Should</strong> allow ranged required version with wild card<br/>
	 * <strong>Should</strong> allow ranged required version with wild card on one end<br/>
	 * <strong>Should</strong> allow single entry for required version<br/>
	 * <strong>Should</strong> allow required version with wild card<br/>
	 * <strong>Should</strong> allow non numeric character required version<br/>
	 * <strong>Should</strong> allow ranged non numeric character required version<br/>
	 * <strong>Should</strong> allow ranged non numeric character with wild card<br/>
	 * <strong>Should</strong> allow ranged non numeric character with wild card on one end<br/>
	 * <strong>Should</strong> return false when openmrs version beyond wild card range<br/>
	 * <strong>Should</strong> return false when required version beyond openmrs version<br/>
	 * <strong>Should</strong> return false when required version with wild card beyond openmrs
	 * version<br/>
	 * <strong>Should</strong> return false when required version with wild card on one end beyond
	 * openmrs version<br/>
	 * <strong>Should</strong> return false when single entry required version beyond openmrs
	 * version<br/>
	 * <strong>Should</strong> allow release type in the version<br/>
	 * <strong>Should</strong> match when revision number is below maximum revision number<br/>
	 * <strong>Should</strong> not match when revision number is above maximum revision number<br/>
	 * <strong>Should</strong> correctly set upper and lower limit for versionRange with qualifiers and
	 * wild card<br/>
	 * <strong>Should</strong> match when version has wild card plus qualifier and is within
	 * boundary<br/>
	 * <strong>Should</strong> not match when version has wild card plus qualifier and is outside
	 * boundary<br/>
	 * <strong>Should</strong> match when version has wild card and is within boundary<br/>
	 * <strong>Should</strong> not match when version has wild card and is outside boundary<br/>
	 * <strong>Should</strong> return true when required version is empty
	 *
	 * @param version openmrs version number to be compared
	 * @param versionRange value in the config file for required openmrs version
	 * @return true if the <code>version</code> is within the <code>value</code>
	 */
	public static boolean matchRequiredVersions(String version, String versionRange) {
		return ModuleVersionUtil.matchRequiredVersions(version, versionRange);
	}

	/**
	 * This method is an enhancement of {@link #compareVersion(String, String)} and adds support for
	 * wildcard characters and upperbounds. <br>
	 * <br>
	 * <br>
	 * The require version number in the config file can be in the following format:
	 * <ul>
	 * <li>1.2.3</li>
	 * <li>1.2.*</li>
	 * <li>1.2.2 - 1.2.3</li>
	 * <li>1.2.* - 1.3.*</li>
	 * </ul>
	 * <p>
	 * Again the possible require version number formats with their interpretation:
	 * <ul>
	 * <li>1.2.3 means 1.2.3 and above</li>
	 * <li>1.2.* means any version of the 1.2.x branch. That is 1.2.0, 1.2.1, 1.2.2,... but not 1.3.0,
	 * 1.4.0</li>
	 * <li>1.2.2 - 1.2.3 means 1.2.2 and 1.2.3 (inclusive)</li>
	 * <li>1.2.* - 1.3.* means any version of the 1.2.x and 1.3.x branch</li>
	 * </ul>
	 * </p>
	 * <p>
	 * <strong>Should</strong> throw ModuleException if openmrs version beyond wild card range<br/>
	 * <strong>Should</strong> throw ModuleException if required version beyond openmrs version<br/>
	 * <strong>Should</strong> throw ModuleException if required version with wild card beyond openmrs
	 * version<br/>
	 * <strong>Should</strong> throw ModuleException if required version with wild card on one end
	 * beyond openmrs<br/>
	 * <strong>Should</strong> throw ModuleException if single entry required version beyond openmrs
	 * version<br/>
	 * <strong>Should</strong> throw ModuleException if SNAPSHOT not handled correctly<br/>
	 * <strong>Should</strong> handle SNAPSHOT versions<br/>
	 * <strong>Should</strong> handle ALPHA versions
	 *
	 * @param version openmrs version number to be compared
	 * @param versionRange value in the config file for required openmrs version
	 * @throws ModuleException if the <code>version</code> is not within the <code>value</code> version
	 */
	public static void checkRequiredVersion(String version, String versionRange) throws ModuleException {
		ModuleVersionUtil.checkRequiredVersion(version, versionRange);
	}

	/**
	 * Compare two version strings.
	 *
	 * @param versionA String like 1.9.2.0, may include a qualifier like "-SNAPSHOT", may be null
	 * @param versionB String like 1.9.2.0, may include a qualifier like "-SNAPSHOT", may be null
	 * @return the value <code>0</code> if versions are equal; a value less than <code>0</code> if first
	 *         version is before the second one; a value greater than <code>0</code> if first version is
	 *         after the second one. If version numbers are equal and only one of them has a qualifier,
	 *         the version without the qualifier is considered greater.
	 */
	public static int compareVersion(String versionA, String versionB) {
		return ModuleVersionUtil.compareVersion(versionA, versionB);
	}

	/**
	 * Compare two version strings. Any version qualifiers are ignored in the comparison.
	 *
	 * @param versionA String like 1.9.2.0, may include a qualifier like "-SNAPSHOT", may be null
	 * @param versionB String like 1.9.2.0, may include a qualifier like "-SNAPSHOT", may be null
	 * @return the value <code>0</code> if versions are equal; a value less than <code>0</code> if first
	 *         version is before the second one; a value greater than <code>0</code> if first version is
	 *         after the second one.
	 */
	public static int compareVersionIgnoringQualifier(String versionA, String versionB) {
		return ModuleVersionUtil.compareVersionIgnoringQualifier(versionA, versionB);
	}

	/**
	 * Checks for qualifier version (i.e "-SNAPSHOT", "-ALPHA" etc. after maven version conventions)
	 *
	 * @param version String like 1.9.2-SNAPSHOT
	 * @return true if version contains qualifier
	 */
	public static boolean isVersionWithQualifier(String version) {
		return ModuleVersionUtil.isVersionWithQualifier(version);
	}

	/**
	 * Gets the folder where modules are stored. ModuleExceptions are thrown on errors
	 * <p>
	 * <strong>Should</strong> use the runtime property as the first choice if specified<br/>
	 * <strong>Should</strong> return the correct file if the runtime property is an absolute path
	 *
	 * @return folder containing modules
	 */
	public static File getModuleRepository() {

		String folderName = Context.getRuntimeProperties().getProperty(ModuleConstants.REPOSITORY_FOLDER_RUNTIME_PROPERTY);
		if (StringUtils.isBlank(folderName)) {
			AdministrationService as = Context.getAdministrationService();
			folderName = as.getGlobalProperty(ModuleConstants.REPOSITORY_FOLDER_PROPERTY,
			    ModuleConstants.REPOSITORY_FOLDER_PROPERTY_DEFAULT);
		}
		// try to load the repository folder straight away.
		var folder = new File(folderName);

		// if the property wasn't a full path already, assume it was intended to be a folder in the
		// application directory
		if (!folder.exists()) {
			folder = new File(OpenmrsUtil.getApplicationDataDirectory(), folderName);
		}

		// now create the modules folder if it doesn't exist
		if (!folder.exists()) {
			log.warn("Module repository " + folder.getAbsolutePath() + " doesn't exist.  Creating directories now.");
			folder.mkdirs();
		}

		if (!folder.isDirectory()) {
			throw new ModuleException("Module repository is not a directory at: " + folder.getAbsolutePath());
		}

		return folder;
	}

	/**
	 * Utility method to convert a {@link File} object to a local URL.
	 *
	 * @param file a file object
	 * @return absolute URL that points to the given file
	 * @throws MalformedURLException if file can't be represented as URL for some reason
	 */
	public static URL file2url(final File file) throws MalformedURLException {
		return ModuleFileUtil.file2url(file);
	}

	/**
	 * Expand the given <code>fileToExpand</code> jar to the <code>tmpModuleFile</code> directory If
	 * <code>name</code> is null, the entire jar is expanded. If<code>name</code> is not null, then only
	 * that path/file is expanded.
	 * <p>
	 * <strong>Should</strong> expand entire jar if name is null<br/>
	 * <strong>Should</strong> expand entire jar if name is empty string<br/>
	 * <strong>Should</strong> expand directory with parent tree if name is directory and keepFullPath
	 * is true<br/>
	 * <strong>Should</strong> expand directory without parent tree if name is directory and
	 * keepFullPath is false<br/>
	 * <strong>Should</strong> expand file with parent tree if name is file and keepFullPath is
	 * true<br/>
	 * <strong>Should</strong> throw exception for Zip slip attack
	 *
	 * @param fileToExpand file pointing at a .jar
	 * @param tmpModuleDir directory in which to place the files
	 * @param name filename inside of the jar to look for and expand
	 * @param keepFullPath if true, will recreate entire directory structure in tmpModuleDir relating to
	 *            <code>name</code>. if false will start directory structure at <code>name</code>
	 * @throws UnsupportedOperationException if an entry would be extracted outside of tmpModuleDir (Zip
	 *             slip attack)
	 */
	public static void expandJar(File fileToExpand, File tmpModuleDir, String name, boolean keepFullPath)
	        throws IOException {
		ModuleFileUtil.expandJar(fileToExpand, tmpModuleDir, name, keepFullPath);
	}

	/**
	 * Downloads the contents of a URL and copies them to a string (Borrowed from oreilly)
	 * <p>
	 * <strong>Should</strong> return a valid input stream for old module urls
	 *
	 * @param url
	 * @return InputStream of contents
	 */
	public static InputStream getURLStream(URL url) {
		return ModuleFileUtil.getURLStream(url);
	}

	/**
	 * Convenience method to follow http to https redirects. Will follow a total of 5 redirects, then
	 * fail out due to foolishness on the url's part.
	 *
	 * @param c the {@link URLConnection} to open
	 * @return an {@link InputStream} that is not necessarily at the same url, possibly at a 403
	 *         redirect.
	 * @throws IOException
	 * @see #getURLStream(URL)
	 */
	protected static InputStream openConnectionCheckRedirects(URLConnection c) throws IOException {
		return ModuleFileUtil.openConnectionCheckRedirects(c);
	}

	/**
	 * Downloads the contents of a URL and copies them to a string (Borrowed from oreilly)
	 * <p>
	 * <strong>Should</strong> return an update rdf page for old https dev urls<br/>
	 * <strong>Should</strong> return an update rdf page for old https module urls<br/>
	 * <strong>Should</strong> return an update rdf page for module urls
	 *
	 * @param url
	 * @return String contents of the URL
	 */
	public static String getURL(URL url) {
		return ModuleFileUtil.getURL(url);
	}

	/**
	 * Iterates over the modules and checks each update.rdf file for an update
	 *
	 * @return True if an update was found for one of the modules, false if none were found
	 * @throws ModuleException
	 */
	public static Boolean checkForModuleUpdates() throws ModuleException {

		Boolean updateFound = false;

		for (Module mod : ModuleFactory.getLoadedModules()) {
			String updateURL = mod.getUpdateURL();
			if (StringUtils.isNotEmpty(updateURL)) {
				try {
					// get the contents pointed to by the url
					var url = new URL(updateURL);
					if (!url.toString().endsWith(ModuleConstants.UPDATE_FILE_NAME)) {
						log.warn("Illegal url: " + url);
						continue;
					}
					String content = getURL(url);

					// skip empty or invalid updates
					if ("".equals(content)) {
						continue;
					}

					// process and parse the contents
					var parser = new UpdateFileParser(content);
					parser.parse();

					log.debug("Update for mod: " + mod.getModuleId() + " compareVersion result: "
					        + compareVersion(mod.getVersion(), parser.getCurrentVersion()));

					// check the update.rdf version against the installed version
					if (compareVersion(mod.getVersion(), parser.getCurrentVersion()) < 0) {
						if (mod.getModuleId().equals(parser.getModuleId())) {
							mod.setDownloadURL(parser.getDownloadURL());
							mod.setUpdateVersion(parser.getCurrentVersion());
							updateFound = true;
						} else {
							log.warn("Module id does not match in update.rdf:" + parser.getModuleId());
						}
					} else {
						mod.setDownloadURL(null);
						mod.setUpdateVersion(null);
					}
				} catch (ModuleException e) {
					log.warn("Unable to get updates from update.xml", e);
				} catch (MalformedURLException e) {
					log.warn("Unable to form a URL object out of: " + updateURL, e);
				}
			}
		}

		return updateFound;
	}

	/**
	 * @return true/false whether the 'allow upload' or 'allow web admin' property has been turned on
	 */
	public static Boolean allowAdmin() {

		Properties properties = Context.getRuntimeProperties();
		String prop = properties.getProperty(ModuleConstants.RUNTIMEPROPERTY_ALLOW_UPLOAD, null);
		if (prop == null) {
			prop = properties.getProperty(ModuleConstants.RUNTIMEPROPERTY_ALLOW_ADMIN, "false");
		}

		return "true".equals(prop);
	}

	/**
	 * @see ModuleUtil#refreshApplicationContext(AbstractRefreshableApplicationContext, boolean, Module)
	 */
	public static AbstractRefreshableApplicationContext refreshApplicationContext(
	        AbstractRefreshableApplicationContext ctx) {
		return refreshApplicationContext(ctx, false, null);
	}

	/**
	 * Refreshes the given application context "properly" in OpenMRS. Will first shut down the Context
	 * and destroy the classloader, then will refresh and set everything back up again.
	 *
	 * @param ctx Spring application context that needs refreshing.
	 * @param isOpenmrsStartup if this refresh is being done at application startup.
	 * @param startedModule the module that was just started and waiting on the context refresh.
	 * @return AbstractRefreshableApplicationContext The newly refreshed application context.
	 */
	public static AbstractRefreshableApplicationContext refreshApplicationContext(AbstractRefreshableApplicationContext ctx,
	        boolean isOpenmrsStartup, Module startedModule) {
		//notify all started modules that we are about to refresh the context
		Set<Module> startedModules = new LinkedHashSet<>(ModuleFactory.getStartedModulesInOrder());
		for (Module module : startedModules) {
			try {
				if (module.getModuleActivator() != null) {
					log.debug("Run module willRefreshContext: {}", module.getModuleId());
					Thread.currentThread().setContextClassLoader(ModuleFactory.getModuleClassLoader(module));
					module.getModuleActivator().willRefreshContext();
				}
			} catch (Error e) {
				log.warn("Unable to call willRefreshContext() method in the module's activator", e);
			}
		}

		ServiceContext.destroyInstance();

		try {
			ctx.stop();
			ctx.close();
		} catch (Exception e) {
			log.warn("Exception while stopping and closing context: ", e);
			// Spring seems to be trying to refresh the context instead of /just/ stopping
			// pass
		}
		OpenmrsClassLoader.destroyInstance();
		ctx.setClassLoader(OpenmrsClassLoader.getInstance());
		Thread.currentThread().setContextClassLoader(OpenmrsClassLoader.getInstance());

		log.debug("Refreshing context");
		ServiceContext.getInstance().startRefreshingContext();
		try {
			ctx.refresh();
			// TRUNK-6421: warn if XML-declared services also use annotations like @Autowired
			logWarningsForAnnotatedXmlServices(ctx);

		} finally {
			ServiceContext.getInstance().doneRefreshingContext();
		}
		log.debug("Done refreshing context");

		ctx.setClassLoader(OpenmrsClassLoader.getInstance());
		Thread.currentThread().setContextClassLoader(OpenmrsClassLoader.getInstance());

		OpenmrsClassLoader.setThreadsToNewClassLoader();

		// reload the advice points that were lost when refreshing Spring
		log.debug("Reloading advice for all started modules: {}", startedModules.size());

		try {
			//The call backs in this block may need lazy loading of objects
			//which will fail because we use an OpenSessionInViewFilter whose opened session
			//was closed when the application context was refreshed as above.
			//So we need to open another session now. TRUNK-3739
			Context.openSessionWithCurrentUser();
			for (Module module : startedModules) {
				if (!module.isStarted()) {
					continue;
				}

				ModuleFactory.loadAdvice(module);
				try {
					ModuleFactory.passDaemonToken(module);

					if (module.getModuleActivator() != null) {
						log.debug("Run module contextRefreshed: {}", module.getModuleId());
						module.getModuleActivator().contextRefreshed();
						try {
							//if it is system start up, call the started method for all started modules
							if (isOpenmrsStartup) {
								log.debug("Run module started: {}", module.getModuleId());
								module.getModuleActivator().started();
							}
							//if refreshing the context after a user started or uploaded a new module
							else if (!isOpenmrsStartup && module.equals(startedModule)) {
								log.debug("Run module started: {}", module.getModuleId());
								module.getModuleActivator().started();
							}
							log.debug("Done running module started: {}", module.getModuleId());
						} catch (Error e) {
							log.warn("Unable to invoke started() method on the module's activator", e);
							ModuleFactory.stopModule(module, true, true);
						}
					}

				} catch (Error e) {
					log.warn("Unable to invoke method on the module's activator ", e);
				}
			}
		} finally {
			Context.closeSessionWithCurrentUser();
		}

		return ctx;
	}

	private static void logWarningsForAnnotatedXmlServices(AbstractRefreshableApplicationContext ctx) {
		ConfigurableListableBeanFactory factory = ctx.getBeanFactory();

		for (String beanName : factory.getBeanDefinitionNames()) {
			BeanDefinition def = factory.getBeanDefinition(beanName);
			String beanClassName = def.getBeanClassName();

			if ("org.springframework.transaction.interceptor.TransactionProxyFactoryBean".equals(beanClassName)) {
				PropertyValue target = def.getPropertyValues().getPropertyValue("target");
				if (target != null) {
					if (target.getValue() instanceof BeanDefinitionHolder) {
						def = ((BeanDefinitionHolder) target.getValue()).getBeanDefinition();
						beanClassName = def.getBeanClassName();
					} else if (target.getValue() instanceof RuntimeBeanReference) {
						beanName = ((RuntimeBeanReference) target.getValue()).getBeanName();
						def = factory.getBeanDefinition(beanName);
						beanClassName = def.getBeanClassName();
					}
				}
			} else {
				continue;
			}

			if (beanClassName == null) {
				log.debug("No classname for bean {}", beanName);
				continue;
			}

			int proxySuffix = beanClassName.indexOf("$$");
			if (proxySuffix != -1) {
				// Get rid of proxy suffix
				beanClassName = beanClassName.substring(0, proxySuffix);
			}

			Class<?> beanClass;
			try {
				beanClass = OpenmrsClassLoader.getInstance().loadClass(beanClassName);
			} catch (ClassNotFoundException e) {
				log.debug("Unable to load class {} for bean {}", beanClassName, beanName, e);
				continue;
			}

			boolean hasAutowired = false;

			for (Field field : beanClass.getDeclaredFields()) {
				if (AnnotationUtils.getAnnotation(field, Autowired.class) != null) {
					hasAutowired = true;
					break;
				}
			}

			if (!hasAutowired) {
				for (Constructor<?> ctor : beanClass.getDeclaredConstructors()) {
					if (AnnotationUtils.getAnnotation(ctor, Autowired.class) != null) {
						hasAutowired = true;
						break;
					}
				}
			}

			if (!hasAutowired) {
				for (Method method : beanClass.getDeclaredMethods()) {
					if (AnnotationUtils.getAnnotation(method, Autowired.class) != null) {
						hasAutowired = true;
						break;
					}
				}
			}

			if (!hasAutowired) {
				continue;
			}

			log.warn(
			    "Service bean '{}' ({}) appears to be declared via XML "
			            + "and also uses @Autowired annotations. This combination can lead to "
			            + "slow context refresh when mixing XML-declared services with "
			            + "annotation-based injection. Please remove @Autowired annotations from the bean. See TRUNK-6363.",
			    beanName, beanClassName);
		}
	}

	/**
	 * Looks at the &lt;moduleid&gt;.mandatory properties and at the currently started modules to make
	 * sure that all mandatory modules have been started successfully.
	 * <p>
	 * <strong>Should</strong> throw ModuleException if a mandatory module is not started
	 *
	 * @throws ModuleException if a mandatory module isn't started
	 */
	protected static void checkMandatoryModulesStarted() throws ModuleException {

		List<String> mandatoryModuleIds = getMandatoryModules();
		Set<String> startedModuleIds = ModuleFactory.getStartedModulesMap().keySet();

		mandatoryModuleIds.removeAll(startedModuleIds);

		// any module ids left in the list are not started
		if (!mandatoryModuleIds.isEmpty()) {
			throw new MandatoryModuleException(mandatoryModuleIds);
		}
	}

	/**
	 * Returns all modules that are marked as mandatory. Currently this means there is a
	 * &lt;moduleid&gt;.mandatory=true global property.
	 * <p>
	 * <strong>Should</strong> return mandatory module ids
	 *
	 * @return list of modules ids for mandatory modules
	 */
	public static List<String> getMandatoryModules() {

		var mandatoryModuleIds = new ArrayList<String>();

		try {
			List<GlobalProperty> props = Context.getAdministrationService().getGlobalPropertiesBySuffix(".mandatory");

			for (GlobalProperty prop : props) {
				if ("true".equalsIgnoreCase(prop.getPropertyValue())) {
					mandatoryModuleIds.add(prop.getProperty().replace(".mandatory", ""));
				}
			}
		} catch (Exception e) {
			log.warn("Unable to get the mandatory module list", e);
		}

		return mandatoryModuleIds;
	}

	/**
	 * <pre>
	 * Gets the module that should handle a path. The path you pass in should be a module id (in
	 * path format, i.e. /ui/springmvc, not ui.springmvc) followed by a resource. Something like
	 * the following:
	 *   /ui/springmvc/css/ui.css
	 *
	 * The first running module out of the following would be returned:
	 *   ui.springmvc.css
	 *   ui.springmvc
	 *   ui
	 * </pre>
	 * <p>
	 * <strong>Should</strong> handle ui springmvc css ui dot css when ui dot springmvc module is
	 * running<br/>
	 * <strong>Should</strong> handle ui springmvc css ui dot css when ui module is running<br/>
	 * <strong>Should</strong> return null for ui springmvc css ui dot css when no relevant module is
	 * running
	 *
	 * @param path
	 * @return the running module that matches the most of the given path
	 */
	public static Module getModuleForPath(String path) {
		int ind = path.lastIndexOf('/');
		if (ind <= 0) {
			throw new IllegalArgumentException(
			        "Input must be /moduleId/resource. Input needs a / after the first character: " + path);
		}
		String moduleId = path.startsWith("/") ? path.substring(1, ind) : path.substring(0, ind);
		moduleId = moduleId.replace('/', '.');
		// iterate over progressively shorter module ids
		while (true) {
			Module mod = ModuleFactory.getStartedModuleById(moduleId);
			if (mod != null) {
				return mod;
			}
			// try the next shorter module id
			ind = moduleId.lastIndexOf('.');
			if (ind < 0) {
				break;
			}
			moduleId = moduleId.substring(0, ind);
		}
		return null;
	}

	/**
	 * Takes a global path and returns the local path within the specified module. For example calling
	 * this method with the path "/ui/springmvc/css/ui.css" and the ui.springmvc module, you would get
	 * "/css/ui.css".
	 * <p>
	 * <strong>Should</strong> handle ui springmvc css ui dot css example
	 *
	 * @param module
	 * @param path
	 * @return local path
	 */
	public static String getPathForResource(Module module, String path) {
		if (path.startsWith("/")) {
			path = path.substring(1);
		}
		return path.substring(module.getModuleIdAsPath().length());
	}

	/**
	 * This loops over all FILES in this jar to get the package names. If there is an empty directory in
	 * this jar it is not returned as a providedPackage.
	 *
	 * @param file jar file to look into
	 * @return list of strings of package names in this jar
	 */
	public static Collection<String> getPackagesFromFile(File file) {
		return ModuleFileUtil.getPackagesFromFile(file);
	}

	/**
	 * Get a resource as from the module's api jar. Api jar should be in the omod's lib folder.
	 * <p>
	 * <strong>Should</strong> load file from api as input stream<br/>
	 * <strong>Should</strong> return null if api is not found<br/>
	 * <strong>Should</strong> return null if file is not found in api
	 *
	 * @param jarFile omod file loaded as jar
	 * @param moduleId id of the module
	 * @param version version of the module
	 * @param resource name of a resource from the api jar
	 * @return resource as an input stream or <code>null</code> if resource cannot be loaded
	 */
	public static InputStream getResourceFromApi(JarFile jarFile, String moduleId, String version, String resource) {
		return ModuleFileUtil.getResourceFromApi(jarFile, moduleId, version, resource);
	}

	/**
	 * Gets the root folder of a module's sources during development
	 *
	 * @param moduleId the module id
	 * @return the module's development folder is specified, else null
	 */
	public static File getDevelopmentDirectory(String moduleId) {
		return ModuleFileUtil.getDevelopmentDirectory(moduleId);
	}
}
