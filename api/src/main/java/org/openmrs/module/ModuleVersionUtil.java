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
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.openmrs.api.context.Context;
import org.openmrs.util.OpenmrsConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Static utility methods for module version comparison and checking, extracted from
 * {@link ModuleUtil}.
 *
 * @since 2.8.0
 */
final class ModuleVersionUtil {

	private static final Logger log = LoggerFactory.getLogger(ModuleVersionUtil.class);

	private ModuleVersionUtil() {
	}

	/**
	 * Checks if the current OpenMRS version is in an array of versions.
	 *
	 * @param versions the openmrs versions to be checked
	 * @return true if the current openmrs version matches one element in versions
	 */
	static boolean isOpenmrsVersionInVersions(String... versions) {
		return isVersionInVersions(OpenmrsConstants.OPENMRS_VERSION_SHORT, versions);
	}

	/**
	 * For testing of {@link #isOpenmrsVersionInVersions(String...)} only.
	 */
	static boolean isVersionInVersions(String version, String... versions) {
		if (versions == null || versions.length == 0) {
			return false;
		}

		boolean result = false;
		for (String candidateVersion : versions) {
			if (matchRequiredVersions(version, candidateVersion)) {
				result = true;
				break;
			}
		}
		return result;
	}

	/**
	 * Checks whether <code>version</code> falls within the given <code>versionRange</code>.
	 * <p>
	 * Supports wildcards ({@code *}), ranges ({@code -}), and comma-separated ranges.
	 *
	 * @param version openmrs version number to be compared
	 * @param versionRange value in the config file for required openmrs version
	 * @return true if the <code>version</code> is within the <code>versionRange</code>
	 */
	static boolean matchRequiredVersions(String version, String versionRange) {
		// There is a null check so no risk in keeping the literal on the right side
		if (StringUtils.isNotEmpty(versionRange)) {
			String[] ranges = versionRange.split(",");
			for (String range : ranges) {
				// need to externalize this string
				String separator = "-";
				if (range.indexOf("*") > 0 || range.indexOf(separator) > 0 && (!isVersionWithQualifier(range))) {
					// if it contains "*" or "-" then we must separate those two
					// assume it's always going to be two part
					// assign the upper and lower bound
					// if there's no "-" to split lower and upper bound
					// then assign the same value for the lower and upper
					String lowerBound = range;
					String upperBound = range;

					int indexOfSeparator = range.indexOf(separator);
					while (indexOfSeparator > 0) {
						lowerBound = range.substring(0, indexOfSeparator);
						upperBound = range.substring(indexOfSeparator + 1);
						if (upperBound.matches("^\\s?\\d+.*")) {
							break;
						}
						indexOfSeparator = range.indexOf(separator, indexOfSeparator + 1);
					}

					// only preserve part of the string that match the following format:
					// - xx.yy.*
					// - xx.yy.zz*
					lowerBound = StringUtils.remove(lowerBound, lowerBound.replaceAll("^\\s?\\d+[\\.\\d+\\*?|\\.\\*]+", ""));
					upperBound = StringUtils.remove(upperBound, upperBound.replaceAll("^\\s?\\d+[\\.\\d+\\*?|\\.\\*]+", ""));

					// if the lower contains "*" then change it to zero
					if (lowerBound.indexOf("*") > 0) {
						lowerBound = lowerBound.replaceAll("\\*", "0");
					}

					// if the upper contains "*" then change it to maxRevisionNumber
					if (upperBound.indexOf("*") > 0) {
						upperBound = upperBound.replaceAll("\\*", Integer.toString(Integer.MAX_VALUE));
					}

					int lowerReturn = compareVersionIgnoringQualifier(version, lowerBound);

					int upperReturn = compareVersionIgnoringQualifier(version, upperBound);

					if (lowerReturn < 0 || upperReturn > 0) {
						log.debug("Version " + version + " is not between " + lowerBound + " and " + upperBound);
					} else {
						return true;
					}
				} else {
					if (compareVersionIgnoringQualifier(version, range) < 0) {
						log.debug("Version " + version + " is below " + range);
					} else {
						return true;
					}
				}
			}
		} else {
			//no version checking if required version is not specified
			return true;
		}

		return false;
	}

