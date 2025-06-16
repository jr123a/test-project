package com.ips.bean;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.net.URLConnection;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;
import javax.faces.context.FacesContext;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.richfaces.event.FileUploadEvent;
import org.richfaces.exception.FileUploadException;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import com.ips.common.common.CustomLogger;
import com.ips.persistence.common.EmailText;
import com.ips.persistence.common.Emailer;
import com.ips.persistence.common.GenerateBarcodeUtil;
import com.ips.polocator.common.IppVo;
import com.ips.polocator.common.LocationVo;
import com.ips.entity.Person;
import com.ips.entity.PersonData;
import com.ips.entity.RefEmailType;
import com.ips.entity.RefSponsor;
import com.ips.entity.RefSponsorConfiguration;
import com.ips.entity.SponsorEmailValues;
import com.ips.entity.SponsorEmailValuesPK;
import com.ips.exception.IPSException;
import com.ips.proofing.VerifyAddressService;
import com.ips.service.AdminService;
import com.ips.service.RefEmailTypeService;
import com.ips.service.RefSponsorConfigurationService;
import com.ips.service.RefSponsorDataService;
import com.ips.service.SponsorEmailValuesService;
import com.ips.xml.generated.polocator.Error;
import com.ips.xml.generated.polocator.Location;

@ManagedBean(name = "sponsorEmailsAdmin")
@ViewScoped
public class SponsorEmailAdminBean extends IPSAdminController implements Serializable {
	private static final long serialVersionUID = 1L;
	private Long selectedSponsor;
	private Long selectedEmailType;
	private String greeting;
	private String body;
	private String closing;
	private String postscript;
	private String subject;
	private String expirationDays;
	private String imageExt;
	private String imageBase64String;
	private byte[] storedTempImage;
	private String previewEmail;
	private String sendEmail;
	private boolean includeUSPSLogo;
	private boolean includeAgencyLogo;
	private Map<String, Boolean> booleanPropertyMapping;
	private List<RefSponsor> sponsorList;
	private List<RefEmailType> emailTypeList;
	private boolean isInitialized = false;

	@PostConstruct
	public void init() {
		if (!isInitialized) {
			buildMap();
			buildSponsorList();
			setSelectedSponsor(sponsorList.isEmpty() ? null : sponsorList.get(0).getSponsorId());
			buildEmailList();
			setSelectedEmailType(emailTypeList.isEmpty() ? null : emailTypeList.get(0).getId());
			resetEmailFields();
			isInitialized = true;
		}
	}

	/**
	 * Sets the new selected sponsor when the dropdown is changed
	 * 
	 * @param vcEvent
	 */
	public void selectSponsor() {
		try {
			resetEmailFields();
			sysoutInfo();
		} catch (Exception e) {
			CustomLogger.error(this.getClass(), "Exception occurred when selecting new sponsor", e);
			setDatabaseError(true);
		}
	}

	private void sysoutInfo() {
		System.out.println("-----------------------------------------------------");
		System.out.println("selectSponsor::" + getSelectedSponsor());
		System.out.println("Loaded body::"+ getBody());
		System.out.println("Loaded greeting::"+ getGreeting());
		System.out.println("Loaded closing::"+ getClosing());
		System.out.println("Loaded postscript::"+ getPostscript());
		System.out.println("Loaded subject::"+ getSubject());
		System.out.println("Loaded expirationDays::"+ getExpirationDays());
		System.out.println("Loaded includeUSPSLogo::"+ isIncludeUSPSLogo());
		System.out.println("Loaded includeAgencyLogo::"+ isIncludeAgencyLogo());
		System.out.println("Loaded EmailType::"+ getSelectedEmailType());
		System.out.println("Loaded EmailTypeList::"+ getEmailTypeList());
		System.out.println("Loaded SponsorList::"+ getSponsorList());
		System.out.println("Loaded PreviewEmail::"+ getPreviewEmail());
		System.out.println("Loaded SendEmail::"+ getSendEmail());
		System.out.println("Loaded StoredTempImage::"+ getStoredTempImage()) ;
		System.out.println("-----------------------------------------------------");
	}

	public void selectEmailType() {
		try {
			resetEmailFields();
			sysoutInfo();
		} catch (Exception e) {
			CustomLogger.error(this.getClass(), "Exception occurred when selecting new email type", e);
			setDatabaseError(true);
		}
	}

	public String getSelectedSponsorName() {
		return getSponsorList().stream().filter(sp -> getSelectedSponsor() == sp.getSponsorId()).findFirst()
				.orElse(new RefSponsor()).getSponsorName();
	}

	public void confirmExpirationChange() {
		if (isSuccess()) {
			setSuccess(false);
		}

		if (getSelectedEmailType() == RefEmailType.OPTIN_INITIAL) {
			if (isBarcodeExpirationChange()) {
				resetEmailFields();
			} else {
				setBarcodeExpirationChange(!isBarcodeExpirationChange());
			}
		} else if (getSelectedEmailType() == RefEmailType.CONFIRMATION_PASSED) {
			if (isActivationCodeExpirationChange()) {
				resetEmailFields();
			} else {
				setActivationCodeExpirationChange(!isActivationCodeExpirationChange());
			}
		}
	}

	public void saveExpirationValue() {
		ServletContext ctx = (ServletContext) FacesContext.getCurrentInstance().getExternalContext().getContext();
		WebApplicationContext webAppContext = WebApplicationContextUtils.getWebApplicationContext(ctx);
		if (webAppContext != null) {
			RefSponsorConfigurationService refSponsorConfigService = webAppContext
					.getBean(RefSponsorConfigurationService.class);

			RefSponsorConfiguration config = null;
			String value = getExpirationDays();
			String name = "";

			if (isBarcodeExpirationChange()) {
				try {
					config = refSponsorConfigService.getConfigRecord(getSelectedSponsor().intValue(),
							RefSponsorConfiguration.IPP_OPTIN_EXPIRATION);
				} catch (Exception e) {
					CustomLogger.error(this.getClass(), "Value did not exist");
				}
				name = RefSponsorConfiguration.IPP_OPTIN_EXPIRATION;
			} else if (isActivationCodeExpirationChange()) {
				try {
					config = refSponsorConfigService.getConfigRecord(getSelectedSponsor().intValue(),
							RefSponsorConfiguration.IPP_ACTIVATION_EXPIRATION);
				} catch (Exception e) {
					CustomLogger.error(this.getClass(), "Value did not exist");
				}
				name = RefSponsorConfiguration.IPP_ACTIVATION_EXPIRATION;
			} else {
				return;
			}

			clearBooleanProperties();

			// sanitize value on server side so client can't disable JS sanitization
			if (!StringUtils.isNumeric(value)) {
				throw new NumberFormatException("Expiration value not numeric" + value);
			}

			try {
				if (config == null) {
					config = new RefSponsorConfiguration();
					config.setCreateDate(new Date());
					config.setValue(value);
					config.setName(name);
					config.setConfigurationId(refSponsorConfigService.getMostRecentConfigId() + 1);
					config.setSponsorId(getSelectedSponsor().intValue());
					refSponsorConfigService.create(config);
				} else {
					config.setValue(value);
					config.setUpdateDate(new Date());
					refSponsorConfigService.update(config);
				}

				setSuccess(true);
			} catch (Exception e) {
				CustomLogger.error(this.getClass(),
						"Exception occurred when trying to save optin expiration days value", e);
				setDatabaseError(true);
			}

			if (isSuccess()) {
				AdminService adminService = (AdminService) webAppContext.getBean("AdminService");
				RefSponsorDataService refSponsorDataService = (RefSponsorDataService) webAppContext
						.getBean("RefSponsorDataService");
				HttpServletRequest request = (HttpServletRequest) FacesContext.getCurrentInstance().getExternalContext()
						.getRequest();

				try {
					String newValue = "to " + value;
					adminService.ippEmailsAdminNotification((String) request.getSession().getAttribute("IVSToken"),
							new Timestamp(new Date().getTime()),
							getSelectedEmailType() == 1 ? "barcodeExpiration" : "activationExpiration", newValue,
							refSponsorDataService.findByPK(getSelectedSponsor()));
				} catch (IPSException e) {
					CustomLogger.error(this.getClass(),
							"Exception occurred when sending admin opt-in expiration notification change", e);
					setEmailError(true);
				}

			}
		} else {
			CustomLogger.error(this.getClass(),
					"Error: WebApplicationContext is null. Unable to retrieve AdminService.");
			goToPage(SYSTEM_ERROR_PAGE);
		}
	}

