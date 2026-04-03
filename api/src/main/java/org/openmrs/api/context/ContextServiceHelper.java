/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.api.context;

import java.util.List;
import java.util.Properties;

import jakarta.mail.Authenticator;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;

import org.aopalliance.aop.Advice;
import org.openmrs.GlobalProperty;
import org.openmrs.api.APIException;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.CohortService;
import org.openmrs.api.ConceptService;
import org.openmrs.api.ConditionService;
import org.openmrs.api.DatatypeService;
import org.openmrs.api.DiagnosisService;
import org.openmrs.api.EncounterService;
import org.openmrs.api.FormService;
import org.openmrs.api.LocationService;
import org.openmrs.api.MedicationDispenseService;
import org.openmrs.api.ObsService;
import org.openmrs.api.OpenmrsService;
import org.openmrs.api.OrderService;
import org.openmrs.api.OrderSetService;
import org.openmrs.api.PatientService;
import org.openmrs.api.PersonService;
import org.openmrs.api.ProgramWorkflowService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.SerializationService;
import org.openmrs.api.UserService;
import org.openmrs.api.VisitService;
import org.openmrs.hl7.HL7Service;
import org.openmrs.logic.LogicService;
import org.openmrs.messagesource.MessageSourceService;
import org.openmrs.notification.AlertService;
import org.openmrs.notification.MessageException;
import org.openmrs.notification.MessagePreparator;
import org.openmrs.notification.MessageSender;
import org.openmrs.notification.MessageService;
import org.openmrs.notification.mail.MailMessageSender;
import org.openmrs.notification.mail.velocity.VelocityMessagePreparator;
import org.openmrs.scheduler.SchedulerService;
import org.openmrs.util.ConfigUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.Advisor;

/**
 * Static helper that encapsulates service-resolution and service-infrastructure logic extracted
 * from {@link Context}.
 * <p>
 * Every method is package-private (or private) and static so that only {@link Context} calls the
 * package-private entry points. {@link ServiceContext} instances are received as parameters; this
 * class never touches {@code Context}'s static state directly.
 *
 * @since 2.8.0
 */
final class ContextServiceHelper {

	private static final Logger log = LoggerFactory.getLogger(ContextServiceHelper.class);

	/** Cached mail session – double-checked locking via {@code volatile}. */
	private static volatile Session mailSession;

	private ContextServiceHelper() {
	}

	// ---- individual service accessors ----

	static ConceptService getConceptService(ServiceContext sc) {
		return sc.getConceptService();
	}

	static EncounterService getEncounterService(ServiceContext sc) {
		return sc.getEncounterService();
	}

	static LocationService getLocationService(ServiceContext sc) {
		return sc.getLocationService();
	}

	static ObsService getObsService(ServiceContext sc) {
		return sc.getObsService();
	}

	static PatientService getPatientService(ServiceContext sc) {
		return sc.getPatientService();
	}

	static CohortService getCohortService(ServiceContext sc) {
		return sc.getCohortService();
	}

	static PersonService getPersonService(ServiceContext sc) {
		return sc.getPersonService();
	}

	static ConditionService getConditionService(ServiceContext sc) {
		return sc.getConditionService();
	}

	static DiagnosisService getDiagnosisService(ServiceContext sc) {
		return sc.getDiagnosisService();
	}

	static MedicationDispenseService getMedicationDispenseService(ServiceContext sc) {
		return sc.getMedicationDispenseService();
	}

	static HL7Service getHL7Service(ServiceContext sc) {
		return sc.getHL7Service();
	}

	static UserService getUserService(ServiceContext sc) {
		return sc.getUserService();
	}

	static OrderService getOrderService(ServiceContext sc) {
		return sc.getOrderService();
	}

	static OrderSetService getOrderSetService(ServiceContext sc) {
		return sc.getOrderSetService();
	}

	static FormService getFormService(ServiceContext sc) {
		return sc.getFormService();
	}

	static SerializationService getSerializationService(ServiceContext sc) {
		return sc.getSerializationService();
	}

	static LogicService getLogicService(ServiceContext sc) {
		return sc.getLogicService();
	}

	static AdministrationService getAdministrationService(ServiceContext sc) {
		return sc.getAdministrationService();
	}

	static MessageSourceService getMessageSourceService(ServiceContext sc) {
		return sc.getMessageSourceService();
	}

	static SchedulerService getSchedulerService(ServiceContext sc) {
		return sc.getSchedulerService();
	}

	static AlertService getAlertService(ServiceContext sc) {
		return sc.getAlertService();
	}

	static ProgramWorkflowService getProgramWorkflowService(ServiceContext sc) {
		return sc.getProgramWorkflowService();
	}

	static VisitService getVisitService(ServiceContext sc) {
		return sc.getVisitService();
	}

	static ProviderService getProviderService(ServiceContext sc) {
		return sc.getProviderService();
	}

	static DatatypeService getDatatypeService(ServiceContext sc) {
		return sc.getDatatypeService();
	}

	// ---- message / mail infrastructure ----