	/**
	 * Throws a {@link ModuleException} if <code>version</code> does not satisfy
	 * <code>versionRange</code>.
	 *
	 * @param version openmrs version number to be compared
	 * @param versionRange value in the config file for required openmrs version
	 * @throws ModuleException if the version is not within the range
	 */
	static void checkRequiredVersion(String version, String versionRange) throws ModuleException {
		if (!matchRequiredVersions(version, versionRange)) {
			String ms = Context.getMessageSourceService().getMessage("Module.requireVersion.outOfBounds",
			    new String[] { versionRange, version }, Context.getLocale());
			throw new ModuleException(ms);
		}
	}

	/**
	 * Compare two version strings.
	 *
	 * @param versionA may include a qualifier like "-SNAPSHOT", may be null
	 * @param versionB may include a qualifier like "-SNAPSHOT", may be null
	 * @return negative if A &lt; B, positive if A &gt; B, 0 if equal
	 */
	static int compareVersion(String versionA, String versionB) {
		return compareVersion(versionA, versionB, false);
	}

	/**
	 * Compare two version strings, ignoring any qualifiers.
	 *
	 * @param versionA may include a qualifier like "-SNAPSHOT", may be null
	 * @param versionB may include a qualifier like "-SNAPSHOT", may be null
	 * @return negative if A &lt; B, positive if A &gt; B, 0 if equal
	 */
	static int compareVersionIgnoringQualifier(String versionA, String versionB) {
		return compareVersion(versionA, versionB, true);
	}

	private static int compareVersion(String versionA, String versionB, boolean ignoreQualifier) {
		try {
			if (versionA == null || versionB == null) {
				return 0;
			}

			var versionANumbers = new ArrayList<String>();
			var versionBNumbers = new ArrayList<String>();
			String qualifierSeparator = "-";

			// strip off any qualifier e.g. "-SNAPSHOT"
			int qualifierIndexA = versionA.indexOf(qualifierSeparator);
			if (qualifierIndexA != -1) {
				versionA = versionA.substring(0, qualifierIndexA);
			}

			// strip off any qualifier e.g. "-SNAPSHOT"
			int qualifierIndexB = versionB.indexOf(qualifierSeparator);
			if (qualifierIndexB != -1) {
				versionB = versionB.substring(0, qualifierIndexB);
			}

			Collections.addAll(versionANumbers, versionA.split("\\."));
			Collections.addAll(versionBNumbers, versionB.split("\\."));

			// match the sizes of the lists
			while (versionANumbers.size() < versionBNumbers.size()) {
				versionANumbers.add("0");
			}
			while (versionBNumbers.size() < versionANumbers.size()) {
				versionBNumbers.add("0");
			}

			for (int x = 0; x < versionANumbers.size(); x++) {
				String verAPartString = versionANumbers.get(x).strip();
				String verBPartString = versionBNumbers.get(x).strip();
				Long verAPart = NumberUtils.toLong(verAPartString, 0);
				Long verBPart = NumberUtils.toLong(verBPartString, 0);

				int ret = verAPart.compareTo(verBPart);
				if (ret != 0) {
					return ret;
				}
			}

			// At this point the version numbers are equal.
			if (!ignoreQualifier) {
				if (qualifierIndexA >= 0 && qualifierIndexB < 0) {
					return -1;
				} else if (qualifierIndexA < 0 && qualifierIndexB >= 0) {
					return 1;
				}
			}
		} catch (NumberFormatException e) {
			log.error("Error while converting a version/value to an integer: " + versionA + "/" + versionB, e);
		}

		// default return value if an error occurs or elements are equal
		return 0;
	}

	/**
	 * Checks for qualifier version (i.e "-SNAPSHOT", "-ALPHA" etc. after maven version conventions).
	 *
	 * @param version String like 1.9.2-SNAPSHOT
	 * @return true if version contains qualifier
	 */
	static boolean isVersionWithQualifier(String version) {
		Matcher matcher = Pattern.compile("(\\d+)\\.(\\d+)(\\.(\\d+))?(\\-([A-Za-z]+))").matcher(version);
		return matcher.matches();
	}
}