	public void confirmSendEmailChange() {
		if (isSuccess()) {
			setSuccess(false);
		}

		if (isSendEmailChange()) {
			resetEmailFields();
		} else {
			setSendEmailChange(!isSendEmailChange());
		}
	}

	public void saveSendEmailChange() {
		ServletContext ctx = (ServletContext) FacesContext.getCurrentInstance().getExternalContext().getContext();
		WebApplicationContext webAppContext = WebApplicationContextUtils.getWebApplicationContext(ctx);
		if (webAppContext != null) {
			RefSponsorConfigurationService refSponsorConfigService = webAppContext
					.getBean(RefSponsorConfigurationService.class);
			String configName = selectedEmailType == 1 ? RefSponsorConfiguration.SEND_OPTIN_EMAILS
					: RefSponsorConfiguration.SEND_CONFIRMATION_EMAILS;

			clearBooleanProperties();

			try {
				RefSponsorConfiguration sendEmailConfig = refSponsorConfigService
						.getConfigRecord(selectedSponsor.intValue(), configName);
				if (sendEmailConfig == null) {
					sendEmailConfig = new RefSponsorConfiguration();
					sendEmailConfig.setConfigurationId(refSponsorConfigService.getMostRecentConfigId() + 1);
					sendEmailConfig.setCreateDate(new Date());
					sendEmailConfig.setName(configName);
					sendEmailConfig.setSponsorId(selectedSponsor.intValue());
					sendEmailConfig.setValue("1".equalsIgnoreCase(sendEmail) ? "true" : "false");
					refSponsorConfigService.create(sendEmailConfig);
				} else {
					sendEmailConfig.setValue("1".equalsIgnoreCase(sendEmail) ? "true" : "false");
					sendEmailConfig.setUpdateDate(new Date());
					refSponsorConfigService.update(sendEmailConfig);
				}

				setSuccess(true);
			} catch (Exception e) {
				CustomLogger.error(this.getClass(), "Exception occurred updating send email config", e);
				setDatabaseError(true);
			}

			if (isSuccess()) {
				AdminService adminService = (AdminService) webAppContext.getBean("AdminService");
				RefSponsorDataService refSponsorDataService = (RefSponsorDataService) webAppContext
						.getBean("RefSponsorDataService");
				HttpServletRequest request = (HttpServletRequest) FacesContext.getCurrentInstance().getExternalContext()
						.getRequest();

				try {
					String newValue = "to " + ("1".equalsIgnoreCase(sendEmail) ? "true" : "false");
					adminService.ippEmailsAdminNotification((String) request.getSession().getAttribute("IVSToken"),
							new Timestamp(new Date().getTime()),
							getSelectedEmailType() == 1 ? "sendEmailOptin" : "sendEmailConfirmation", newValue,
							refSponsorDataService.findByPK(getSelectedSponsor()));
				} catch (IPSException e) {
					CustomLogger.error(this.getClass(),
							"Exception occurred when sending admin opt-in expiration notification change", e);
					setEmailError(true);
				}
			}
		} else {
			CustomLogger.error(this.getClass(),
					"Error: WebApplicationContext is null. Unable to retrieve AdminService.");
			goToPage(SYSTEM_ERROR_PAGE);
		}
	}

	public void confirmEmailChange() {
		if (isSuccess()) {
			setSuccess(false);
		}

		switch (getSelectedEmailType().intValue()) {
		case (int) RefEmailType.OPTIN_INITIAL:
			if (isOptinInitialEmailChange()) {
				resetEmailFields();
			} else {
				setOptinInitialEmailChange(!isOptinInitialEmailChange());
			}
			break;
		case (int) RefEmailType.OPTIN_REMINDER:
			if (isOptinReminderEmailChange()) {
				resetEmailFields();
			} else {
				setOptinReminderEmailChange(!isOptinReminderEmailChange());
			}
			break;
		case (int) RefEmailType.CONFIRMATION_PASSED:
			if (isConfirmationPassedEmailChange()) {
				resetEmailFields();
			} else {
				setConfirmationPassedEmailChange(!isConfirmationPassedEmailChange());
			}
			break;
		case (int) RefEmailType.CONFIRMATION_FAILED:
			if (isConfirmationFailedEmailChange()) {
				resetEmailFields();
			} else {
				setConfirmationFailedEmailChange(!isConfirmationFailedEmailChange());
			}
			break;
		case (int) RefEmailType.IAL2_CONFIRMATION_EMAIL:
			if (isIAL2EmailChange()) {
				resetEmailFields();
			} else {
				setIAL2EmailChange(!isIAL2EmailChange());
			}
			break;
		case (int) RefEmailType.IAL2_CONFIRMATION_PHYSICAL_LETTER:
			if (isIAL2LetterChange()) {
				resetEmailFields();
			} else {
				setIAL2LetterChange(!isIAL2LetterChange());
			}
			break;
		default:
			break;
		}
	}