	/**
	 * Returns the {@link MessageService}, lazily wiring the message preparator and sender when they are
	 * not yet set.
	 */
	static MessageService getMessageService(ServiceContext sc, Properties runtimeProperties) {
		MessageService ms = sc.getMessageService();
		try {
			if (ms.getMessagePreparator() == null) {
				ms.setMessagePreparator(getMessagePreparator());
			}

			if (ms.getMessageSender() == null) {
				ms.setMessageSender(getMessageSender(sc, runtimeProperties));
			}

		} catch (Exception e) {
			log.error("Unable to create message service due", e);
		}
		return ms;
	}

	/**
	 * Collects all mail-related properties from global properties, runtime properties and system
	 * properties (in increasing priority order).
	 */
	static Properties getMailProperties(ServiceContext sc, Properties runtimeProperties) {
		var p = new Properties();
		String prefix = "mail.";
		for (GlobalProperty gp : sc.getAdministrationService().getGlobalPropertiesByPrefix(prefix)) {
			// Historically, some mail properties defined with underscores, support these for legacy compatibility
			if (gp.getProperty().equals("mail.transport_protocol")) {
				p.setProperty("mail.transport.protocol", gp.getPropertyValue());
			} else if (gp.getProperty().equals("mail.smtp_host")) {
				p.setProperty("mail.smtp.host", gp.getPropertyValue());
			} else if (gp.getProperty().equals("mail.smtp_port")) {
				p.setProperty("mail.smtp.port", gp.getPropertyValue());
			} else if (gp.getProperty().equals("mail.smtp_auth")) {
				p.setProperty("mail.smtp.auth", gp.getPropertyValue());
			} else {
				p.setProperty(gp.getProperty(), gp.getPropertyValue());
			}
		}
		for (String runtimeProperty : runtimeProperties.stringPropertyNames()) {
			if (runtimeProperty.startsWith(prefix)) {
				p.setProperty(runtimeProperty, runtimeProperties.getProperty(runtimeProperty));
			}
		}
		for (String systemProperty : System.getProperties().stringPropertyNames()) {
			if (systemProperty.startsWith(prefix)) {
				p.setProperty(systemProperty, System.getProperty(systemProperty));
			}
		}
		return p;
	}

	/**
	 * Returns (and lazily creates) the Jakarta Mail {@link Session} used by the mail message sender.
	 * Uses double-checked locking on a {@code volatile} field.
	 */
	private static Session getMailSession(ServiceContext sc, Properties runtimeProperties) {
		if (mailSession == null) {
			synchronized (ContextServiceHelper.class) {
				if (mailSession == null) {
					Authenticator auth = new Authenticator() {

						@Override
						public PasswordAuthentication getPasswordAuthentication() {
							return new PasswordAuthentication(ConfigUtil.getProperty("mail.user"),
							        ConfigUtil.getProperty("mail.password"));
						}
					};
					mailSession = Session.getInstance(getMailProperties(sc, runtimeProperties), auth);
				}
			}
		}
		return mailSession;
	}

	private static MessageSender getMessageSender(ServiceContext sc, Properties runtimeProperties) {
		return new MailMessageSender(getMailSession(sc, runtimeProperties));
	}

	private static MessagePreparator getMessagePreparator() throws MessageException {
		return new VelocityMessagePreparator();
	}

	// ---- generic service resolution ----

	static <T> T getService(Class<? extends T> cls, ServiceContext sc) {
		return sc.getService(cls);
	}

	// ---- AOP management ----

	@SuppressWarnings("rawtypes")
	static void addAdvisor(Class cls, Advisor advisor, ServiceContext sc) {
		sc.addAdvisor(cls, advisor);
	}

	@SuppressWarnings("rawtypes")
	static void addAdvice(Class cls, Advice advice, ServiceContext sc) {
		sc.addAdvice(cls, advice);
	}

	@SuppressWarnings("rawtypes")
	static void removeAdvisor(Class cls, Advisor advisor, ServiceContext sc) {
		sc.removeAdvisor(cls, advisor);
	}

	@SuppressWarnings("rawtypes")
	static void removeAdvice(Class cls, Advice advice, ServiceContext sc) {
		sc.removeAdvice(cls, advice);
	}

	// ---- component registration ----

	static <T> List<T> getRegisteredComponents(Class<T> type, ServiceContext sc) {
		return sc.getRegisteredComponents(type);
	}

	static <T> T getRegisteredComponent(String beanName, Class<T> type, ServiceContext sc) throws APIException {
		return sc.getRegisteredComponent(beanName, type);
	}

	static List<OpenmrsService> getModuleOpenmrsServices(String modulePackage, ServiceContext sc) {
		return sc.getModuleOpenmrsServices(modulePackage);
	}

	// ---- classloader / refresh state ----

	static boolean isRefreshingContext(ServiceContext sc) {
		return sc.isRefreshingContext();
	}

	static void setUseSystemClassLoader(boolean useSystemClassLoader, ServiceContext sc) {
		sc.setUseSystemClassLoader(useSystemClassLoader);
	}

	static boolean isUseSystemClassLoader(ServiceContext sc) {
		return sc.isUseSystemClassLoader();
	}
}
