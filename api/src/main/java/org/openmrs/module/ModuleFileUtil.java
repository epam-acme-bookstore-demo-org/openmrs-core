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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.openmrs.util.OpenmrsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Static utility methods for module file operations (jar expansion, URL fetching, package
 * scanning), extracted from {@link ModuleUtil}.
 *
 * @since 2.8.0
 */
final class ModuleFileUtil {

	private static final Logger log = LoggerFactory.getLogger(ModuleFileUtil.class);

	private ModuleFileUtil() {
	}

	/**
	 * Add the <code>inputStream</code> as a file in the modules repository.
	 *
	 * @param inputStream <code>InputStream</code> to load
	 * @param filename desired filename
	 * @return the written File
	 */
	static File insertModuleFile(InputStream inputStream, String filename) {
		File folder = ModuleUtil.getModuleRepository();

		// check if module filename is already loaded
		if (OpenmrsUtil.folderContains(folder, filename)) {
			throw new ModuleException(filename + " is already associated with a loaded module.");
		}

		File file = new File(folder.getAbsolutePath(), filename);

		try (FileOutputStream outputStream = new FileOutputStream(file)) {
			OpenmrsUtil.copyFile(inputStream, outputStream);
		} catch (IOException e) {
			throw new ModuleException("Can't create module file for " + filename, e);
		} finally {
			try {
				inputStream.close();
			} catch (Exception e) { /* pass */}
		}

		return file;
	}

	/**
	 * Expand the given <code>fileToExpand</code> jar to the <code>tmpModuleDir</code> directory.
	 *
	 * @param fileToExpand file pointing at a .jar
	 * @param tmpModuleDir directory in which to place the files
	 * @param name filename inside of the jar to look for and expand
	 * @param keepFullPath if true, will recreate entire directory structure
	 * @throws UnsupportedOperationException if an entry would be extracted outside of tmpModuleDir
	 */
	static void expandJar(File fileToExpand, File tmpModuleDir, String name, boolean keepFullPath) throws IOException {
		String docBase = tmpModuleDir.getAbsolutePath();
		log.debug("Expanding jar {}", fileToExpand);
		try (JarFile jarFile = new JarFile(fileToExpand)) {
			Enumeration<JarEntry> jarEntries = jarFile.entries();
			boolean foundName = (name == null);

			// loop over all of the elements looking for the match to 'name'
			while (jarEntries.hasMoreElements()) {
				JarEntry jarEntry = jarEntries.nextElement();
				if (name == null || jarEntry.getName().startsWith(name)) {
					String entryName = jarEntry.getName();
					// trim out the name path from the name of the new file
					if (!keepFullPath && name != null) {
						entryName = entryName.replaceFirst(name, "");
					}

					// if it has a slash, it's in a directory
					int last = entryName.lastIndexOf('/');
					if (last >= 0) {
						File parent = new File(docBase, entryName.substring(0, last));
						if (!parent.toPath().normalize().startsWith(docBase)) {
							throw new UnsupportedOperationException("Attempted to create directory '" + entryName
							        + "' rejected as it attempts to write outside the chosen directory. This may be the result of a zip-slip style attack.");
						}
						parent.mkdirs();
						log.debug("Creating parent dirs: " + parent.getAbsolutePath());
					}
					// we don't want to "expand" directories or empty names
					if (entryName.endsWith("/") || "".equals(entryName)) {
						continue;
					}
					try (InputStream input = jarFile.getInputStream(jarEntry)) {
						expand(input, docBase, entryName);
					}
					foundName = true;
				}
			}
			if (!foundName) {
				log.debug("Unable to find: " + name + " in file " + fileToExpand.getAbsolutePath());
			}

		} catch (IOException e) {
			log.warn("Unable to delete tmpModuleFile on error", e);
			throw e;
		}
	}