	public void saveEmailValues() {
		clearBooleanProperties();

		ServletContext ctx = (ServletContext) FacesContext.getCurrentInstance().getExternalContext().getContext();
		WebApplicationContext webAppContext = WebApplicationContextUtils.getWebApplicationContext(ctx);
		if (webAppContext != null) {
			SponsorEmailValuesService sponsorEmailValuesService = (SponsorEmailValuesService) webAppContext
					.getBean("sponsorEmailValuesService");
			SponsorEmailValues sev = null;

			try {
				sev = sponsorEmailValuesService.getEmailBySponsorIdAndEmailType(getSelectedSponsor(),
						getSelectedEmailType());
			} catch (Exception e) {
				CustomLogger.error(this.getClass(),
						"Exception occurred in save email values when attempting to get values from db", e);
				setDatabaseError(true);
				return;
			}

			greeting = getGreeting();
			closing = getClosing();
			postscript = getPostscript();
			body = getBody();
			subject = getSubject();

			// sanitize script tag server side to prevent client side from disabling it;
			// replace strong and em tags (from HTML editor) with b and i tags to reduce
			// character count
			subject = subject.replace("<script>", "").replace("</script>", "");
			body = body.replace("<strong>", "<b>").replace("</strong>", "</b>").replace("<em>", "<i>")
					.replace("</em>", "</i>").replace("<script>", "").replace("</script>", "").replace("\r", "")
					.replace("\n", "");
			greeting = greeting.replace("<strong>", "<b>").replace("</strong>", "</b>").replace("<em>", "<i>")
					.replace("</em>", "</i>").replace("<script>", "").replace("</script>", "").replace("\r", "")
					.replace("\n", "");
			closing = closing.replace("<strong>", "<b>").replace("</strong>", "</b>").replace("<em>", "<i>")
					.replace("</em>", "</i>").replace("<script>", "").replace("</script>", "").replace("\r", "")
					.replace("\n", "");
			postscript = postscript.replace("<strong>", "<b>").replace("</strong>", "</b>").replace("<em>", "<i>")
					.replace("</em>", "</i>").replace("<script>", "").replace("</script>", "").replace("\r", "")
					.replace("\n", "");

			try {
				if (sev == null) {
					sev = new SponsorEmailValues();
					SponsorEmailValuesPK sevPK = new SponsorEmailValuesPK();
					sevPK.setEmailType(getSelectedEmailType());
					sevPK.setSponsorId(getSelectedSponsor());
					sev.setId(sevPK);
					sev.setCreateDate(new Date());
					sev.setEmailBody(body);
					sev.setSubject(subject);
					sev.setClosing(closing);
					sev.setGreeting(greeting);
					sev.setPostscript(postscript);
					sponsorEmailValuesService.create(sev);
				} else {
					sev.setUpdateDate(new Date());
					sev.setEmailBody(body);
					sev.setSubject(subject);
					sev.setClosing(closing);
					sev.setGreeting(greeting);
					sev.setPostscript(postscript);
					sponsorEmailValuesService.update(sev);
				}

				setSuccess(true);
			} catch (Exception e) {
				CustomLogger.error(this.getClass(), "Exception occurred when trying to save email values", e);
				setDatabaseError(true);
			}

			if (isSuccess()) {
				AdminService adminService = (AdminService) webAppContext.getBean("AdminService");
				RefSponsorDataService refSponsorDataService = (RefSponsorDataService) webAppContext
						.getBean("RefSponsorDataService");
				HttpServletRequest request = (HttpServletRequest) FacesContext.getCurrentInstance().getExternalContext()
						.getRequest();

				String action = "";
				switch (getSelectedEmailType().intValue()) {
				case (int) RefEmailType.OPTIN_INITIAL:
					action = "optinInitial";
					break;
				case (int) RefEmailType.OPTIN_REMINDER:
					action = "optinReminder";
					break;
				case (int) RefEmailType.CONFIRMATION_PASSED:
					action = "confirmationPassed";
					break;
				case (int) RefEmailType.CONFIRMATION_FAILED:
					action = "confirmationFailed";
					break;
				case (int) RefEmailType.IAL2_CONFIRMATION_EMAIL:
					action = "ial2ConfirmationEmail";
					break;
				case (int) RefEmailType.IAL2_CONFIRMATION_PHYSICAL_LETTER:
					action = "ial2ConfirmationLetter";
					break;
				default:
					break;
				}

				try {
					adminService.ippEmailsAdminNotification((String) request.getSession().getAttribute("IVSToken"),
							new Timestamp(new Date().getTime()), action, "",
							refSponsorDataService.findByPK(getSelectedSponsor()));
				} catch (IPSException e) {
					CustomLogger.error(this.getClass(),
							"Exception occurred when sending admin opt-in email notification change", e);
					setEmailError(true);
				}
			}
		} else {
			CustomLogger.error(this.getClass(),
					"Error: WebApplicationContext is null. Unable to retrieve sponsorEmailValuesService.");
			goToPage(SYSTEM_ERROR_PAGE);
		}
	}

