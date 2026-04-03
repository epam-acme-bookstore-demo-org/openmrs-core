/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.filter;

import java.io.StringReader;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.openmrs.module.Module;
import org.openmrs.module.ModuleException;
import org.openmrs.module.web.filter.ModuleFilterDefinition;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ModuleFilterDefinitionTest {

	public static Document getDocument(String xmlString) {
		try {
			DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
			Document doc = docBuilder.parse(new InputSource(new StringReader(xmlString)));
			return doc;
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * @see ModulefilterDefinition#retrieveFilterDefinitions
	 * @throws ModuleException
	 */
	@Test
	public void retrieveFilterDefinitions_shouldThrowModuleExceptionIfNoConfig() {
		Module module = new Module("test");
		ModuleException exception = assertThrows(ModuleException.class,
		    () -> ModuleFilterDefinition.retrieveFilterDefinitions(module));
		assertThat(exception.getMessage(), is("Unable to parse filters in module configuration."));
	}

	/**
	 * @see ModulefilterDefinition#retrieveFilterDefinitions
	 * @throws ModuleException
	 */
	@Test
	public void retrieveFilterDefinitions_shouldReturnEmptyListIfNoFilterNodes() {
		String xmlString = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<data></data>";
		Module module = new Module("test");
		module.setConfig(getDocument(xmlString));

		List<ModuleFilterDefinition> out = ModuleFilterDefinition.retrieveFilterDefinitions(module);
		assertThat(out, is(empty()));
	}

	/**
	 * @see ModulefilterDefinition#retrieveFilterDefinitions
	 * @throws ModuleException
	 */
	@Test
	public void retrieveFilterDefinitions_shouldReturnListOfSizeOneUsingInitParams() {
		String xmlString = """
		        <?xml version ="1.0" encoding="UTF-8"?>
		        <data><filter>
		        	<init-param>
		        		<param-name>test</param-name>
		        		<param-value>123</param-value>
		        	</init-param>
		        </filter></data>""";
		Module module = new Module("test");
		module.setConfig(getDocument(xmlString));

		List<ModuleFilterDefinition> out = ModuleFilterDefinition.retrieveFilterDefinitions(module);
		assertThat(out.size(), is(1));
		assertThat(out.getFirst().getInitParameters().get("test"), is("123"));
	}

	/**
	 * @see ModulefilterDefinition#retrieveFilterDefinitions
	 * @throws ModuleException
	 */
	@Test
	public void retrieveFilterDefinitions_shouldReturnListOfSizeOneUsingFilterNameAndClass() {
		String xmlString = """
		        <?xml version ="1.0" encoding="UTF-8"?>
		        <data><filter>
		        	<filter-name>test</filter-name>
		        	<filter-class>123</filter-class>
		        </filter></data>""";
		Module module = new Module("test");
		module.setConfig(getDocument(xmlString));

		List<ModuleFilterDefinition> out = ModuleFilterDefinition.retrieveFilterDefinitions(module);
		assertThat(out.size(), is(1));
		assertThat(out.getFirst().getFilterName(), is("test"));
		assertThat(out.getFirst().getFilterClass(), is("123"));
	}
}