	/**
	 * Expand the given file in the given stream to a location (fileDir/name).
	 */
	private static void expand(InputStream input, String fileDir, String name) throws IOException {
		log.debug("expanding: {}", name);

		var file = new File(fileDir, name);

		if (!file.toPath().normalize().startsWith(fileDir)) {
			throw new UnsupportedOperationException("Attempted to write file '" + name
			        + "' rejected as it attempts to write outside the chosen directory. This may be the result of a zip-slip style attack.");
		}

		try (FileOutputStream outStream = new FileOutputStream(file)) {
			OpenmrsUtil.copyFile(input, outStream);
		}
	}

	/**
	 * Utility method to convert a {@link File} object to a local URL.
	 *
	 * @param file a file object
	 * @return absolute URL that points to the given file
	 * @throws MalformedURLException if file can't be represented as URL
	 */
	static URL file2url(final File file) throws MalformedURLException {
		if (file == null) {
			return null;
		}
		try {
			return file.getCanonicalFile().toURI().toURL();
		} catch (IOException | NoSuchMethodError ioe) {
			throw new MalformedURLException("Cannot convert: " + file.getName() + " to url");
		}
	}

	/**
	 * Downloads the contents of a URL and copies them to an InputStream.
	 *
	 * @param url the URL to fetch
	 * @return InputStream of contents
	 */
	static InputStream getURLStream(URL url) {
		InputStream in = null;
		try {
			URLConnection uc = url.openConnection();
			uc.setDefaultUseCaches(false);
			uc.setUseCaches(false);
			uc.setRequestProperty("Cache-Control", "max-age=0,no-cache");
			uc.setRequestProperty("Pragma", "no-cache");

			log.debug("Logging an attempt to connect to: " + url);

			in = openConnectionCheckRedirects(uc);
		} catch (IOException io) {
			log.warn("io while reading: " + url, io);
		}

		return in;
	}

	/**
	 * Convenience method to follow http to https redirects. Will follow a total of 5 redirects.
	 *
	 * @param c the {@link URLConnection} to open
	 * @return an {@link InputStream}
	 * @throws IOException if an I/O error occurs
	 */
	static InputStream openConnectionCheckRedirects(URLConnection c) throws IOException {
		boolean redir;
		int redirects = 0;
		InputStream in;
		do {
			if (c instanceof HttpURLConnection connection) {
				connection.setInstanceFollowRedirects(false);
			}
			// We want to open the input stream before getting headers
			// because getHeaderField() et al swallow IOExceptions.
			in = c.getInputStream();
			redir = false;
			if (c instanceof HttpURLConnection http) {
				int stat = http.getResponseCode();
				if (stat == 300 || stat == 301 || stat == 302 || stat == 303 || stat == 305 || stat == 307) {
					URL base = http.getURL();
					String loc = http.getHeaderField("Location");
					URL target = null;
					if (loc != null) {
						target = new URL(base, loc);
					}
					http.disconnect();
					// Redirection should be allowed only for HTTP and HTTPS
					// and should be limited to 5 redirects at most.
					if (target == null || !("http".equals(target.getProtocol()) || "https".equals(target.getProtocol()))
					        || redirects >= 5) {
						throw new SecurityException("illegal URL redirect");
					}
					redir = true;
					c = target.openConnection();
					redirects++;
				}
			}
		} while (redir);
		return in;
	}

	/**
	 * Downloads the contents of a URL and returns them as a String.
	 *
	 * @param url the URL to fetch
	 * @return String contents of the URL
	 */
	static String getURL(URL url) {
		InputStream in = null;
		ByteArrayOutputStream out = null;
		String output = "";
		try {
			in = getURLStream(url);
			if (in == null) {
				// skip this module if updateURL is not defined
				return "";
			}

			out = new ByteArrayOutputStream();
			OpenmrsUtil.copyFile(in, out);
			output = out.toString(StandardCharsets.UTF_8.name());
		} catch (IOException io) {
			log.warn("io while reading: " + url, io);
		} finally {
			try {
				in.close();
			} catch (Exception e) { /* pass */}
			try {
				out.close();
			} catch (Exception e) { /* pass */}
		}

		return output;
	}