	public void sendPreviewEmail() {
		ServletContext ctx = (ServletContext) FacesContext.getCurrentInstance().getExternalContext().getContext();
		WebApplicationContext webAppContext = WebApplicationContextUtils.getWebApplicationContext(ctx);
		if (webAppContext != null) {
			Emailer emailer = (Emailer) webAppContext.getBean("emailer");
			SponsorEmailValuesService sponsorEmailValuesService = (SponsorEmailValuesService) webAppContext
					.getBean("sponsorEmailValuesService");
			RefSponsorDataService refSponsorDataService = (RefSponsorDataService) webAppContext
					.getBean("RefSponsorDataService");
			RefSponsorConfigurationService refSponsorConfigService = webAppContext
					.getBean(RefSponsorConfigurationService.class);

			setPreviewEmailError(false);

			PersonData personData = new PersonData();
			personData.setFirstName("John");
			personData.setLastName("Doe");

			Person person = new Person();
			person.setPersonData(personData);

			RefSponsor sponsor = null;

			try {
				sponsor = refSponsorDataService.findByPK(getSelectedSponsor());
			} catch (Exception e) {
				CustomLogger.error(this.getClass(), "Exception occurred when getting ref sponsor for email preview", e);
				setPreviewEmailError(true);
				return;
			}

			person.setRefSponsor(sponsor);

			SponsorEmailValues dbSev = null;
			SponsorEmailValues localSev = new SponsorEmailValues();

			try {
				dbSev = sponsorEmailValuesService.getEmailBySponsorIdAndEmailType(getSelectedSponsor(),
						getSelectedEmailType());
			} catch (Exception e) {
				CustomLogger.error(this.getClass(), "Exception occurred when getting ref sponsor for email preview", e);
			}

			// reassign variables - this grabs the values from the page without the database
			// being updated
			greeting = getGreeting();
			closing = getClosing();
			postscript = getPostscript();
			subject = getSubject();
			body = getBody();
			includeUSPSLogo = isIncludeUSPSLogo();
			includeAgencyLogo = isIncludeAgencyLogo();

			localSev.setEmailBody(body);
			if (StringUtils.isBlank(localSev.getEmailBody())) {
				if (dbSev == null || StringUtils.isBlank(dbSev.getEmailBody())) {
					localSev.setEmailBody("");
				} else {
					localSev.setEmailBody(dbSev.getEmailBody());
				}
			}

			localSev.setSubject(subject);
			if (StringUtils.isBlank(localSev.getSubject())) {
				if (dbSev == null || StringUtils.isBlank(dbSev.getSubject())) {
					localSev.setSubject("");
				} else {
					localSev.setSubject(dbSev.getSubject());
				}
			}

			localSev.setClosing(closing);
			if (StringUtils.isBlank(localSev.getClosing())) {
				if (dbSev == null || StringUtils.isBlank(dbSev.getClosing())) {
					localSev.setClosing("");
				} else {
					localSev.setClosing(dbSev.getClosing());
				}
			}

			localSev.setGreeting(greeting);
			if (StringUtils.isBlank(localSev.getGreeting())) {
				if (dbSev == null || StringUtils.isBlank(dbSev.getGreeting())) {
					localSev.setGreeting("");
				} else {
					localSev.setGreeting(dbSev.getGreeting());
				}
			}

			localSev.setPostscript(postscript);
			if (StringUtils.isBlank(localSev.getPostscript())) {
				if (dbSev == null || StringUtils.isBlank(dbSev.getPostscript())) {
					localSev.setPostscript("");
				} else {
					localSev.setPostscript(dbSev.getPostscript());
				}
			}

			localSev.setIncludeAgencyLogo(includeAgencyLogo ? "y" : "n");
			localSev.setIncludeUSPSLogo(includeUSPSLogo ? "y" : "n");

			byte[] agencyLogo = null;
			if (getStoredTempImage() != null && getStoredTempImage().length > 0) {
				agencyLogo = getStoredTempImage();
			} else if (dbSev != null && dbSev.getLogo() != null && dbSev.getLogo().length > 0) {
				agencyLogo = dbSev.getLogo();
			}
			localSev.setLogo(agencyLogo);

			Integer days = null;
			String text = "";

			try {
				if (getSelectedEmailType() == RefEmailType.OPTIN_INITIAL
						|| getSelectedEmailType() == RefEmailType.OPTIN_REMINDER) {
					IppVo address = new IppVo();
					address.setAddressLine1("5737 Newgate Tavern Ct");
					address.setCity("Centreville");
					address.setStateProvince("VA");
					address.setPostalCode("20120");
					address.setEmail(getPreviewEmail());
					address.setRecordLocator("0000000000000000");
					address.setSponsorId(sponsor.getSponsorId());
					address.setSponsorCustReg(sponsor.isCustReg());
					address.setSponsorActiveIppClient(sponsor.isIppClientActive());
					address.setSponsorUsingFacilitySubList(sponsor.isClientUsingFacilitySublist());
					// Added for IAL2 transactions
					RefSponsorConfiguration optionEnabled = refSponsorConfigService
							.getConfigRecord((int) sponsor.getSponsorId(), RefSponsorConfiguration.IAL2_REQUIRED);
					if (optionEnabled != null && optionEnabled.getValue() != null) {
						address.setIal2Transaction(optionEnabled.getValue());
					} else {
						address.setIal2Transaction("");
					}

					GenerateBarcodeUtil barcode = new GenerateBarcodeUtil();
					String barcodeFile = barcode.generateBarcode(address.getRecordLocator());

					VerifyAddressService verifyAddressService = (VerifyAddressService) webAppContext
							.getBean("VerifyAddressService");
					List<Object> poLocations = verifyAddressService.getFilteredPOList(address);
					String locationsHtml = createLocationsHtml(poLocations);

					RefSponsorConfiguration dbConfig = refSponsorConfigService.getConfigRecord(
							getSelectedSponsor().intValue(), RefSponsorConfiguration.IPP_OPTIN_EXPIRATION);

					String barcodeExpiration = getExpirationDays();
					if (StringUtils.isBlank(barcodeExpiration)) {
						if (dbConfig != null && StringUtils.isNotBlank(dbConfig.getValue())) {
							days = Integer.valueOf(dbConfig.getValue());
						} else {
							days = 0;
						}
					} else {
						days = Integer.valueOf(barcodeExpiration);
					}

					if (getSelectedSponsor() == RefSponsor.SPONSOR_ID_CUSTREG) {
						text = EmailText.getHTMLTextIPPFacility(address, locationsHtml,
								getSelectedEmailType() == RefEmailType.OPTIN_REMINDER, days);
						localSev.setSubject(EmailText.IPP_SUBJECT);
						emailer.sendIppEmail(barcodeFile, address.getEmail(), text, localSev.getSubject(),
								getSelectedSponsor());
					} else {
						text = EmailText.getHTMLOptinSponsorText(days, locationsHtml, address, localSev, person,
								new Timestamp(new Date().getTime()), "0000000000000000");
						emailer.sendEmail(barcodeFile, address.getEmail(), text, localSev.getSubject(),
								localSev.getLogo());
					}
				} else {
					if (getSelectedSponsor() == RefSponsor.SPONSOR_ID_CUSTREG) {
						text = EmailText.getPassedOrFailedIppEmail("0000000000000000",
								getSelectedEmailType() == RefEmailType.CONFIRMATION_PASSED);
						localSev.setSubject(getSelectedEmailType() == RefEmailType.CONFIRMATION_PASSED
								? "United States Postal Service In-Person Proofing Success"
								: "United States Postal Service Identity Validation Error");
					} else {
						RefSponsorConfiguration dbConfig = refSponsorConfigService.getConfigRecord(
								getSelectedSponsor().intValue(), RefSponsorConfiguration.IPP_ACTIVATION_EXPIRATION);

						String activationCodeExpiration = getExpirationDays();
						if (StringUtils.isBlank(activationCodeExpiration)) {
							if (dbConfig != null && StringUtils.isNotBlank(dbConfig.getValue())) {
								days = Integer.valueOf(dbConfig.getValue());
							} else {
								days = 0;
							}
						} else {
							days = Integer.valueOf(activationCodeExpiration);
						}

						StringBuilder logoHTML = new StringBuilder();
						boolean includeUSPSLogo = (selectedEmailType == RefEmailType.IAL2_CONFIRMATION_PHYSICAL_LETTER
								&& this.includeUSPSLogo)
								|| selectedEmailType != RefEmailType.IAL2_CONFIRMATION_PHYSICAL_LETTER;
						if (includeUSPSLogo && !localSev.getGreeting().contains("~USPS LOGO~")) {
							logoHTML.append(
									"<table border=\"0\" cellpadding=\"1\" cellspacing=\"1\" style=\"width: 100%;\"><tbody><tr><td>~USPS LOGO~</td>");
						}
						if (includeAgencyLogo && !localSev.getGreeting().contains("~LOGO~")) {
							if (logoHTML.length() > 0) {
								logoHTML.append("<td style=\"text-align: right;\">~LOGO~</td></tr></tbody></table>");
							} else {
								logoHTML.append(
										"<table border=\"0\" cellpadding=\"1\" cellspacing=\"1\" style=\"width: 100%;\"><tbody><tr><td>~LOGO~</td></tr></tbody></table>");
							}
						}

						if (logoHTML.length() > 0
								&& selectedEmailType == RefEmailType.IAL2_CONFIRMATION_PHYSICAL_LETTER) {
							localSev.setGreeting(logoHTML.toString() + localSev.getGreeting());
						}

						text = EmailText.getSponsorPassedOrFailedIppEmailTemplate(localSev, "00000000", days, person,
								new Timestamp(new Date().getTime()), new Timestamp(new Date().getTime()),
								"0000000000000000", "CENTREVILLE", "CENTREVILLE, VA");
					}

					if (selectedEmailType == RefEmailType.IAL2_CONFIRMATION_PHYSICAL_LETTER && !includeAgencyLogo) {
						localSev.setLogo(null);
					}

					emailer.sendHtmlEmail(new String[] { getPreviewEmail() }, text, localSev.getSubject(),
							includeUSPSLogo, localSev.getLogo());
				}
			} catch (Exception e) {
				CustomLogger.error(this.getClass(), "Exception occurred when attempting to send preview email", e);
				setPreviewEmailError(true);
			}
		} else {
			CustomLogger.error(this.getClass(),
					"Error: WebApplicationContext is null. Unable to retrieve necessary services.");
			goToPage(SYSTEM_ERROR_PAGE);
		}
	}

	private String createLocationsHtml(List<Object> poLocatorList) {
		List<Location> locationList = new ArrayList<>();
		boolean poListError = false;

		for (Object obj : poLocatorList) {
			if (obj != null) {
				if (obj instanceof Location) {
					locationList.add((Location) obj);
				} else if (obj instanceof Error) {
					poListError = true;
					break;
				}
			}
		}

		ServletContext ctx = (ServletContext) FacesContext.getCurrentInstance().getExternalContext().getContext();
		WebApplicationContext webAppContext = WebApplicationContextUtils.getWebApplicationContext(ctx);
		if (webAppContext != null) {
			VerifyAddressService verifyAddressService = (VerifyAddressService) webAppContext
					.getBean("VerifyAddressService");

			List<LocationVo> locsVo = new ArrayList<>();

			try {
				locsVo = verifyAddressService.getLocationVo(locationList);
			} catch (ParseException e) {
				CustomLogger.error(this.getClass(), "Error occurred when parsing PO locations.", e);
			} catch (Exception e) {
				CustomLogger.error(this.getClass(), "Error occurred when processing the response.", e);
			}

			return EmailText.convertLocsToHtml(locsVo, poListError);
		} else {
			CustomLogger.error(this.getClass(), "Error: WebApplicationContext is null.");
			goToPage(SYSTEM_ERROR_PAGE);
			return null;
		}
	}

	public void storeUploadImage(FileUploadEvent fileUploadEvent) {
		try {
			setStoredTempImage(fileUploadEvent.getUploadedFile().getData());
		} catch (FileUploadException e) {
			CustomLogger.error(this.getClass(), "Exception occurred when trying to upload new logo", e);
			setDatabaseError(true);
		}
	}

	public void confirmLogoChange() {
		if (isSuccess()) {
			setSuccess(false);
		}

		if (isLogoConfirmChange()) {
			setStoredTempImage(null);
			resetEmailFields();
		} else {
			setLogoConfirmChange(!isLogoConfirmChange());
		}
	}

	public void confirmLogoDelete() {
		if (isSuccess()) {
			setSuccess(false);
		}

		setLogoDeleteChange(!isLogoDeleteChange());
	}

	public void modifyStoredImage() {
		ServletContext ctx = (ServletContext) FacesContext.getCurrentInstance().getExternalContext().getContext();
		WebApplicationContext webAppContext = WebApplicationContextUtils.getWebApplicationContext(ctx);
		if (webAppContext != null) {
			SponsorEmailValuesService sponsorEmailValuesService = (SponsorEmailValuesService) webAppContext
					.getBean("sponsorEmailValuesService");
			SponsorEmailValues sev = null;

			try {
				sev = sponsorEmailValuesService.getEmailBySponsorIdAndEmailType(getSelectedSponsor(),
						getSelectedEmailType());

				if (isLogoConfirmChange()) {
					if (sev == null) {
						sev = new SponsorEmailValues();
						SponsorEmailValuesPK sevPK = new SponsorEmailValuesPK();
						sevPK.setEmailType(getSelectedEmailType());
						sevPK.setSponsorId(getSelectedSponsor());
						sev.setId(sevPK);
						sev.setCreateDate(new Date());
						if (selectedEmailType == RefEmailType.IAL2_CONFIRMATION_PHYSICAL_LETTER) {
							sev.setIncludeAgencyLogo(includeAgencyLogo ? "y" : "n");
							sev.setIncludeUSPSLogo(includeUSPSLogo ? "y" : "n");
						} else {
							sev.setIncludeAgencyLogo(null);
							sev.setIncludeUSPSLogo(null);
						}
						sponsorEmailValuesService.create(sev);
						sev.setLogo(getStoredTempImage());
					} else {
						if (selectedEmailType == RefEmailType.IAL2_CONFIRMATION_PHYSICAL_LETTER) {
							sev.setIncludeAgencyLogo(includeAgencyLogo ? "y" : "n");
							sev.setIncludeUSPSLogo(includeUSPSLogo ? "y" : "n");
						} else {
							sev.setIncludeAgencyLogo(null);
							sev.setIncludeUSPSLogo(null);
						}
						sev.setUpdateDate(new Date());
						sev.setLogo(getStoredTempImage() == null && sev.getLogo() != null ? sev.getLogo()
								: getStoredTempImage());
					}

					sponsorEmailValuesService.update(sev);

					boolean updatePairedSEV = true;
					// store the logo in the paired email (opt-in initial with reminder,
					// confirmation passed with failed)
					if (selectedEmailType == RefEmailType.OPTIN_INITIAL) {
						sev = sponsorEmailValuesService.getEmailBySponsorIdAndEmailType(selectedSponsor,
								RefEmailType.OPTIN_REMINDER);
					} else if (selectedEmailType == RefEmailType.CONFIRMATION_PASSED) {
						sev = sponsorEmailValuesService.getEmailBySponsorIdAndEmailType(selectedSponsor,
								RefEmailType.CONFIRMATION_FAILED);
					} else {
						updatePairedSEV = false;
					}

					if (updatePairedSEV) {
						if (sev == null) {
							sev = new SponsorEmailValues();
							SponsorEmailValuesPK sevPK = new SponsorEmailValuesPK();
							sevPK.setEmailType(
									getSelectedEmailType() == RefEmailType.OPTIN_INITIAL ? RefEmailType.OPTIN_REMINDER
											: RefEmailType.CONFIRMATION_FAILED);
							sevPK.setSponsorId(getSelectedSponsor());
							sev.setId(sevPK);
							sev.setCreateDate(new Date());
							sponsorEmailValuesService.create(sev);
							sev.setLogo(getStoredTempImage());
						} else {
							sev.setUpdateDate(new Date());
							sev.setLogo(getStoredTempImage() == null && sev.getLogo() != null ? sev.getLogo()
									: getStoredTempImage());
						}

						sponsorEmailValuesService.update(sev);
					}
				} else if (isLogoDeleteChange()) {
					if (sev != null) {
						sev.setLogo(null);
						sponsorEmailValuesService.update(sev);
					}

					boolean updatePairedSEV = true;
					// delete the logo in the paired email (opt-in initial with reminder,
					// confirmation passed with failed)
					if (selectedEmailType == RefEmailType.OPTIN_INITIAL) {
						sev = sponsorEmailValuesService.getEmailBySponsorIdAndEmailType(selectedSponsor,
								RefEmailType.OPTIN_REMINDER);
					} else if (selectedEmailType == RefEmailType.CONFIRMATION_PASSED) {
						sev = sponsorEmailValuesService.getEmailBySponsorIdAndEmailType(selectedSponsor,
								RefEmailType.CONFIRMATION_FAILED);
					} else {
						updatePairedSEV = false;
					}

					if (updatePairedSEV && sev != null) {
						sev.setLogo(null);
						sponsorEmailValuesService.update(sev);
					}
				}

				resetEmailFields();
				setSuccess(true);
			} catch (Exception e) {
				CustomLogger.error(this.getClass(), "Exception occurred when saving/deleting logo", e);
				setDatabaseError(true);
			}

			if (isSuccess()) {
				AdminService adminService = (AdminService) webAppContext.getBean("AdminService");
				RefSponsorDataService refSponsorDataService = (RefSponsorDataService) webAppContext
						.getBean("RefSponsorDataService");
				HttpServletRequest request = (HttpServletRequest) FacesContext.getCurrentInstance().getExternalContext()
						.getRequest();

				String action = "";
				switch (selectedEmailType.intValue()) {
				case (int) RefEmailType.OPTIN_INITIAL:
					action = "optinLogo";
					break;
				case (int) RefEmailType.CONFIRMATION_PASSED:
					action = "confirmationLogo";
					break;
				case (int) RefEmailType.IAL2_CONFIRMATION_EMAIL:
					action = "ial2EmailLogo";
					break;
				case (int) RefEmailType.IAL2_CONFIRMATION_PHYSICAL_LETTER:
					action = "ial2LetterLogo";
					break;
				default:
					break;
				}

				try {
					adminService.ippEmailsAdminNotification((String) request.getSession().getAttribute("IVSToken"),
							new Timestamp(new Date().getTime()), action, "",
							refSponsorDataService.findByPK(getSelectedSponsor()));
				} catch (IPSException e) {
					CustomLogger.error(this.getClass(),
							"Exception occurred when sending admin logo notification change", e);
					setEmailError(true);
				}
			}
		} else {
			CustomLogger.error(this.getClass(), "Error: WebApplicationContext is null.");
			goToPage(SYSTEM_ERROR_PAGE);
		}
	}