	/**
	 * This loops over all FILES in this jar to get the package names.
	 *
	 * @param file jar file to look into
	 * @return list of strings of package names in this jar
	 */
	static Collection<String> getPackagesFromFile(File file) {

		// End early if we're given a non jar file
		if (!file.getName().endsWith(".jar")) {
			return Set.of();
		}

		var packagesProvided = new HashSet<String>();

		try (var jar = new JarFile(file)) {
			Enumeration<JarEntry> jarEntries = jar.entries();
			while (jarEntries.hasMoreElements()) {
				var jarEntry = jarEntries.nextElement();
				if (jarEntry.isDirectory()) {
					// skip over directory entries, we only care about files
					continue;
				}
				var name = jarEntry.getName();

				// Skip over some folders in the jar/omod
				if (name.startsWith("lib") || name.startsWith("META-INF") || name.startsWith("web/module")) {
					continue;
				}

				int indexOfLastSlash = name.lastIndexOf("/");
				if (indexOfLastSlash <= 0) {
					continue;
				}
				var packageName = name.substring(0, indexOfLastSlash).replaceAll("/", ".");

				if (packagesProvided.add(packageName) && log.isTraceEnabled()) {
					log.trace("Adding module's jarentry with package: " + packageName);
				}
			}
		} catch (IOException e) {
			log.error("Error while reading file: " + file.getAbsolutePath(), e);
		}

		return packagesProvided;
	}

	/**
	 * Get a resource from the module's api jar.
	 *
	 * @param jarFile omod file loaded as jar
	 * @param moduleId id of the module
	 * @param version version of the module
	 * @param resource name of a resource from the api jar
	 * @return resource as an input stream or <code>null</code>
	 */
	static InputStream getResourceFromApi(JarFile jarFile, String moduleId, String version, String resource) {
		String apiLocation = "lib/" + moduleId + "-api-" + version + ".jar";
		return getResourceFromInnerJar(jarFile, apiLocation, resource);
	}

	/**
	 * Load resource from a jar inside a jar.
	 */
	private static InputStream getResourceFromInnerJar(JarFile outerJarFile, String innerJarFileLocation, String resource) {
		File tempFile = null;
		FileOutputStream tempOut = null;
		JarFile innerJarFile = null;
		InputStream innerInputStream = null;
		try {
			tempFile = File.createTempFile("tempFile", "jar");
			tempOut = new FileOutputStream(tempFile);
			ZipEntry innerJarFileEntry = outerJarFile.getEntry(innerJarFileLocation);
			if (innerJarFileEntry != null) {
				IOUtils.copy(outerJarFile.getInputStream(innerJarFileEntry), tempOut);
				innerJarFile = new JarFile(tempFile);
				ZipEntry targetEntry = innerJarFile.getEntry(resource);
				if (targetEntry != null) {
					// clone InputStream to make it work after the innerJarFile is closed
					innerInputStream = innerJarFile.getInputStream(targetEntry);
					byte[] byteArray = IOUtils.toByteArray(innerInputStream);
					return new ByteArrayInputStream(byteArray);
				}
			}
		} catch (IOException e) {
			log.error(
			    "Unable to get '" + resource + "' from '" + innerJarFileLocation + "' of '" + outerJarFile.getName() + "'",
			    e);
		} finally {
			IOUtils.closeQuietly(tempOut);
			IOUtils.closeQuietly(innerInputStream);

			// close inner jar file before attempting to delete temporary file
			try {
				if (innerJarFile != null) {
					innerJarFile.close();
				}
			} catch (IOException e) {
				log.warn("Unable to close inner jarfile: " + innerJarFile, e);
			}

			// delete temporary file
			if (tempFile != null && !tempFile.delete()) {
				log.warn("Could not delete temporary jarfile: " + tempFile);
			}
		}
		return null;
	}

	/**
	 * Gets the root folder of a module's sources during development.
	 *
	 * @param moduleId the module id
	 * @return the module's development folder or null
	 */
	static File getDevelopmentDirectory(String moduleId) {
		String directory = System.getProperty(moduleId + ".development.directory");
		if (StringUtils.isNotBlank(directory)) {
			return new File(directory);
		}

		return null;
	}
}