	private byte[] encodeBytesToBase64(byte[] logo) {
		if (logo == null || logo.length == 0) {
			return new byte[0];
		}

		return Base64.encodeBase64(logo);
	}

	private String determineImageExtension(byte[] image) throws IOException {
		String imageExtension = "";
		if (image == null) {
			return imageExtension;
		}

		try (ByteArrayInputStream is = new ByteArrayInputStream(image)) {
			imageExtension = URLConnection.guessContentTypeFromStream(is);
		}

		// default to png. A default must be done so internet explorer can properly
		// decode base64 images
		return StringUtils.isBlank(imageExtension) ? "png" : imageExtension;
	}

	public void resetEmailFields() {
		clearBooleanProperties();

		ServletContext ctx = (ServletContext) FacesContext.getCurrentInstance().getExternalContext().getContext();
		WebApplicationContext webAppContext = WebApplicationContextUtils.getWebApplicationContext(ctx);
		if (webAppContext != null) {
			RefSponsorConfigurationService refSponsorConfigService = webAppContext
					.getBean(RefSponsorConfigurationService.class);
			SponsorEmailValuesService sponsorEmailValuesService = (SponsorEmailValuesService) webAppContext
					.getBean("sponsorEmailValuesService");

			SponsorEmailValues sev = null;

			try {
				sev = sponsorEmailValuesService.getEmailBySponsorIdAndEmailType(getSelectedSponsor(),
						getSelectedEmailType());
			} catch (Exception e) {
				CustomLogger.error(this.getClass(), "Exception occurred when getting db values to reset email fields",
						e);
				setDatabaseError(true);
				return;
			}

			setEmailExists(sev != null && StringUtils.isNotBlank(sev.getEmailBody()));
			setBrandExists(sev != null && sev.getLogo() != null && sev.getLogo().length > 0);
			setSubjectExists(sev != null && StringUtils.isNotBlank(sev.getSubject()));
			setGreetingExists(sev != null && StringUtils.isNotBlank(sev.getGreeting()));
			setClosingExists(sev != null && StringUtils.isNotBlank(sev.getClosing()));
			setPostscriptExists(sev != null && StringUtils.isNotBlank(sev.getPostscript()));
			setBody(isEmailExists() ? sev.getEmailBody() : "");
			setSubject(isSubjectExists() ? sev.getSubject() : "");
			setImageExt("");
			setImageBase64String("");
			setExpirationDays("");
			setSendEmail("0");
			setGreeting(isGreetingExists() ? sev.getGreeting() : "");
			setPostscript(isPostscriptExists() ? sev.getPostscript() : "");
			setClosing(isClosingExists() ? sev.getClosing() : "");
			setIncludeAgencyLogo((sev != null && "y".equalsIgnoreCase(sev.getIncludeAgencyLogo()))
					|| selectedEmailType != RefEmailType.IAL2_CONFIRMATION_PHYSICAL_LETTER);
			setIncludeUSPSLogo(sev != null && "y".equalsIgnoreCase(sev.getIncludeUSPSLogo())
					|| selectedEmailType != RefEmailType.IAL2_CONFIRMATION_PHYSICAL_LETTER);

			if (getSelectedEmailType() == RefEmailType.OPTIN_INITIAL
					|| getSelectedEmailType() == RefEmailType.CONFIRMATION_PASSED) {
				String expiration = "";
				String expirationType = getSelectedEmailType() == RefEmailType.OPTIN_INITIAL
						? RefSponsorConfiguration.IPP_OPTIN_EXPIRATION
						: RefSponsorConfiguration.IPP_ACTIVATION_EXPIRATION;
				try {
					expiration = refSponsorConfigService
							.getConfigRecord(getSelectedSponsor().intValue(), expirationType).getValue();
				} catch (Exception e) {
					CustomLogger.debug(this.getClass(), "Value most likely doesn't exist");
				}

				String configName = selectedEmailType == 1 ? RefSponsorConfiguration.SEND_OPTIN_EMAILS
						: RefSponsorConfiguration.SEND_CONFIRMATION_EMAILS;
				RefSponsorConfiguration sendEmailConfig = refSponsorConfigService
						.getConfigRecord(selectedSponsor.intValue(), configName);
				setSendEmail(
						sendEmailConfig != null && "true".equalsIgnoreCase(sendEmailConfig.getValue()) ? "1" : "0");

				setBarcodeExpirationExists(
						getSelectedEmailType() == RefEmailType.OPTIN_INITIAL && StringUtils.isNotBlank(expiration));
				setActivationCodeExpirationExists(getSelectedEmailType() == RefEmailType.CONFIRMATION_PASSED
						&& StringUtils.isNotBlank(expiration));
				setExpirationDays(StringUtils.isNotBlank(expiration) ? expiration : "");
			}

			if (isBrandExists()) {
				try {
					byte[] base64String = encodeBytesToBase64(sev.getLogo());
					setImageExt(determineImageExtension(base64String));
					setImageBase64String(new String(base64String));
				} catch (Exception e) {
					CustomLogger.error(this.getClass(), "Exception occurred when encoding image to base64", e);
					setDatabaseError(true);
				}
			}
		} else {
			CustomLogger.error(this.getClass(), "Error: WebApplicationContext is null.");
			goToPage(SYSTEM_ERROR_PAGE);
		}
	}

	/**
	 * Builds list of external agency IPP sponsor clients for dropdown
	 * 
	 * @return
	 */
	private void buildSponsorList() {
		sponsorList = new ArrayList<>();

		ServletContext ctx = (ServletContext) FacesContext.getCurrentInstance().getExternalContext().getContext();
		WebApplicationContext webAppContext = WebApplicationContextUtils.getWebApplicationContext(ctx);
		if (webAppContext != null) {
			RefSponsorDataService refSponsorDataService = (RefSponsorDataService) webAppContext.getBean("RefSponsorDataService");

			try {
				sponsorList.add(refSponsorDataService.findBySponsorName(RefSponsor.SPONSOR_CUSTREG));
				refSponsorDataService.getAllExternalIppClients().forEach(client -> sponsorList.add(client));
			} catch (Exception e) {
				CustomLogger.error(this.getClass(), "Exception occurred when building RefSponsor list", e);
				setDatabaseError(true);
			}
		} else {
			CustomLogger.error(this.getClass(), "Error: WebApplicationContext is null.");
			goToPage(SYSTEM_ERROR_PAGE);
		}
	}

	/**
	 * Builds the list of email types for the user to switch between
	 * 
	 * @return
	 */
	private void buildEmailList() {
		emailTypeList = new ArrayList<>();

		ServletContext ctx = (ServletContext) FacesContext.getCurrentInstance().getExternalContext().getContext();
		WebApplicationContext webAppContext = WebApplicationContextUtils.getWebApplicationContext(ctx);
		if (webAppContext != null) {
			RefEmailTypeService refEmailTypeService = (RefEmailTypeService)webAppContext.getBean("refEmailTypeService");

			try {
				emailTypeList = refEmailTypeService.getAllTypes();
			} catch (Exception e) {
				CustomLogger.error(this.getClass(), "Exception occurred when building RefEmailType list", e);
				setDatabaseError(true);
			}
		} else {
			CustomLogger.error(this.getClass(), "Error: WebApplicationContext is null.");
			goToPage(SYSTEM_ERROR_PAGE);
		}
	}

	/**
	 * Builds the map of booleans for use on web page
	 * 
	 * @return
	 */
	private void buildMap() {
		booleanPropertyMapping = new HashMap<>();
		booleanPropertyMapping.put("databaseError", false);
		booleanPropertyMapping.put("emailError", false);
		booleanPropertyMapping.put("previewEmailError", false);
		booleanPropertyMapping.put("greetingExists", false);
		booleanPropertyMapping.put("closingExists", false);
		booleanPropertyMapping.put("postscriptExists", false);
		booleanPropertyMapping.put("emailExists", false);
		booleanPropertyMapping.put("brandExists", true);
		booleanPropertyMapping.put("subjectExists", true);
		booleanPropertyMapping.put("barcodeExpirationExists", true);
		booleanPropertyMapping.put("activationCodeExpirationExists", true);
		booleanPropertyMapping.put("logoConfirmChange", false);
		booleanPropertyMapping.put("logoDeleteChange", false);
		booleanPropertyMapping.put("optinInitialEmailChange", false);
		booleanPropertyMapping.put("optinReminderEmailChange", false);
		booleanPropertyMapping.put("confirmationPassedEmailChange", false);
		booleanPropertyMapping.put("confirmationFailedEmailChange", false);
		booleanPropertyMapping.put("barcodeExpirationChange", false);
		booleanPropertyMapping.put("activationCodeExpirationChange", false);
		booleanPropertyMapping.put("sendEmailChange", false);
		booleanPropertyMapping.put("ial2EmailChange", false);
		booleanPropertyMapping.put("ial2LetterChange", false);
		booleanPropertyMapping.put("success", false);
	}

	/**
	 * Resets boolean properties
	 */
	private void clearBooleanProperties() {
		setDatabaseError(false);
		setEmailError(false);
		setPreviewEmailError(false);
		setEmailExists(true);
		setGreetingExists(true);
		setClosingExists(true);
		setPostscriptExists(true);
		setBrandExists(true);
		setSubjectExists(true);
		setBarcodeExpirationExists(true);
		setActivationCodeExpirationExists(true);
		setLogoConfirmChange(false);
		setLogoDeleteChange(false);
		setOptinInitialEmailChange(false);
		setOptinReminderEmailChange(false);
		setConfirmationPassedEmailChange(false);
		setConfirmationFailedEmailChange(false);
		setIAL2EmailChange(false);
		setIAL2LetterChange(false);
		setBarcodeExpirationChange(false);
		setActivationCodeExpirationChange(false);
		setSendEmailChange(false);
		setSuccess(false);
	}

	public boolean isGreetingExists() {
		return booleanPropertyMapping.get("greetingExists");
	}

	public void setGreetingExists(boolean greetingExists) {
		booleanPropertyMapping.replace("greetingExists", greetingExists);
	}

	public boolean isClosingExists() {
		return booleanPropertyMapping.get("closingExists");
	}

	public void setClosingExists(boolean closingExists) {
		booleanPropertyMapping.replace("closingExists", closingExists);
	}

	public boolean isPostscriptExists() {
		return booleanPropertyMapping.get("postscriptExists");
	}

	public void setPostscriptExists(boolean postscriptExists) {
		booleanPropertyMapping.replace("postscriptExists", postscriptExists);
	}

	public boolean isIAL2EmailChange() {
		return getBooleanPropertyMapping().get("ial2EmailChange");
	}

	public void setIAL2EmailChange(boolean ial2EmailChange) {
		getBooleanPropertyMapping().replace("ial2EmailChange", ial2EmailChange);
	}

	public boolean isIAL2LetterChange() {
		return getBooleanPropertyMapping().get("ial2LetterChange");
	}

	public void setIAL2LetterChange(boolean ial2LetterChange) {
		getBooleanPropertyMapping().replace("ial2LetterChange", ial2LetterChange);
	}

	public boolean isSendEmailChange() {
		return getBooleanPropertyMapping().get("sendEmailChange");
	}

	public void setSendEmailChange(boolean sendEmailChange) {
		getBooleanPropertyMapping().replace("sendEmailChange", sendEmailChange);
	}

	public boolean isEmailExists() {
		return getBooleanPropertyMapping().get("emailExists");
	}

	public void setEmailExists(boolean emailExists) {
		getBooleanPropertyMapping().replace("emailExists", emailExists);
	}

	public boolean isActivationCodeExpirationExists() {
		return getBooleanPropertyMapping().get("activationCodeExpirationExists");
	}

	public void setActivationCodeExpirationExists(boolean activationCodeExpirationExists) {
		getBooleanPropertyMapping().replace("activationCodeExpirationExists", activationCodeExpirationExists);
	}

	public boolean isActivationCodeExpirationChange() {
		return getBooleanPropertyMapping().get("activationCodeExpirationChange");
	}

	public void setActivationCodeExpirationChange(boolean activationCodeExpirationChange) {
		getBooleanPropertyMapping().replace("activationCodeExpirationChange", activationCodeExpirationChange);
	}

	public boolean isSubjectExists() {
		return getBooleanPropertyMapping().get("subjectExists");
	}

	public void setSubjectExists(boolean subjectExists) {
		getBooleanPropertyMapping().replace("subjectExists", subjectExists);
	}

	public boolean isDatabaseError() {
		return getBooleanPropertyMapping().get("databaseError");
	}

	public void setDatabaseError(boolean databaseError) {
		getBooleanPropertyMapping().replace("databaseError", databaseError);
	}

	public boolean isEmailError() {
		return getBooleanPropertyMapping().get("emailError");
	}

	public void setEmailError(boolean emailError) {
		getBooleanPropertyMapping().replace("emailError", emailError);
	}

	public boolean isPreviewEmailError() {
		return getBooleanPropertyMapping().get("previewEmailError");
	}

	public void setPreviewEmailError(boolean previewEmailError) {
		getBooleanPropertyMapping().replace("previewEmailError", previewEmailError);
	}

	public boolean isBrandExists() {
		return getBooleanPropertyMapping().get("brandExists");
	}

	public void setBrandExists(boolean brandExists) {
		getBooleanPropertyMapping().replace("brandExists", brandExists);
	}

	public boolean isBarcodeExpirationExists() {
		return getBooleanPropertyMapping().get("barcodeExpirationExists");
	}

	public void setBarcodeExpirationExists(boolean barcodeExpirationExists) {
		getBooleanPropertyMapping().replace("barcodeExpirationExists", barcodeExpirationExists);
	}

	public boolean isLogoConfirmChange() {
		return getBooleanPropertyMapping().get("logoConfirmChange");
	}

	public void setLogoConfirmChange(boolean logoConfirmChange) {
		getBooleanPropertyMapping().replace("logoConfirmChange", logoConfirmChange);
	}

	public boolean isLogoDeleteChange() {
		return getBooleanPropertyMapping().get("logoDeleteChange");
	}

	public void setLogoDeleteChange(boolean logoDeleteChange) {
		getBooleanPropertyMapping().replace("logoDeleteChange", logoDeleteChange);
	}

	public boolean isOptinInitialEmailChange() {
		return getBooleanPropertyMapping().get("optinInitialEmailChange");
	}

	public void setOptinInitialEmailChange(boolean optinInitialEmailChange) {
		getBooleanPropertyMapping().replace("optinInitialEmailChange", optinInitialEmailChange);
	}

	public boolean isOptinReminderEmailChange() {
		return getBooleanPropertyMapping().get("optinReminderEmailChange");
	}

	public void setOptinReminderEmailChange(boolean optinReminderEmailChange) {
		getBooleanPropertyMapping().replace("optinReminderEmailChange", optinReminderEmailChange);
	}

	public boolean isConfirmationPassedEmailChange() {
		return getBooleanPropertyMapping().get("confirmationPassedEmailChange");
	}

	public void setConfirmationPassedEmailChange(boolean confirmationPassedEmailChange) {
		getBooleanPropertyMapping().replace("confirmationPassedEmailChange", confirmationPassedEmailChange);
	}

	public boolean isConfirmationFailedEmailChange() {
		return getBooleanPropertyMapping().get("confirmationFailedEmailChange");
	}

	public void setConfirmationFailedEmailChange(boolean confirmationFailedEmailChange) {
		getBooleanPropertyMapping().replace("confirmationFailedEmailChange", confirmationFailedEmailChange);
	}

	public boolean isBarcodeExpirationChange() {
		return getBooleanPropertyMapping().get("barcodeExpirationChange");
	}

	public void setBarcodeExpirationChange(boolean barcodeExpirationChange) {
		getBooleanPropertyMapping().replace("barcodeExpirationChange", barcodeExpirationChange);
	}

	public boolean isSuccess() {
		return getBooleanPropertyMapping().get("success");
	}

	public void setSuccess(boolean success) {
		getBooleanPropertyMapping().replace("success", success);
	}

	public Long getSelectedSponsor() {
		return selectedSponsor;
	}

	public void setSelectedSponsor(Long selectedSponsor) {
		this.selectedSponsor = selectedSponsor;
	}

	public Long getSelectedEmailType() {
		return selectedEmailType;
	}

	public void setSelectedEmailType(Long selectedEmailType) {
		this.selectedEmailType = selectedEmailType;
	}

	public String getImageExt() {
		return imageExt;
	}

	public void setImageExt(String imageExt) {
		this.imageExt = imageExt;
	}

	public String getImageBase64String() {
		return imageBase64String;
	}

	public void setImageBase64String(String imageBase64String) {
		this.imageBase64String = imageBase64String;
	}

	public List<RefSponsor> getSponsorList() {
		return sponsorList;
	}

	public List<RefEmailType> getEmailTypeList() {
		return emailTypeList;
	}

	private Map<String, Boolean> getBooleanPropertyMapping() {
		return booleanPropertyMapping;
	}

	public byte[] getStoredTempImage() {
		return storedTempImage;
	}

	public void setStoredTempImage(byte[] storedTempImage) {
		this.storedTempImage = storedTempImage;
	}

	public String getPreviewEmail() {
		return previewEmail;
	}

	public void setPreviewEmail(String previewEmail) {
		this.previewEmail = previewEmail;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}

	public String getSubject() {
		return subject;
	}

	public void setSubject(String subject) {
		this.subject = subject;
	}

	public String getExpirationDays() {
		return expirationDays;
	}

	public void setExpirationDays(String expirationDays) {
		this.expirationDays = expirationDays;
	}

	public String getSendEmail() {
		return sendEmail;
	}

	public void setSendEmail(String sendEmail) {
		this.sendEmail = sendEmail;
	}

	public String getPostscript() {
		return postscript;
	}

	public void setPostscript(String postscript) {
		this.postscript = postscript;
	}

	public String getClosing() {
		return closing;
	}

	public void setClosing(String closing) {
		this.closing = closing;
	}

	public String getGreeting() {
		return greeting;
	}

	public void setGreeting(String greeting) {
		this.greeting = greeting;
	}

	public boolean isIncludeUSPSLogo() {
		return includeUSPSLogo;
	}

	public void setIncludeUSPSLogo(boolean includeUSPSLogo) {
		this.includeUSPSLogo = includeUSPSLogo;
	}

	public boolean isIncludeAgencyLogo() {
		return includeAgencyLogo;
	}

	public void setIncludeAgencyLogo(boolean includeAgencyLogo) {
		this.includeAgencyLogo = includeAgencyLogo;
	}
}
