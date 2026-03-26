package com.ips.service;

import java.io.Serializable;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.equifax.smfa.response.ResponseStatusModel;
import com.google.gson.Gson;
import com.ibm.json.java.JSONArray;
import com.ibm.json.java.JSONObject;
import com.ips.common.common.CustomLogger;
import com.ips.common.common.DateTimeUtil;
import com.ips.common.common.Utils;
import com.ips.entity.Person;
import com.ips.entity.PersonData;
import com.ips.entity.PersonProofingStatus;
import com.ips.entity.PersonProofingStatusPK;
import com.ips.entity.RefApp;
import com.ips.entity.RefCustomerCategory;
import com.ips.entity.RefLoaLevel;
import com.ips.entity.RefOtpSupplier;
import com.ips.entity.RefOtpVelocity;
import com.ips.entity.RefRpStatus;
import com.ips.entity.RefSponsor;
import com.ips.entity.RefSponsorConfiguration;
import com.ips.entity.RefWorkflowApiType;
import com.ips.entity.RpDeviceReputationResponse;
import com.ips.entity.RpEvent;
import com.ips.entity.RpOtpAttempt;
import com.ips.entity.RpPhoneVerification;
import com.ips.entity.SponsorApplicationMap;
import com.ips.persistence.common.CommonAssessmentParamVo;
import com.ips.persistence.common.IPSConstants;
import com.ips.persistence.common.PersonVo;
import com.ips.persistence.common.PhoneVerificationParamVo;
import com.ips.persistence.common.SponsorConfigurationResponse;
import com.ips.proofing.CommonRestService;
import com.ips.proofing.EquifaxService;
import com.ips.proofing.EquifaxServiceImpl;
import com.ips.proofing.PhoneVerificationService;
import com.ips.proofing.ProofingService;
import com.ips.proofing.VerificationProviderService;
import com.ips.request.RemoteRequest;
import com.ips.response.InputValidationError;
import com.ips.response.RemoteResponse;
import com.ips.validation.ErrorMessage;
import com.ips.validation.RemoteProofingValidatedField;
import com.ips.validation.RemoteProofingValidation;

@Service("remoteSupportService")
@Transactional
public class RemoteSupportServiceImpl implements Serializable, RemoteSupportService {
    private final static long serialVersionUID = 1L;
    public final static String EXCEPTION_MSG_FMT = "Exception thrown for user: %s %s:";
    public final static String SUPPLIER_NOT_FOUND_MSG_FMT = "Phone verification supplier for customer ID %s not found";
    public final static String PV_FAILED_MSG_FMT = "Phone verification failed for customer %s";
    public final static String PASSCODE_EXPIRED_MSG_FMT = "Passcode for customer %s has expired";
    public final static String SPONSOR_NAME_CR = "CustomReg";
    public final static String SPONSOR_NAME_OS = "Operation Santa";
    public final static String SPONSOR_NAME_COA = "Change of Address";
    public final static String SPONSOR_CODE_CR = "CR";
    public final static String SPONSOR_CODE_OS = "OS";
    public final static String SPONSOR_CODE_COA = "COA";
    public final static String CHECK_DEVICE = "checkDevice";
    public final static String CHECK_DEVICE_PLUS = "checkDevicePlus";
    public final static String VERIFY_PHONE = "verifyPhone";
    public final static String REQUEST_PASSCODE = "requestPasscode";
    public final static String CONFIRM_PASSCODE = "confirmPasscode";
    public final static String RESEND_LINK = "resendLink";
    public final static String VALIDATE_LINK = "validateLink";
    public final static String RESP_VALUE_PHONE = "phone";
    public final static String REQ_VALUE_STATE = "state";
    public final static String ACCESS_CTL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    public final static String ACCESS_CTL_ALLOW_HEADERS = "Access-Control-Allow-Headers";
    public final static String CONTENT_TYPE_HEADER_VALUE = "Content-Type, Authorization";
    public final static String DECISION_FAILED = "FAILED";
    public final static String DECISION_FAIL = "Fail";
    public final static String VALUE_TRUE = "true";
    public final static String AMS_MODE_CONFIG_VALUE_TRUE = "True";
    public final static String APP_NAME_UNKNOWN = "unknown";
    public final static String SPONSOR_NAME_UNKNOWN = "unknown";
    public final static String STATUS_TOKEN_EXPIRED = "Token expired";
    public final static String USERID_IPS_OWN = "IPS_OWN";
    public final static String LOCKOUT_MSG_FMT = "Customer %s is locked out.";
    public final static String SIMPLE_DATE_FMT = "dd-MMM-yy hh.mm.ss a";
    public final static String MMDDYYYY_DATE_FMT = "MMddyyyy";
    public final static String REMOTE_SERVICES_MOD_FP = "04-24-2023-1";
	public final static String ALPHA_NUMERIC_WITH_SLASH_HASHTAG = "^[a-zA-Z0-9' -,.#\\/]+$";
	public final static String PROOFING_LEVEL_1_5 = "1.5";
	private final static String SEPARATOR = ", ";
	public final static String REASON_APPROVED_PHONE_VERIFICATION = "ApprovedPhoneVerification";
	public final static String REASON_FAILED_OTP_CONFIRMATION_MISMATCH = "FailedOTPConfirmationMismatch";
	public final static String REASON_FAILED_OTP_CONFIRMATION_OTHER = "FailedOTPConfirmationOther";
	public final static String REASON_FAILED_OTP_DELIVERY = "FailedOTPDelivery";
	public final static String REASON_FAILED_OTP_VELOCITY = "FailedOTPVelocity";
	public final static String REASON_FAILED_PHONE_VELOCITY = "FailedPhoneVelocity";
	public final static String REASON_FAILED_PHONE_VERIFICATION = "FailedPhoneVerification";
	public final static String REASON_FAILED_SUPPLIER_API_CALL = "FailedSupplierAPICall";
	public final static String REASON_DEVICE_DEFAULT_DECISION = "DeviceDefaultDecision";
	public final static String REASON_FAILED_SMFA_DELIVERY = "FailedSMFADelivery";
	public final static String REASON_FAILED_SMFA_VALIDATION = "FailedSMFAValidation";
	public final static String REASON_FAILED_SMFA_VELOCITY = "FailedSMFAVelocity";
	public final static String REASON_FAILED_SUPPLIER_SELECTION = "FailedSupplierSelection";
	public final static String REASON_PASSED_OTP_CONFIRMATION = "PassedOTPConfirmation";
	public final static String REASON_PASSED_SMFA_VALIDATION = "PassedSMFAValidation";
	public final static String REASON_SUCCESSS_OTP_DELIVERY = "SuccessfulOTPDelivery";
	public final static String REASON_SUCCESSSFUL_SMFA_DELIVERY = "SuccessfulSMFADelivery";
	public final static String QUALITY_TIMEOUT = "TIMEOUT";
	public final static String QUALITY_MISMATCH = "MISMATCH";
	public final static String QUALITY_TOO_MANY_ATTEMPTS = "TOO_MANY_SUBMIT_ATTEMPTS";
	public final static String ATTEMPT_TYPE_CONFIRM = "Confirm";
	public final static String ATTEMPT_TYPE_REQUEST = "Request";
	public final static String MFA_TYPE_PASSCODE = "Passcode";
	public final static String MFA_TYPE_SMFA_LINK = "SMFA Link";
	public final static String SUPPLIER_EQUIFAX_IDFS = "Equifax IDFS";
	public final static String SUPPLIER_EXPERIAN_CROSSCORE = "Experian CrossCore";
	public final static String SUPPLIER_LEXISNEXIS_RDP = "LexisNexis RDP";
    
 	@Autowired
    private CheckDeviceService checkDeviceService;
    @Autowired
    private CheckDevicePlusService checkDevicePlusService;
    @Autowired
    private CommonRestService commonRestService;
    @Autowired
    private EquifaxService equifaxService;
    @Autowired
    private OtpVelocityCheckService otpVelocityCheckService;
    @Autowired
    private PersonDataService personDataService;
    @Autowired
    private PhoneVerificationService phoneVerificationService;
    @Autowired
    private ProofingService proofingService;
    @Autowired
    private RefAppService refAppService;
    @Autowired
    private RefCustomerCategoryService refCustomerCategoryService;
    @Autowired
    private RefWorkflowApiTypeService refWorkflowApiTypeService;
    @Autowired
    private RefLoaLevelService refLoaLevelService;
    @Autowired
    private RefOtpSupplierDataService refOtpSupplierDataService;
    @Autowired
    private RefRpStatusDataService refRpStatusService;
    @Autowired
    private RefSponsorConfigurationService refSponsorConfigService;
    @Autowired
    private RefSponsorDataService refSponsorDataService;
    @Autowired
    private RefSponsorDataService refSponsorService;
    @Autowired
    private RpDeviceReputationResponseService rpDeviceReputationResponseService;
    @Autowired
    private RpEventDataService rpEventDataService;
    @Autowired
    private RpOtpAttemptDataService rpOtpAttemptDataService;  
    @Autowired
    private RpPhoneVerificationService rpPhoneVerificationService;
    @Autowired
    private SmfaVelocityCheckService smfaVelocityCheckService;
    @Autowired
    private SponsorApplicationMapService sponsorApplicationMapService;
    @Autowired
    private VerificationProviderService verificationProviderService;
    @Autowired
    private VerifyBusinessIdentityService verifyBusinessIdentityService;
    @Autowired
    private VerifyBusinessAddressService verifyBusinessAddressService;

	    
    public Response buildRequestValidationResponse(RemoteRequest remoteReq, PersonVo personVo, String origin) {
    	CustomLogger.enter(this.getClass());
 
    	// Validate the data that was passed in determining any missing or incorrect formatted values.
     	HashMap<String, InputValidationError> validationErrorMap = RemoteProofingValidation.validateRemoteProofingData(remoteReq);
     	InputValidationError inputValidationError = null;
     	
     	String assessmentCall = remoteReq.getAssessmentCall();
    	boolean checkDevicePlus = RemoteProofingServiceImpl.CHECK_DEVICE_PLUS.equalsIgnoreCase(assessmentCall);
    	boolean verifyBusiness = RemoteProofingServiceImpl.VERIFY_BUSINESS.equalsIgnoreCase(assessmentCall);
    	boolean businessApp = checkDevicePlus || verifyBusiness;
 		boolean hasSponsorAppInputError = false;

		ErrorMessage errorMessage = null;
    
 		String fieldName = RemoteProofingValidatedField.FIELD_SPONSOR_CODE;
 		String sponsorCode = remoteReq.getSponsorCode();

      	if (!validationErrorMap.containsKey(fieldName)) {
      		if (businessApp) {
	     		if (RefSponsor.SPONSOR_CODE_CR.equalsIgnoreCase(sponsorCode.toUpperCase())) {
	     			remoteReq.setSponsorName(RefSponsor.SPONSOR_CUSTREG);
			    }
	     		else {
	     			hasSponsorAppInputError = true;
	     		}
     		}
      		else {
     			if (RefSponsor.SPONSOR_CODE_COA.equalsIgnoreCase(sponsorCode.toUpperCase())
     					|| RefSponsor.SPONSOR_CODE_OS.equalsIgnoreCase(sponsorCode.toUpperCase())) {
     				remoteReq.setSponsorName(sponsorCode.toUpperCase());
			    }
     			else {
     				hasSponsorAppInputError = true;
     			}
     		}
 
     		if (hasSponsorAppInputError) {
				errorMessage = ErrorMessage.INVALID_CODE;
	       		inputValidationError = getInputValidationError(fieldName, sponsorCode, errorMessage.getFormattedErrorMessage("Sponsor", sponsorCode));   	
	    	    validationErrorMap.put(fieldName, inputValidationError);
		    }
     	}

    	fieldName = RemoteProofingValidatedField.FIELD_APP_CODE;
    
		if (businessApp && !validationErrorMap.containsKey(fieldName)) {
    		Map<String, String> appCodeNameMap = getAppCodeNameMap();
    		String appCode = remoteReq.getAppCode();
			String appName = appCodeNameMap.get(appCode.toUpperCase());
			
			if (appName != null) {
				remoteReq.setAppName(appName);
			}
			else {
				errorMessage = ErrorMessage.INVALID_CODE;
	       		inputValidationError = getInputValidationError(fieldName, appCode, errorMessage.getFormattedErrorMessage("Application", appCode));   	
	    	    validationErrorMap.put(fieldName, inputValidationError);
          		hasSponsorAppInputError = true;
			}
    	}

    	if (!hasSponsorAppInputError) {
    		String sponsorName =  remoteReq.getSponsorName();
    		String appName = remoteReq.getAppName();
    		RefSponsor refSponsor = null;
    		
    		try {
    			refSponsor = refSponsorService.findBySponsorName(sponsorName);
    		}
    		catch (Exception e) {
    	        //Set to default:
    			sponsorName = businessApp ? RefSponsor.SPONSOR_CUSTREG 
    					: (RefSponsor.SPONSOR_CODE_OS.equalsIgnoreCase(sponsorCode.toUpperCase()) ? 
    							RefSponsor.SPONSOR_OPERATION_SANTA : RefSponsor.SPONSOR_CHANGE_ADDRESS);
    			refSponsor = refSponsorService.findBySponsorName(sponsorName);
    			CustomLogger.info(this.getClass(), "Error occurred in obtaining sponsor for sponsorName:" + sponsorName);
    	    }
    		
	        if (refSponsor != null) {
	     	   	boolean hasSponsorDisabled = sponsorApplicationMapService.hasSponsorDisabled(refSponsor.getSponsorId(), 0L);
	    	   	if (hasSponsorDisabled) {
	 				errorMessage = ErrorMessage.SPONSOR_APP_DISABLED;
		       		inputValidationError = getInputValidationError(fieldName, remoteReq.getSponsorCode(), errorMessage.getFormattedErrorMessage("Sponsor"));   	
		    	    validationErrorMap.put(fieldName, inputValidationError);
	    	   	}
	    	   	else {
	    	        RefApp refApp = refAppService.findByAppName(appName);
	    	        
	    	        if (refApp != null) {
			        	boolean hasAppDisabled = sponsorApplicationMapService.hasSponsorDisabled(refSponsor.getSponsorId(), refApp.getAppId());

		        	   	if (hasAppDisabled) {
		     				errorMessage = ErrorMessage.SPONSOR_APP_DISABLED;
				       		inputValidationError = getInputValidationError(fieldName, remoteReq.getSponsorCode(), errorMessage.getFormattedErrorMessage("Sponsor"));   	
				    	    validationErrorMap.put(fieldName, inputValidationError);
		        	   	}
	    	        }
	    	   	}
	        }
    	}
       	
        if (!validationErrorMap.isEmpty()) {
        	if (businessApp) {
          		return buildCheckDevicePlusValidationResponse(remoteReq, personVo, validationErrorMap, fieldName, origin);
          	}
        	else {
	      		return  buildPhoneVerificationValidationResponse(remoteReq, personVo, validationErrorMap, fieldName, origin);
         	}
        }

        return null;       	
    }
    
    public Response buildCheckDevicePlusValidationResponse(RemoteRequest remoteReq, PersonVo personVo, HashMap<String, InputValidationError> validationErrorMap, 
    		String fieldName, String origin) {
		JSONArray validationErrorJSONArray = new JSONArray();
		
    	for (Map.Entry<String, InputValidationError> entry : validationErrorMap.entrySet()) {
            InputValidationError validationError = entry.getValue();

            JSONObject validationErrorJson = new JSONObject();
            validationErrorJson.put("fieldName", validationError.getFieldName());
            validationErrorJson.put("fieldValue", validationError.getFieldValue());
            validationErrorJson.put("errorMessage", validationError.getErrorMessage());
            validationErrorJSONArray.add(validationErrorJson);
        }
    	
    	JSONObject errorResponseJson = new JSONObject();
    	JSONObject statusJson = new JSONObject();
    	statusJson.put("reference", "IVS_Transaction");
    	statusJson.put("transactionStatus", IPSConstants.TRANSACTION_STATUS_FAILED);
    	
    	JSONArray reasonCodeJSONArray = new JSONArray();
    	JSONObject reasonCodeJSON = new JSONObject();
    	reasonCodeJSON.put("description", "Input Validation Error");
    	reasonCodeJSON.put("code", "input_validation_error");
    	reasonCodeJSONArray.add(reasonCodeJSON);
       
    	statusJson.put("transactionReasonCodes", reasonCodeJSONArray);
    	statusJson.put("inputValidationErrors", validationErrorJSONArray);
 
    	errorResponseJson.put("status", statusJson);
         	
        Response checkDevicePlusErrorResponse = buildCheckDevicePlusErrorResponse(null, errorResponseJson, origin);

      	return checkDevicePlusErrorResponse;
    }
    
    public Response buildPhoneVerificationValidationResponse(RemoteRequest remoteReq, PersonVo personVo, HashMap<String, 
    		InputValidationError> validationErrorMap, String fieldName, String origin) {
	  	StringBuilder missingFields = new StringBuilder();
	    StringBuilder badlyFormattedFields = new StringBuilder();
	    List<String> missingFieldList = new ArrayList<>();
	    List<String> badlyFormattedFieldList = new ArrayList<>();
	    
	    for (Map.Entry<String, InputValidationError> entry : validationErrorMap.entrySet()) {
	        InputValidationError validationError = entry.getValue();
	   		
	   		String entryfieldName = validationError.getFieldName();
	   		if (!"country".equalsIgnoreCase(entryfieldName)) {
	       		String errorMessageStr = validationError.getErrorMessage();
	       		boolean isMissingField = errorMessageStr.contains("required");
	           		
	       		if (isMissingField) {
	       			missingFieldList.add(entryfieldName);
	       		}
	       		else {
	       			badlyFormattedFieldList.add(entryfieldName);
	       		}
	   		}
	    }
	    
	    String errMsg = "";
	    ErrorMessage errorMessage = ErrorMessage.INVALID_FORMAT_FIELDS;
		
		int listSize = 0;
		int fieldCtr = 1;
		if (!badlyFormattedFieldList.isEmpty()) {
			listSize = badlyFormattedFieldList.size();
			for (String fldName : badlyFormattedFieldList) {
				if (badlyFormattedFields.length() > 0) {
					if (fieldCtr == listSize && fieldCtr > 1) {
						badlyFormattedFields.append(" and " + fldName);
					} else {
						badlyFormattedFields.append(SEPARATOR + fldName);
					}
				}
				else {
					badlyFormattedFields.append(fldName);
				}
	
				fieldCtr++;
			}
	     	String fieldList = badlyFormattedFields.toString();
	   		errorMessage.setMessagePlaceholders(fieldList);
	   		errMsg = errorMessage.getMessage();
	   		
			if (listSize > 1) {
				errMsg = errMsg.replace("contains", "contain");	       			
			}
		}
		else if (!missingFieldList.isEmpty()) {
			listSize = missingFieldList.size();
			for (String fldName : missingFieldList) {
				if (missingFields.length() > 0) {
					if (fieldCtr == listSize && fieldCtr > 1) {
						missingFields.append(" and " + fldName);
					} else {
						missingFields.append(SEPARATOR + fldName);
					}
				}
				else {
					missingFields.append(fldName);
				}
	
				fieldCtr++;
			}
	     	String fieldList = missingFields.toString();
	   		errorMessage.setMessagePlaceholders(fieldList);
	   		errMsg = errorMessage.getMessage();
	   		
			if (listSize > 1) {
				errMsg = errMsg.replace("contains", "contain");	       			
			}
		}
		
		if (badlyFormattedFields.length() == 0 && missingFields.length() == 0) {
			return null;
		}
	
	 	Response checkDeviceErrorResponse = buildErrorPVResponse(errorMessage.getHttpResponseCode(), errMsg, origin);
	 	saveDeviceReputationAssessmentErrorResponse(personVo, remoteReq, checkDeviceErrorResponse);
		
		return checkDeviceErrorResponse;
    }
    
    public Response checkDeviceReputation(String origin, Person person, PersonVo personVo, CommonAssessmentParamVo assessmentVo, RemoteRequest remoteReq) {
      	JSONObject deviceResponseJson = new JSONObject();
     	
    	try {
    		Gson g = new Gson();
           	String remoteReqStr = g.toJson(remoteReq);
           	assessmentVo.setRemoteRequest(remoteReqStr);
           	
        	deviceResponseJson = checkDeviceService.getDeviceReputationStatus(person, personVo, assessmentVo);

            return buildPVResponse(null, deviceResponseJson, origin);
         } catch (Exception e) {
           	return exceptionErrorResponse(personVo, null, "getting Device Reputation Status", origin, e);
         }
    }
     
	public Response assessDevicePlusEmailRisk(String origin, RemoteRequest remoteReq, Person person, PersonVo personVo, CommonAssessmentParamVo assessmentVo) {
    	CustomLogger.enter(this.getClass());
    	Response assessmentResponse = null;
     	
    	try {
      	    Gson g = new Gson();
           	String remoteReqStr = g.toJson(remoteReq);
           	assessmentVo.setRemoteRequest(remoteReqStr);
           	
          	JSONObject deviceEmailRiskRespJSON = checkDevicePlusService.getAssessDevicePlusEmailRiskResponse(person, personVo, assessmentVo);
    	
        	if (personVo.isCheckHighRiskAddress()) {
          		assessmentResponse = buildPVResponse(null, deviceEmailRiskRespJSON, origin);
        	}
        	else if (assessmentVo.isHighRiskAddress()) {
        		assessmentResponse = buildCommonResponse(null, deviceEmailRiskRespJSON, origin);
        	}
        	else if (assessmentVo.isDeviceProfilingDisabled()) {
        		assessmentResponse = buildCommonResponse(null, deviceEmailRiskRespJSON, origin);
            }
           	else {
           		assessmentResponse = buildCommonResponse(null, deviceEmailRiskRespJSON, origin);
            }
    	} catch (Exception e) {
        	 assessmentResponse = exceptionErrorResponse(personVo, null, "getting Device Reputation Status", origin, e);
        }

    	return assessmentResponse;
    }
    
    public Response getVerifyBusinessResponse(String origin, RemoteRequest remoteReq, Person person, PersonVo personVo, CommonAssessmentParamVo assessmentVo) {
    	    CustomLogger.enter(this.getClass());
    	    Response assessmentResponse = null;
    	try {
     		Gson g = new Gson();
           	String remoteReqStr = g.toJson(remoteReq);
             	assessmentVo.setRemoteRequest(remoteReqStr);
 
            JSONObject verifyBizAddrRespJSON = null;
           	JSONObject verifyBizIDRespJSON = null;
     		  
           	RefSponsorConfiguration amsPassiveModeConfig = refSponsorConfigService.getConfigRecord((int) RefSponsor.SPONSOR_ID_CUSTREG,
                    RefSponsorConfiguration.AMS_PASSIVE_MODE);
	        boolean amsPassiveMode = amsPassiveModeConfig != null && AMS_MODE_CONFIG_VALUE_TRUE.equalsIgnoreCase(amsPassiveModeConfig.getValue());
	        
	        if (amsPassiveMode) {
	        	RefSponsorConfiguration bypassAMSAPICallConfig = refSponsorConfigService.getConfigRecord((int) RefSponsor.SPONSOR_ID_CUSTREG,
		                RefSponsorConfiguration.BYPASS_AMS_API_CALL);
		        boolean bypassAMSAPICall = bypassAMSAPICallConfig != null && BusinessIdentityServiceImpl.BYPASS_AMS_API_CONFIG_VALUE_TRUE.equalsIgnoreCase(bypassAMSAPICallConfig.getValue());
		        assessmentVo.setBypassAMSAPICall(bypassAMSAPICall);
	        }
        
           	verifyBizAddrRespJSON = getVerifyBusinessAddressJson(origin, remoteReq, person, personVo, assessmentVo);
   
           	JSONObject verifyBizAddrStatusJson = (JSONObject) verifyBizAddrRespJSON.get("status");
			
			String verifyBizAddrTransStatus = (String) verifyBizAddrStatusJson.get("transactionStatus");
			CustomLogger.debug(this.getClass(), "RemoteSupportService verifyBizAddrTransStatus: " + verifyBizAddrTransStatus);
	
	        

			boolean failedBizAddressCheck = !IPSConstants.TRANSACTION_STATUS_PASSED.equalsIgnoreCase(verifyBizAddrTransStatus);

			//If failed AMS Check or Passive Mode, proceed to LexisNexis BIID Check
			if (failedBizAddressCheck || amsPassiveMode) {  
				assessmentVo.setRequestJson(null);
				assessmentVo.setResponseJson(null);
				verifyBizIDRespJSON = getVerifyBusinessIdentityJSONObject(origin, remoteReq, person, personVo, assessmentVo);

				if (verifyBizIDRespJSON != null) {
					JSONObject verifyBizIDStatusJson = (JSONObject) verifyBizIDRespJSON.get("status");
					String verifyBizIDTransStatus = (String) verifyBizIDStatusJson.get("transactionStatus");
					CustomLogger.debug(this.getClass(), "RemoteSupportService verifyBizIDTransStatus: " + verifyBizIDTransStatus);
				}
				else {
					verifyBizIDRespJSON = new JSONObject();
					verifyBizIDRespJSON.put("responseMessage", "BIID Call API returns null");
				}
           		
           		//Returns to CustReg, passed or failed.
           		assessmentResponse = buildCommonResponse(null, verifyBizIDRespJSON, origin);
			} else {
   				assessmentResponse = buildCommonResponse(null, verifyBizAddrRespJSON, origin);
			}            
    	} catch (Exception e) {
        	 assessmentResponse = exceptionErrorResponse(personVo, null, "getting Business Verification Status", origin, e);
        }

    	return assessmentResponse;
    }
    
    public Response getVerifyBusinessAddressResponseByEndpoint(String origin, RemoteRequest remoteReq, Person person, PersonVo personVo, CommonAssessmentParamVo assessmentVo) {
	    CustomLogger.enter(this.getClass());
	    Response assessmentResponse = null;
		try {
	 		Gson g = new Gson();
	       	String remoteReqStr = g.toJson(remoteReq);
	         	assessmentVo.setRemoteRequest(remoteReqStr);
	
	        JSONObject verifyBizAddrRespJSON = null;
	     	
	    	try {
	           	assessmentVo.setRemoteRequest(remoteReqStr);
	 
	           	verifyBizAddrRespJSON = verifyBusinessAddressService.checkBusinessAddressResponseByEndpoint(personVo, assessmentVo);
	           	// public JSONObject checkBusinessAddressResponseByEndpoint(PersonVo personVo, CommonAssessmentParamVo assessmentVo) {
	      	} catch (Exception e) {
	      		return assessmentResponse;
	        }
	       	
	       	JSONObject verifyBizAddrStatusJson = (JSONObject) verifyBizAddrRespJSON.get("status");
			
			String verifyBizAddrTransStatus = (String) verifyBizAddrStatusJson.get("transactionStatus");
			CustomLogger.debug(this.getClass(), "RemoteSupportService verifyBizAddrTransStatus: " + verifyBizAddrTransStatus);
		
			assessmentResponse = buildCommonResponse(null, verifyBizAddrRespJSON, origin);         
		} catch (Exception e) {
	    	 assessmentResponse = exceptionErrorResponse(personVo, null, "getting Business Verification Status", origin, e);
	    }
	
		return assessmentResponse;
    }
    
    public Response getVerifyTestBusinessResponse(String origin, RemoteRequest remoteReq, Person person, PersonVo personVo, CommonAssessmentParamVo assessmentVo) {
    	CustomLogger.enter(this.getClass());
    	Response assessmentResponse = null;

    	try {
      		//RdpDeviceAssessmentResponseModel checkDevicePlusResponse = new RdpDeviceAssessmentResponseModel();
    		Gson g = new Gson();
           	String remoteReqStr = g.toJson(remoteReq);
           	assessmentVo.setRemoteRequest(remoteReqStr);
           	
            JSONObject verifyBizAddrRespJSON = null;
           	JSONObject verifyBizIDRespJSON = null;
           	String responseJsonString = "";
     		  
           	String workflow = remoteReq.getGenericParam2();
           	String directCall = remoteReq.getGenericParam3();
           	
        	if ("DERA".equalsIgnoreCase(workflow)) {
         		assessmentResponse = assessDevicePlusEmailRisk(origin, remoteReq, person, personVo, assessmentVo);
        		
        		if (directCall != null && "true".equalsIgnoreCase(directCall)) {
        			responseJsonString= assessmentVo.getResponseJson();
         			JSONObject responseJSON = new JSONObject();
        			responseJSON.put("responseJson", responseJsonString.replace("\\", ""));
         			assessmentResponse = buildCommonResponse(null, responseJSON, origin);
        			return assessmentResponse;
        		}
         	}
        	else if ("AMS".equalsIgnoreCase(workflow)) {
        		verifyBizAddrRespJSON = getVerifyBusinessAddressJson(origin, remoteReq, person, personVo, assessmentVo);
        		responseJsonString= assessmentVo.getResponseJson();
          		JSONObject responseJSON = new JSONObject();
        		responseJSON.put("responseJson", responseJsonString.replace("\\", ""));
    			assessmentResponse = buildCommonResponse(null, responseJSON, origin);
    			return assessmentResponse;
			}
        	else if ("BIID".equalsIgnoreCase(workflow)) {
        		verifyBizIDRespJSON = getVerifyBusinessIdentityJSONObject(origin, remoteReq, person, personVo, assessmentVo);responseJsonString= assessmentVo.getResponseJson();
           		JSONObject responseJSON = new JSONObject();
        		responseJSON.put("responseJson", responseJsonString.replace("\\", ""));
      			assessmentResponse = buildCommonResponse(null, responseJSON, origin);
    			return assessmentResponse;
			}
        	else if ("AMS-BIID".equalsIgnoreCase(workflow)) {
 	           	verifyBizAddrRespJSON = getVerifyBusinessAddressJson(origin, remoteReq, person, personVo, assessmentVo);
	           	JSONObject verifyBizAddrStatusJson = (JSONObject) verifyBizAddrRespJSON.get("status");
				String verifyBizAddrTransStatus = (String) verifyBizAddrStatusJson.get("transactionStatus");
		
				if (IPSConstants.TRANSACTION_STATUS_PASSED.equalsIgnoreCase(verifyBizAddrTransStatus)) {
	   				assessmentResponse = buildCommonResponse(null, verifyBizAddrRespJSON, origin);
	   				return assessmentResponse;
				}
				else { //If failed AMS Check, proceed to LexisNexis BIID Check
					verifyBizIDRespJSON = getVerifyBusinessIdentityJSONObject(origin, remoteReq, person, personVo, assessmentVo);
	
					if (verifyBizIDRespJSON != null) {
						JSONObject verifyBizIDStatusJson = (JSONObject) verifyBizIDRespJSON.get("status");
						String verifyBizIDTransStatus = (String) verifyBizIDStatusJson.get("transactionStatus");
					}
					else {
						verifyBizIDRespJSON = new JSONObject();
						verifyBizIDRespJSON.put("responseMessage", "BIID Call API returns null");
					}
	           		
	           		//Returns to CustReg, passed or failed.
	           		assessmentResponse = buildCommonResponse(null, verifyBizIDRespJSON, origin);
	           		return assessmentResponse;
				}
           	}
     	} catch (Exception e) {
        	 assessmentResponse = exceptionErrorResponse(personVo, null, "getting Business Verification Status", origin, e);
        	 return assessmentResponse;
        }

     	return assessmentResponse;
    }
    
    public Response getVerifyBusinessIdentityResponse(String origin, RemoteRequest remoteReq, Person person, PersonVo personVo, CommonAssessmentParamVo assessmentVo) {
      	Response assessmentResponse = null;
     	
    	try {
      		JSONObject pvResponseJSON = getVerifyBusinessIdentityJSONObject(origin, remoteReq, person, personVo, assessmentVo);

           	if (personVo.isCheckHighRiskAddress()) {
          		assessmentResponse = buildPVResponse(null, pvResponseJSON, origin);
          		//saveDeviceReputationAssessmentResponse(personVo, remoteReq, assessmentResponse);
        	}
        	else if (assessmentVo.isHighRiskAddress()) {
        		assessmentResponse = buildCommonResponse(null, pvResponseJSON, origin);
        		//saveDeviceReputationAssessmentResponse(personVo, remoteReq, assessmentResponse);
        	}
           	else {
           		assessmentResponse = buildCommonResponse(null, pvResponseJSON, origin);
            }
    	} catch (Exception e) {
        	 assessmentResponse = exceptionErrorResponse(personVo, null, "getting Device Reputation Status", origin, e);
        }

    	return assessmentResponse;
    }
    
    public JSONObject getVerifyBusinessIdentityJSONObject(String origin, RemoteRequest remoteReq, Person person, PersonVo personVo, CommonAssessmentParamVo assessmentVo) {
    	JSONObject pvResponseJSON = null;
     	
    	try {
     		Gson g = new Gson();
           	String remoteReqStr = g.toJson(remoteReq);
           	assessmentVo.setRemoteRequest(remoteReqStr);

           	pvResponseJSON = verifyBusinessIdentityService.getVerifyBusinessIdentityResponse(person, personVo, assessmentVo);
    	} catch (Exception e) {
        	 //
        }

    	return pvResponseJSON;
    }
    
    public Response getVerifyBusinessAddressResponse(String origin, RemoteRequest remoteReq, Person person, PersonVo personVo, CommonAssessmentParamVo assessmentVo) {
    	Response assessmentResponse = null;
     	
    	try {
           	JSONObject pvResponseJSON = getVerifyBusinessAddressJson(origin, remoteReq, person, personVo, assessmentVo);
            
           	assessmentResponse = buildCommonResponse(null, pvResponseJSON, origin);
    	} catch (Exception e) {
        	 //assessmentResponse = exceptionErrorResponse(personVo, null, "getting VerifyBusinessAddress Response", origin, e);
        }

    	return assessmentResponse;
    }
    
    public JSONObject getVerifyBusinessAddressJson(String origin, RemoteRequest remoteReq, Person person, PersonVo personVo, CommonAssessmentParamVo assessmentVo) {
    	JSONObject pvResponseJSON = null;
     	
    	try {
      		Gson g = new Gson();
           	String remoteReqStr = g.toJson(remoteReq);
           	assessmentVo.setRemoteRequest(remoteReqStr);
 
           	pvResponseJSON = verifyBusinessAddressService.getVerifyBusinessAddressResponse(person, personVo, null, assessmentVo);

      	} catch (Exception e) {
        	 //assessmentResponse = exceptionErrorResponse(personVo, null, "getting VerifyBusinessAddress Response", origin, e);
        }

    	return pvResponseJSON;
    }
    
    public RefOtpSupplier determineVerificationMethod(PersonVo personVo) {
    	CustomLogger.enter(this.getClass());

    	RefOtpSupplier refOtpSupplier = null;

    	try {
    		refOtpSupplier = verificationProviderService.determineVerificationMethod(personVo);
        } catch (Exception e) {
            CustomLogger.error(this.getClass(), "Error determining phone verification supplier: ", e);
        }
        return refOtpSupplier;
    }

    public boolean passPhoneVelocity(Person person, PersonVo personVo, RefOtpSupplier refOtpSupplier, String serviceName) {
    	CustomLogger.enter(this.getClass());

    	try {
    		RpEvent rpEvent = rpEventDataService.findLatestEventByPersonId(person.getPersonId());
    		boolean passVelocityCheck = passVelocityCheck(IPSConstants.VELOCITY_TYPE_PHONE, person, personVo, refOtpSupplier, rpEvent);
	        
			if (passVelocityCheck) {
            	PhoneVerificationParamVo verificationParamVo = new PhoneVerificationParamVo();
            	
            	if (!Utils.isEmptyString(personVo.getPhoneNumber())) {
            		verificationParamVo.setMobilePhoneNumber(personVo.getPhoneNumber());
            	}
                
                boolean hasPreviousPhoneVerificationDecision = phoneVerificationService.hasPreviousPhoneVerificationDecision(person, personVo, refOtpSupplier.getOtpSupplierId(), verificationParamVo);
                personVo.setHasPreviousPhoneVerificationDecision(hasPreviousPhoneVerificationDecision);
                
                personVo.setResetProofingStatus(false);  
                return true;
	        }
	        else {
	        	return false;
	        } 
    	}
        catch (Exception e) {
        	personVo.setErrorMessage("Error occurred in checking phone velocity.");
           	return false;
        }
    }
 
    /**
     * Sends passcode to the customer's phone and verifies the Passcode was sent
     * 
     * @param origin
     *            - Header parameter origin
     * @param personVo
     * @param phoneSupplier
     * @param person
     * @param pvResponse
     * @return
     * @throws Throwable 
     */
    public String sendPasscodeToPhone(PersonVo personVo, RefOtpSupplier phoneSupplier,
            RpEvent rpEvent) throws Throwable {
    	CustomLogger.enter(this.getClass());

    	RemoteResponse pvResponse = new RemoteResponse();
     	
        try {
            if (REQUEST_PASSCODE.equalsIgnoreCase(personVo.getCallingMethod())) {
                phoneVerificationService.resendPasscode(personVo, phoneSupplier, rpEvent);
            } else {
            	phoneVerificationService.sendPasscodeSuccessful(personVo, phoneSupplier);
            }

            boolean otpSent = personVo.isPasscodeSent();
            pvResponse.setPasscodeSent(String.valueOf(otpSent));

            if (!otpSent) {
                CustomLogger.info(this.getClass(), "Passcode failed to send for customer " + personVo.getSponsorUserId());
                return REASON_FAILED_OTP_DELIVERY;
            }
        } catch (Exception e) {
        	CustomLogger.error(this.getClass(), "Exception occurred in sending Passcode to Phone for customer " + personVo.getSponsorUserId());
        	return REASON_FAILED_OTP_DELIVERY;
        }

        return null;
    }
  
    public JSONObject buildRemoteResponseJSON(PersonVo personVo, RefOtpSupplier phoneSupplier, RpEvent rpEvent, String serviceName, String velocityType, String reason) {           
        if (REASON_APPROVED_PHONE_VERIFICATION.equalsIgnoreCase(reason)) {
       		return buildApprovedPhoneVerificationResponseJSON(personVo, phoneSupplier, rpEvent, serviceName, velocityType, reason);       
        }
        else if(REASON_FAILED_PHONE_VELOCITY.equalsIgnoreCase(reason)) {
    		return buildFailedPhoneVelocityResponseJSON(personVo, phoneSupplier, rpEvent, serviceName, velocityType, reason);       
		}
      	else if (REASON_FAILED_PHONE_VERIFICATION.equalsIgnoreCase(reason)) {
     		return buildFailedPhoneVerificationResponseJSON(personVo, phoneSupplier, rpEvent, serviceName, velocityType, reason);       
    	}
      	else if (REASON_FAILED_SUPPLIER_SELECTION.equalsIgnoreCase(reason)) {
     		return buildFailedSupplierSelectionResponseJSON(personVo, phoneSupplier, rpEvent, serviceName, velocityType, reason);       
      	}
      	else if (REASON_FAILED_OTP_DELIVERY.equalsIgnoreCase(reason)) {  
       		return buildFailedOtpDeliveryResponseJSON(personVo, phoneSupplier, rpEvent, serviceName, velocityType, reason);       
    	}
       	else if (REASON_FAILED_OTP_VELOCITY.equalsIgnoreCase(reason)) {
      		return buildFailedOtpVelocityResponseJSON(personVo, phoneSupplier, rpEvent, serviceName, velocityType, reason);
       	}
       	else if (REASON_FAILED_OTP_CONFIRMATION_MISMATCH.equalsIgnoreCase(reason)) {
     		return buildFailedOtpConfirmationResponseJSON(personVo, phoneSupplier, rpEvent, serviceName, velocityType, reason);
       	}
       	else if (REASON_FAILED_OTP_CONFIRMATION_OTHER.equalsIgnoreCase(reason)) {
     		return buildFailedOtpConfirmationResponseJSON(personVo, phoneSupplier, rpEvent, serviceName, velocityType, reason);
       	}
       	else if (REASON_FAILED_SMFA_DELIVERY.equalsIgnoreCase(reason)) {
     		return buildFailedSmfaDeliveryResponseJSON(personVo, phoneSupplier, rpEvent, serviceName, velocityType, reason);
    	}
      	else if (REASON_FAILED_SMFA_VALIDATION.equalsIgnoreCase(reason)) {
     		return buildFailedSmfaValidationResponseJSON(personVo, phoneSupplier, rpEvent, serviceName, velocityType, reason);
       	}
      	else if (REASON_FAILED_SMFA_VELOCITY.equalsIgnoreCase(reason)) {
       		return buildFailedSmfaVelocityResponseJSON(personVo, phoneSupplier, rpEvent, serviceName, velocityType, reason);
 	   	}
      	else if (REASON_PASSED_OTP_CONFIRMATION.equalsIgnoreCase(reason)) {  
     		return buildPassedOtpConfirmationResponseJSON(personVo, phoneSupplier, rpEvent, serviceName, velocityType, reason);
       	}
     	else if (REASON_PASSED_SMFA_VALIDATION.equalsIgnoreCase(reason)) {
       		return buildPassedSmfaValidationResponseJSON(personVo, phoneSupplier, rpEvent, serviceName, velocityType, reason);
      	}
    	else if (REASON_SUCCESSS_OTP_DELIVERY.equalsIgnoreCase(reason)) {  
      		return buildSuccessfulOtpDeliveryResponseJSON(personVo, phoneSupplier, rpEvent, serviceName, velocityType, reason);
    	}
    	else if (REASON_SUCCESSSFUL_SMFA_DELIVERY.equalsIgnoreCase(reason)) {  
    		return buildSuccessfulSmfaDeliveryResponseJSON(personVo, phoneSupplier, rpEvent, serviceName, velocityType, reason);
      	}
    	else if (REASON_DEVICE_DEFAULT_DECISION.equalsIgnoreCase(reason)) {  
			return buildDefaultCheckDeviceResponseJSON(personVo, phoneSupplier, rpEvent, serviceName, velocityType, reason);
      	} 

		return new JSONObject();
    }
 
    public JSONObject buildApprovedPhoneVerificationResponseJSON(PersonVo personVo, RefOtpSupplier phoneSupplier, RpEvent rpEvent, String serviceName, String velocityType, String reason) {
  		JSONObject remoteResponseJSON = new JSONObject();
      	String responseMessage = String.format("Identity for customer %s successfully verified", personVo.getSponsorUserId());
     	
      	remoteResponseJSON.put("reviewStatus", RpPhoneVerification.DECISION_APPROVE); 
     	remoteResponseJSON.put("reasonCode", RESP_VALUE_PHONE); 
     	remoteResponseJSON.put("phoneVerified", String.valueOf(true)); 
        remoteResponseJSON.put("identityVerified", String.valueOf(true));
        remoteResponseJSON.put("responseMessage", responseMessage);

		return remoteResponseJSON;
    }

    public JSONObject buildFailedPhoneVelocityResponseJSON(PersonVo personVo, RefOtpSupplier phoneSupplier, RpEvent rpEvent, String serviceName, String velocityType, String reason) {
  		JSONObject remoteResponseJSON = new JSONObject();
        String sponsorUserId = personVo != null? personVo.getSponsorUserId() : "";
  		String responseMessage = String.format(LOCKOUT_MSG_FMT, sponsorUserId);
 
        Timestamp lockoutExpiresDatetime = personVo.getLockoutExpiresDatetime();
        boolean hasLockoutExpiresDatetime = lockoutExpiresDatetime != null;
 
  		// Get how many phone velocity attempts made.
    	remoteResponseJSON.put("phoneVerified", String.valueOf(false));
    	remoteResponseJSON.put("identityVerified", String.valueOf(false));
    	
    	if (phoneSupplier.isEquifaxDITPhone()) {
    		remoteResponseJSON.put("smfaLinkSent", String.valueOf(false));
    		remoteResponseJSON.remove("smfaLinkAttemptLimitReached");
        }
        else {
        	remoteResponseJSON.put("passcodeSent", String.valueOf(false));
        	remoteResponseJSON.remove("passcodeAttemptLimitReached");  
        }
    
    	long phoneVerificationAttemptCount = personVo.getPhoneVerificationAttemptCount();
    	boolean phoneAttemptLimitReached = phoneVerificationAttemptCount >= 3;
        remoteResponseJSON.put("phoneAttemptsIn72Hours", phoneVerificationAttemptCount); 
        		//+ (hasLockoutExpiresDatetime ? 0 : 1));
        remoteResponseJSON.put("phoneAttemptLimitReached", String.valueOf(phoneAttemptLimitReached));

        if (hasLockoutExpiresDatetime) {
        	remoteResponseJSON.put("lockoutExpiresDateTime", new SimpleDateFormat(SIMPLE_DATE_FMT)
                    .format(lockoutExpiresDatetime).toUpperCase());
        	remoteResponseJSON.put("lockoutStillInEffect", String.valueOf(true));
          	
          	//TODO: Check for "Return isLockoutStillInEffect" the first time it was locked out
	        if (personVo.getSupplierEffectingLockout() != null) {
	        	remoteResponseJSON.put("supplierEffectingLockout", personVo.getSupplierEffectingLockout());
	        }  		        			        
        }
        else {
        	remoteResponseJSON.remove("lockoutExpiresDateTime");
        	remoteResponseJSON.remove("lockoutStillInEffect");
        	remoteResponseJSON.remove("supplierEffectingLockout");
        }
        
        remoteResponseJSON.put("responseMessage", responseMessage);

        CustomLogger.info(this.getClass(), responseMessage);
		return remoteResponseJSON;
    }
    
    public JSONObject buildFailedPhoneVerificationResponseJSON(PersonVo personVo, RefOtpSupplier phoneSupplier, RpEvent rpEvent, String serviceName, String velocityType, String reason) {
  		JSONObject remoteResponseJSON = new JSONObject();
  		String sponsorUserId = personVo != null? personVo.getSponsorUserId() : "";
  		String responseMessage = String.format("Phone: %s was not verified for customer %s", 
        		personVo.getMobileNumber(), sponsorUserId);  
  		
        Timestamp lockoutExpiresDatetime = personVo.getLockoutExpiresDatetime();
        boolean hasLockoutExpiresDatetime = lockoutExpiresDatetime != null;
        long pvAttemptCount = personVo.getPhoneVerificationAttemptCount();

        if (personVo.getTransactionId() != null) {
        	remoteResponseJSON.put("transactionID", personVo.getTransactionId());
        }

        remoteResponseJSON.put("phoneVerified", String.valueOf(false));
        remoteResponseJSON.put("identityVerified", String.valueOf(false));
		
		if (phoneSupplier.isEquifaxDITPhone()) {
			remoteResponseJSON.put("phoneVerificationDecision", RpPhoneVerification.DECISION_DENY);
			remoteResponseJSON.put("smfaLinkSent", String.valueOf(false));
        }
        else {
        	remoteResponseJSON.put("phoneVerificationDecision", RpPhoneVerification.DECISION_FAIL);
        	remoteResponseJSON.put("passcodeSent", String.valueOf(false));
        }
			
		if (phoneSupplier.isEquifaxDITPhone()) {
			remoteResponseJSON.remove("smfaLinkAttemptLimitReached");
		} else {
			remoteResponseJSON.remove("passcodeAttemptLimitReached");  
		}
		
        if (VERIFY_PHONE.equalsIgnoreCase(serviceName) && !personVo.isOtpSmsLandline()) {
    		pvAttemptCount = pvAttemptCount + (hasLockoutExpiresDatetime ? 0 : 1);  
    	}
			        
        remoteResponseJSON.put("phoneAttemptsIn72Hours", pvAttemptCount);
        remoteResponseJSON.put("phoneAttemptLimitReached", String.valueOf(personVo.getPhoneVerificationAttemptCount() >= 3));

        if (hasLockoutExpiresDatetime) {
        	remoteResponseJSON.put("lockoutExpiresDateTime", new SimpleDateFormat(SIMPLE_DATE_FMT)
                    .format(lockoutExpiresDatetime).toUpperCase());
        	remoteResponseJSON.put("lockoutStillInEffect", String.valueOf(true));
          	
	        if (personVo.getSupplierEffectingLockout() != null) {
	        	remoteResponseJSON.put("supplierEffectingLockout", personVo.getSupplierEffectingLockout());
	        }  		        			        
        }
        else {
        	remoteResponseJSON.remove("lockoutExpiresDateTime");
        	remoteResponseJSON.remove("lockoutStillInEffect");
        	remoteResponseJSON.remove("supplierEffectingLockout");
        }

        if (personVo.isOtpSmsLandline()) {
        	responseMessage = String.format("Phone: %s is a landline and was not verified for customer %s", 
            		personVo.getMobileNumber(), sponsorUserId);
        }
        else if (hasLockoutExpiresDatetime) {
        	responseMessage = String.format(LOCKOUT_MSG_FMT, sponsorUserId);
        }
        
        remoteResponseJSON.put("responseMessage", responseMessage);
        
        if (commonRestService.isLowerEnvironment() && personVo.isReturnDebugData()) {
	        remoteResponseJSON.put("reason", reason);
	 		remoteResponseJSON.put("phoneVerificationResponse", personVo.getLexisPhoneVerificationResponseJson());
			remoteResponseJSON.put("passcodeDeliveryResponse", personVo.getLexisPasscodeDeliveryResponseJson());
			remoteResponseJSON.put("passcodeConfirmationResponse", personVo.getLexisPasscodeConfirmationResponseJson()); 
        }
        
        if (personVo.isReturnDebugData() && personVo.getResponseData() != null) {
        	remoteResponseJSON.put("responseData", personVo.getResponseData());
        }
        
        CustomLogger.debug(this.getClass(), responseMessage);
        
		return remoteResponseJSON;
    }
    
    public JSONObject buildFailedSupplierSelectionResponseJSON(PersonVo personVo, RefOtpSupplier phoneSupplier, RpEvent rpEvent, String serviceName, String velocityType, String reason) {
  		JSONObject remoteResponseJSON = new JSONObject();
  		String responseMessage = String.format("Phone: %s was not verified for customer %s. No phone verification provider is available.", 
				personVo.getMobileNumber(), personVo.getSponsorUserId());

  		remoteResponseJSON.put("phoneVerified", String.valueOf(false));
  		remoteResponseJSON.put("identityVerified", String.valueOf(false));

		if (phoneSupplier.isEquifaxDITPhone()) {
			remoteResponseJSON.put("smfaLinkSent", String.valueOf(false));
		} else {
			remoteResponseJSON.put("passcodeSent", String.valueOf(false));
		}
 
		remoteResponseJSON.put("responseMessage", responseMessage);

		CustomLogger.info(this.getClass(), responseMessage);
		
		return remoteResponseJSON;
    }
    
    public JSONObject buildFailedOtpDeliveryResponseJSON(PersonVo personVo, RefOtpSupplier phoneSupplier, RpEvent rpEvent, String serviceName, String velocityType, String reason) {
  		JSONObject remoteResponseJSON = new JSONObject();
        String sponsorUserId = personVo != null? personVo.getSponsorUserId() : "";
  		String responseMessage = "Passcode could not be sent to customer " + sponsorUserId;
  		
  		boolean phoneVerified = false;
        Timestamp lockoutExpiresDatetime = personVo.getLockoutExpiresDatetime();
        boolean hasLockoutExpiresDatetime = lockoutExpiresDatetime != null;
        long passcodeAttemptsIn180Hours = personVo.getOtpOrSmfaRequestAttemptCount();
 	    		//+ (hasLockoutExpiresDatetime ? 0 : 1);
        long afterRequestAttemptCount = personVo.getPasscodeAttemptCountAfterRequest();
         
  		if (personVo.getTransactionId() != null) {
        	remoteResponseJSON.put("transactionID", personVo.getTransactionId());
        }
          
  	   	RpPhoneVerification phoneVerification = rpPhoneVerificationService.findPhoneVerificationByEventId(rpEvent.getEventId());
        if (phoneVerification != null) {
        	phoneVerified = phoneVerification.isPhoneVerified();
        }
        
        remoteResponseJSON.put("identityVerified", String.valueOf(false));
        remoteResponseJSON.put("phoneVerified", String.valueOf(phoneVerified));
        remoteResponseJSON.put("phoneVerificationDecision", RpPhoneVerification.DECISION_REVIEW);
        remoteResponseJSON.put("passcodeSent", String.valueOf(false));
        remoteResponseJSON.put("phoneAttemptsIn72Hours", personVo.getPhoneVerificationAttemptCount());
        remoteResponseJSON.put("passcodeAttemptsIn180Hours", passcodeAttemptsIn180Hours);
        remoteResponseJSON.put("passcodeAttemptsAfterLastRequest", afterRequestAttemptCount);
        remoteResponseJSON.put("phoneAttemptLimitReached", String.valueOf(personVo.getPhoneVerificationAttemptCount() >= 3));
        remoteResponseJSON.put("passcodeAttemptLimitReached", String.valueOf(passcodeAttemptsIn180Hours >= 8));       

        if (hasLockoutExpiresDatetime) {
        	remoteResponseJSON.put("lockoutExpiresDateTime", new SimpleDateFormat(SIMPLE_DATE_FMT)
                    .format(lockoutExpiresDatetime).toUpperCase());
        	remoteResponseJSON.put("lockoutStillInEffect", String.valueOf(true));
          	
	        if (personVo.getSupplierEffectingLockout() != null) {
	        	remoteResponseJSON.put("supplierEffectingLockout", personVo.getSupplierEffectingLockout());
	        }  		        			        
        }
        else {
        	remoteResponseJSON.remove("lockoutExpiresDateTime");
        	remoteResponseJSON.remove("lockoutStillInEffect");
        	remoteResponseJSON.remove("supplierEffectingLockout");
        }

        if (hasLockoutExpiresDatetime) {
        	responseMessage = String.format(LOCKOUT_MSG_FMT, sponsorUserId);
        }
        else if (VERIFY_PHONE.equalsIgnoreCase(personVo.getCallingMethod())) {
        	responseMessage = "Passcode was not sent to customer phone: " + personVo.getMobileNumber();
        } 
 
        remoteResponseJSON.put("responseMessage", responseMessage);
         
        if (commonRestService.isLowerEnvironment() && personVo.isReturnDebugData()) {
	        remoteResponseJSON.put("reason", reason);
	 		remoteResponseJSON.put("phoneVerificationResponse", personVo.getLexisPhoneVerificationResponseJson());
			remoteResponseJSON.put("passcodeDeliveryResponse", personVo.getLexisPasscodeDeliveryResponseJson());
			remoteResponseJSON.put("passcodeConfirmationResponse", personVo.getLexisPasscodeConfirmationResponseJson()); 
			remoteResponseJSON.put("responseData", personVo.getResponseData());
        }

		return remoteResponseJSON;
    }
    
    public JSONObject buildFailedOtpVelocityResponseJSON(PersonVo personVo, RefOtpSupplier phoneSupplier, RpEvent rpEvent, String serviceName, String velocityType, String reason) {
  		JSONObject remoteResponseJSON = new JSONObject();
  		boolean phoneVerified = false;
        Timestamp lockoutExpiresDatetime = personVo.getLockoutExpiresDatetime();
        boolean hasLockoutExpiresDatetime = lockoutExpiresDatetime != null;
        long passcodeAttemptsIn180Hours = personVo.getOtpOrSmfaRequestAttemptCount(); 
        long afterRequestAttemptCount = personVo.getPasscodeAttemptCountAfterRequest();
        String responseMessage = String.format(LOCKOUT_MSG_FMT, personVo.getSponsorUserId());
        
  		if (personVo.getTransactionId() != null) {
         	remoteResponseJSON.put("transactionID", personVo.getTransactionId());
        }
         
  		RpPhoneVerification phoneVerification = rpPhoneVerificationService.findPhoneVerificationByEventId(rpEvent.getEventId());
        if (phoneVerification != null) {
        	phoneVerified = phoneVerification.isPhoneVerified();
        }
   		
        remoteResponseJSON.put("identityVerified", String.valueOf(false));
        remoteResponseJSON.put("phoneVerified", String.valueOf(phoneVerified));
        remoteResponseJSON.put("phoneVerificationDecision", RpPhoneVerification.DECISION_PASS);
        remoteResponseJSON.put("passcodeSent", String.valueOf(false));
        remoteResponseJSON.put("phoneAttemptsIn72Hours", personVo.getPhoneVerificationAttemptCount());
 	    remoteResponseJSON.put("passcodeAttemptsIn180Hours", passcodeAttemptsIn180Hours);
 	   remoteResponseJSON.put("passcodeAttemptsAfterLastRequest", afterRequestAttemptCount);
        remoteResponseJSON.put("phoneAttemptLimitReached", String.valueOf(personVo.getPhoneVerificationAttemptCount() >= 3));
      	remoteResponseJSON.put("passcodeAttemptLimitReached", String.valueOf(passcodeAttemptsIn180Hours >=  8));
        remoteResponseJSON.put("responseMessage", responseMessage);

        if (hasLockoutExpiresDatetime) {
        	remoteResponseJSON.put("lockoutExpiresDateTime", new SimpleDateFormat(SIMPLE_DATE_FMT)
                    .format(lockoutExpiresDatetime).toUpperCase());
          	remoteResponseJSON.put("lockoutStillInEffect", String.valueOf(true));
          	
	        if (personVo.getSupplierEffectingLockout() != null) {
	        	remoteResponseJSON.put("supplierEffectingLockout", personVo.getSupplierEffectingLockout());
	        }  		        			        
        }
        else {
        	remoteResponseJSON.remove("lockoutExpiresDateTime");
        	remoteResponseJSON.remove("lockoutStillInEffect");
        	remoteResponseJSON.remove("supplierEffectingLockout");
        }
        
        CustomLogger.info(this.getClass(), responseMessage);
  
		return remoteResponseJSON;
    }
    
    public JSONObject buildFailedOtpConfirmationResponseJSON(PersonVo personVo, RefOtpSupplier phoneSupplier, RpEvent rpEvent, String serviceName, String velocityType, String reason) {
  		JSONObject remoteResponseJSON = new JSONObject();
        String responseMessage = null;
        String sponsorUserId = personVo != null? personVo.getSponsorUserId() : "";
         	
        if (RemoteSupportServiceImpl.REASON_FAILED_OTP_CONFIRMATION_MISMATCH.equalsIgnoreCase(reason)) {  
        	responseMessage = "Passcode submitted did not match passcode sent for customer " + sponsorUserId;
        } else if (RemoteSupportServiceImpl.REASON_FAILED_OTP_CONFIRMATION_OTHER.equalsIgnoreCase(reason)) { 
        	responseMessage = "Passcode confirmation failed for customer " + sponsorUserId;
        } 
        
   		Timestamp lockoutExpiresDatetime = personVo.getLockoutExpiresDatetime();
        boolean hasLockoutExpiresDatetime = lockoutExpiresDatetime != null;
        long passcodeAttemptsIn180Hours = personVo.getOtpOrSmfaRequestAttemptCount();
        		//+ ((hasLockoutExpiresDatetime || personVo.isPasscodeExpired())  ? 0 : 1);
        long afterRequestAttemptCount = personVo.getPasscodeAttemptCountAfterRequest();
 
   		if (personVo.getTransactionId() != null) {
        	remoteResponseJSON.put("transactionID", personVo.getTransactionId());
   		}

 		remoteResponseJSON.put("identityVerified", String.valueOf(false));
   		remoteResponseJSON.put("phoneVerified", String.valueOf(true));
        remoteResponseJSON.put("phoneVerificationDecision", RpPhoneVerification.DECISION_PASS);
   		remoteResponseJSON.put("passcodeSent", String.valueOf(true));
  		remoteResponseJSON.put("passcodeExpired", String.valueOf(rpEvent.lastPasscodeAttemptExpired() || rpEvent.transactionKeyHasExpired()));
        remoteResponseJSON.put("phoneAttemptsIn72Hours", personVo.getPhoneVerificationAttemptCount());
        remoteResponseJSON.put("passcodeAttemptsIn180Hours", passcodeAttemptsIn180Hours);
        remoteResponseJSON.put("passcodeAttemptsAfterLastRequest", afterRequestAttemptCount);
        remoteResponseJSON.put("phoneAttemptLimitReached", String.valueOf(personVo.getPhoneVerificationAttemptCount() >= 3));
     	remoteResponseJSON.put("passcodeAttemptLimitReached", String.valueOf(passcodeAttemptsIn180Hours >=  8));
  
     	boolean removeLockoutData = true;
     	
     	if (personVo.isPasscodeExpired()) {
           	responseMessage = "Passcode expired for customer " + personVo.getSponsorUserId();
           	remoteResponseJSON.put("passcodeExpired", String.valueOf(true));
        }
     	else if (hasLockoutExpiresDatetime) {
     		removeLockoutData = false;
        	responseMessage = String.format(LOCKOUT_MSG_FMT, sponsorUserId);
     		remoteResponseJSON.put("passcodeAttemptsIn180Hours", 8L);
        	remoteResponseJSON.put("lockoutExpiresDateTime", new SimpleDateFormat(SIMPLE_DATE_FMT)
                    .format(lockoutExpiresDatetime).toUpperCase());
          	remoteResponseJSON.put("lockoutStillInEffect", String.valueOf(true));
          	
	        if (personVo.getSupplierEffectingLockout() != null) {
	        	remoteResponseJSON.put("supplierEffectingLockout", personVo.getSupplierEffectingLockout());
	        }  		        			        
        }
     	else if (personVo.isSubmitAttemptsMismatched()) {
           	responseMessage = "Passcode submitted did not match passcode sent for customer " + sponsorUserId;
           	remoteResponseJSON.put("passcodeSubmitExceeded", String.valueOf(true));
        }
     	else if ((personVo.isPasscodeSubmitExceeded() && passcodeAttemptsIn180Hours <= 8L) || afterRequestAttemptCount >= 3) {
           	responseMessage = String.format("Customer %s has too many passcode attempts and needs to request new passcode", 
           			sponsorUserId);
          
           	remoteResponseJSON.put("passcodeSubmitExceeded", String.valueOf(true));
            
           	//TODO: Following line to be removed once COA/OS supports the PasscodeSubmitExceeded response
        }
        
        else if (rpEvent.transactionKeyHasExpired()) {
        	responseMessage = "Transaction expired for customer " + sponsorUserId;
        	remoteResponseJSON.put("passcodeSent", String.valueOf(false));
        	remoteResponseJSON.put("transactionExpired", String.valueOf(true));
        }
        else {
        	// Get latest RpOtpAttempt with confirmation type.
        	RpOtpAttempt latestOtpAttempt = rpOtpAttemptDataService.getLatestOtpConfirmAttempt(personVo.getId(), phoneSupplier.getOtpSupplierId(), rpEvent.getEventId());
         		  
        	if (latestOtpAttempt != null) {
            	String otpDecision = latestOtpAttempt.getOtpDecision();
            	String otpMatchQuality = latestOtpAttempt.getRefOtpMatchQuality() != null ?
            			latestOtpAttempt.getRefOtpMatchQuality().getMatchQuality() : "";

            	if (otpDecision != null && DECISION_FAILED.equalsIgnoreCase(otpDecision)) {
            		if (QUALITY_TIMEOUT.equalsIgnoreCase(otpMatchQuality)) {
            			responseMessage = String.format(PASSCODE_EXPIRED_MSG_FMT, sponsorUserId);
            		}
            		else if (QUALITY_MISMATCH.equalsIgnoreCase(otpMatchQuality)) {
                       	responseMessage = "Passcode submitted did not match passcode sent for customer " + sponsorUserId;
             		}
            		else if (QUALITY_TOO_MANY_ATTEMPTS.equalsIgnoreCase(otpMatchQuality)) {
        	        	responseMessage = String.format(LOCKOUT_MSG_FMT, sponsorUserId);
            		} 
            	}
        	}
	    }
     	
     	if (removeLockoutData) {
     		remoteResponseJSON.remove("lockoutExpiresDateTime");
        	remoteResponseJSON.remove("lockoutStillInEffect");
        	remoteResponseJSON.remove("supplierEffectingLockout");
     	}

     	if (responseMessage != null) {
     		remoteResponseJSON.put("responseMessage", responseMessage);
     	}
        
        if (personVo.isReturnDebugData() && personVo.getResponseData() != null) {
        	remoteResponseJSON.put("responseData", personVo.getResponseData());
        }
        
        if (commonRestService.isLowerEnvironment() && personVo.isReturnDebugData()) {
	        remoteResponseJSON.put("reason", reason);
		remoteResponseJSON.put("phoneVerificationResponse", personVo.getLexisPhoneVerificationResponseJson());
		remoteResponseJSON.put("passcodeDeliveryResponse", personVo.getLexisPasscodeDeliveryResponseJson());
	
		String passcodeConfirmationResponse = personVo.getLexisPasscodeConfirmationResponseJson();
		if (personVo.getLexisPasscodeConfirmationResponseJson() == null) {
			passcodeConfirmationResponse = personVo.getErrorMessage();
		}
			
		remoteResponseJSON.put("passcodeConfirmationResponse", passcodeConfirmationResponse); 
        }
        
        CustomLogger.debug(this.getClass(), responseMessage);

		return remoteResponseJSON;
    }
 
    public JSONObject buildFailedSmfaDeliveryResponseJSON(PersonVo personVo, RefOtpSupplier phoneSupplier, RpEvent rpEvent, String serviceName, String velocityType, String reason) {
  		JSONObject remoteResponseJSON = new JSONObject();
        String sponsorUserId = personVo != null? personVo.getSponsorUserId() : "";
  		String responseMessage = "SMFA Link could not be sent to customer " + sponsorUserId;
    		
  		boolean phoneVerified = false;
  		Timestamp lockoutExpiresDatetime = personVo.getLockoutExpiresDatetime();
        boolean hasLockoutExpiresDatetime = lockoutExpiresDatetime != null;
        long smfaLinkAttemptsIn180Hours = personVo.getOtpOrSmfaRequestAttemptCount();
        //+ (hasLockoutExpiresDatetime ? 0 : 1);
        long afterRequestAttemptCount = personVo.getPasscodeAttemptCountAfterRequest();

        if (personVo.getTransactionId() != null) {
        	remoteResponseJSON.put("transactionID", personVo.getTransactionId());
        }
        
        RpPhoneVerification phoneVerification = rpPhoneVerificationService.findPhoneVerificationByEventId(rpEvent.getEventId());
        if (phoneVerification != null) {
        	phoneVerified = phoneVerification.isPhoneVerified();
        }
          
        remoteResponseJSON.put("identityVerified", String.valueOf(false));
        remoteResponseJSON.put("phoneVerified", String.valueOf(phoneVerified));        
        remoteResponseJSON.put("phoneVerificationDecision", RpPhoneVerification.DECISION_REVIEW);
        remoteResponseJSON.put("smfaLinkSent", String.valueOf(false));
        remoteResponseJSON.put("phoneAttemptsIn72Hours", personVo.getPhoneVerificationAttemptCount());
 		remoteResponseJSON.put("smfaLinkAttemptsIn180Hours", smfaLinkAttemptsIn180Hours);
 		remoteResponseJSON.put("passcodeAttemptsAfterLastRequest", afterRequestAttemptCount);
		remoteResponseJSON.put("phoneAttemptLimitReached", String.valueOf(personVo.getPhoneVerificationAttemptCount() >= 3));
		remoteResponseJSON.put("smfaLinkAttemptLimitReached", String.valueOf(smfaLinkAttemptsIn180Hours >= 8));

        if (hasLockoutExpiresDatetime) {
        	remoteResponseJSON.put("lockoutExpiresDateTime", new SimpleDateFormat(SIMPLE_DATE_FMT)
                    .format(lockoutExpiresDatetime).toUpperCase());
          	remoteResponseJSON.put("lockoutStillInEffect", String.valueOf(true));
          	
	        if (personVo.getSupplierEffectingLockout() != null) {
	        	remoteResponseJSON.put("supplierEffectingLockout", personVo.getSupplierEffectingLockout());
	        }  		        			        
        }
        else {
        	remoteResponseJSON.remove("lockoutExpiresDateTime");
        	remoteResponseJSON.remove("lockoutStillInEffect");
        	remoteResponseJSON.remove("supplierEffectingLockout");
        }
        
        if (hasLockoutExpiresDatetime) {
        	responseMessage = String.format(LOCKOUT_MSG_FMT, sponsorUserId);
        }
        else if (VERIFY_PHONE.equalsIgnoreCase(personVo.getCallingMethod())) {
        	responseMessage = "SMFA Link was not sent to customer phone: " + personVo.getMobileNumber();
        }     
        
        remoteResponseJSON.put("responseMessage", responseMessage);

  		if (commonRestService.isLowerEnvironment() && personVo.isReturnDebugData()) {
	        remoteResponseJSON.put("reason", reason);
			remoteResponseJSON.put("smfaLinkDeliveryRequest", personVo.getEquifaxLinkDeliveryRequestJson());
			remoteResponseJSON.put("smfaLinkDeliveryResponse", personVo.getEquifaxLinkDeliveryResponseJson());
			remoteResponseJSON.put("smfaLinkValidationRequest", personVo.getEquifaxLinkValidationRequestJson()); 
			remoteResponseJSON.put("smfaLinkValidationResponse", personVo.getEquifaxLinkValidationResponseJson()); 
			remoteResponseJSON.put("responseData", personVo.getResponseData());
        }
        
        CustomLogger.debug(this.getClass(), responseMessage);

		return remoteResponseJSON;
    }
    
    public JSONObject buildFailedSmfaValidationResponseJSON(PersonVo personVo, RefOtpSupplier phoneSupplier, RpEvent rpEvent, String serviceName, String velocityType, String reason) {
  		JSONObject remoteResponseJSON = new JSONObject();
        String sponsorUserId = personVo != null? personVo.getSponsorUserId() : "";
  		String responseMessage = String.format("SMFA Link has failed validation for customer %s.", sponsorUserId);
  		
  		boolean phoneVerified = false;
  		Timestamp lockoutExpiresDatetime = personVo.getLockoutExpiresDatetime();
        boolean hasLockoutExpiresDatetime = lockoutExpiresDatetime != null;
        long smfaLinkAttemptsIn180Hours = personVo.getOtpOrSmfaRequestAttemptCount();
        //+ (hasLockoutExpiresDatetime ? 0 : 1);
        long afterRequestAttemptCount = personVo.getPasscodeAttemptCountAfterRequest();
 
  		if (personVo.getTransactionId() != null) {
        	remoteResponseJSON.put("transactionID", personVo.getTransactionId());
  		}
         
  		RpPhoneVerification phoneVerification = rpPhoneVerificationService.findPhoneVerificationByEventId(rpEvent.getEventId());
        if (phoneVerification != null) {
        	phoneVerified = phoneVerification.isPhoneVerified();
        }
   		
  		remoteResponseJSON.put("identityVerified", String.valueOf(false));
        remoteResponseJSON.put("phoneVerified", String.valueOf(phoneVerified));
        remoteResponseJSON.put("phoneVerificationDecision", RpPhoneVerification.DECISION_REVIEW);
        
        if (personVo.getSmfaStatus() != null) {
			remoteResponseJSON.put("smfaStatus", personVo.getSmfaStatus());
		}
		
        remoteResponseJSON.put("smfaLinkSent", String.valueOf(true));
        remoteResponseJSON.put("phoneAttemptsIn72Hours", personVo.getPhoneVerificationAttemptCount());
        remoteResponseJSON.put("smfaLinkAttemptsIn180Hours", smfaLinkAttemptsIn180Hours);
        remoteResponseJSON.put("passcodeAttemptsAfterLastRequest", afterRequestAttemptCount);
 		remoteResponseJSON.put("phoneAttemptLimitReached", String.valueOf(personVo.getPhoneVerificationAttemptCount() >= 3));
		remoteResponseJSON.put("smfaLinkAttemptLimitReached", String.valueOf(smfaLinkAttemptsIn180Hours >= 8));
		
        if (hasLockoutExpiresDatetime) {
        	remoteResponseJSON.put("lockoutExpiresDateTime", new SimpleDateFormat(SIMPLE_DATE_FMT)
                    .format(lockoutExpiresDatetime).toUpperCase());
          	remoteResponseJSON.put("lockoutStillInEffect", String.valueOf(true));
          	
	        if (personVo.getSupplierEffectingLockout() != null) {
	        	remoteResponseJSON.put("supplierEffectingLockout", personVo.getSupplierEffectingLockout());
	        }  	
	        
	        responseMessage = String.format(LOCKOUT_MSG_FMT, sponsorUserId);
        } else {
        	remoteResponseJSON.remove("lockoutExpiresDateTime");
        	remoteResponseJSON.remove("lockoutStillInEffect");
        	remoteResponseJSON.remove("supplierEffectingLockout");
        }
        
    	if (personVo.getErrorMessage() != null  && personVo.getErrorCode() != null) {
    		responseMessage = String.format("%s %s", responseMessage, personVo.getErrorMessage());
    	}
    	
		remoteResponseJSON.put("responseMessage", responseMessage);

        CustomLogger.debug(this.getClass(), responseMessage);
        
  		if (commonRestService.isLowerEnvironment() && personVo.isReturnDebugData()) {
	        remoteResponseJSON.put("reason", reason);
			remoteResponseJSON.put("smfaLinkDeliveryRequest", personVo.getEquifaxLinkDeliveryRequestJson());
			remoteResponseJSON.put("smfaLinkDeliveryResponse", personVo.getEquifaxLinkDeliveryResponseJson());
			remoteResponseJSON.put("smfaLinkValidationRequest", personVo.getEquifaxLinkValidationRequestJson()); 
			remoteResponseJSON.put("smfaLinkValidationResponse", personVo.getEquifaxLinkValidationResponseJson()); 
			remoteResponseJSON.put("responseData", personVo.getResponseData());
        }
        
       
		return remoteResponseJSON;
    }
    
    public JSONObject buildFailedSmfaVelocityResponseJSON(PersonVo personVo, RefOtpSupplier phoneSupplier, RpEvent rpEvent, String serviceName, String velocityType, String reason) {
  		JSONObject remoteResponseJSON = new JSONObject();
  		boolean phoneVerified = false;
  		Timestamp lockoutExpiresDatetime = personVo.getLockoutExpiresDatetime();
        boolean hasLockoutExpiresDatetime = lockoutExpiresDatetime != null;
        long smfaLinkAttemptsIn180Hours = personVo.getOtpOrSmfaRequestAttemptCount(); 
				//+ (hasLockoutExpiresDatetime ? 0 : 1);
        long afterRequestAttemptCount = personVo.getPasscodeAttemptCountAfterRequest();
  	    String responseMessage = String.format(LOCKOUT_MSG_FMT, personVo.getSponsorUserId());
  	    
 		if (personVo.getTransactionId() != null) {
        	remoteResponseJSON.put("transactionID", personVo.getTransactionId());
  		}
       
 		RpPhoneVerification phoneVerification = rpPhoneVerificationService.findPhoneVerificationByEventId(rpEvent.getEventId());
        if (phoneVerification != null) {
        	phoneVerified = phoneVerification.isPhoneVerified();
        }
   		
  		remoteResponseJSON.put("identityVerified", String.valueOf(false));
        remoteResponseJSON.put("phoneVerified", String.valueOf(phoneVerified));
        remoteResponseJSON.put("phoneVerificationDecision", RpPhoneVerification.DECISION_REVIEW);
 		remoteResponseJSON.put("smfaLinkSent", String.valueOf(false));
        remoteResponseJSON.put("phoneAttemptsIn72Hours", personVo.getPhoneVerificationAttemptCount());
 		remoteResponseJSON.put("smfaLinkAttemptsIn180Hours", smfaLinkAttemptsIn180Hours);
 		remoteResponseJSON.put("passcodeAttemptsAfterLastRequest", afterRequestAttemptCount);
		remoteResponseJSON.put("phoneAttemptLimitReached", String.valueOf(personVo.getPhoneVerificationAttemptCount() >= 3));
		remoteResponseJSON.put("smfaLinkAttemptLimitReached", String.valueOf(smfaLinkAttemptsIn180Hours >= 8));
	    remoteResponseJSON.put("responseMessage", responseMessage);
		
        if (hasLockoutExpiresDatetime) {
        	remoteResponseJSON.put("lockoutExpiresDateTime", new SimpleDateFormat(SIMPLE_DATE_FMT)
                    .format(lockoutExpiresDatetime).toUpperCase());
          	remoteResponseJSON.put("lockoutStillInEffect", String.valueOf(true));
          	
	        if (personVo.getSupplierEffectingLockout() != null) {
	        	remoteResponseJSON.put("supplierEffectingLockout", personVo.getSupplierEffectingLockout());
	        }  		        			        
        }
        else {
        	remoteResponseJSON.remove("lockoutExpiresDateTime");
        	remoteResponseJSON.remove("lockoutStillInEffect");
        	remoteResponseJSON.remove("supplierEffectingLockout");
        }
  
        CustomLogger.info(this.getClass(), responseMessage);
        
		return remoteResponseJSON;
    }
    
    public JSONObject buildPassedOtpConfirmationResponseJSON(PersonVo personVo, RefOtpSupplier phoneSupplier, RpEvent rpEvent, String serviceName, String velocityType, String reason) {
  		JSONObject remoteResponseJSON = new JSONObject();
        Timestamp lockoutExpiresDatetime = personVo.getLockoutExpiresDatetime();
        boolean hasLockoutExpiresDatetime = lockoutExpiresDatetime != null;
        long passcodeAttemptsIn180Hours = personVo.getOtpOrSmfaRequestAttemptCount();
	    		//+ (hasLockoutExpiresDatetime ? 0 : 1);
        long afterRequestAttemptCount = personVo.getPasscodeAttemptCountAfterRequest();
     	String responseMessage = "Identity for customer " + personVo.getSponsorUserId() + " successfully verified";      	 

 		if (personVo.getTransactionId() != null) {
         	remoteResponseJSON.put("transactionID", personVo.getTransactionId());
        }
 		
        remoteResponseJSON.put("phoneVerified", String.valueOf(true));
        remoteResponseJSON.put("identityVerified", String.valueOf(true));
        remoteResponseJSON.put("phoneVerificationDecision", RpPhoneVerification.DECISION_PASS);
        remoteResponseJSON.put("passcodeSent", String.valueOf(true));
        remoteResponseJSON.put("phoneAttemptsIn72Hours", personVo.getPhoneVerificationAttemptCount());
 	    remoteResponseJSON.put("passcodeAttemptsIn180Hours", passcodeAttemptsIn180Hours);
 	   remoteResponseJSON.put("passcodeAttemptsAfterLastRequest", afterRequestAttemptCount);
        remoteResponseJSON.put("phoneAttemptLimitReached", String.valueOf(personVo.getPhoneVerificationAttemptCount() >= 3));
      	remoteResponseJSON.put("passcodeAttemptLimitReached", String.valueOf(passcodeAttemptsIn180Hours >=  8));
      	remoteResponseJSON.put("responseMessage", responseMessage);      	 

        if (hasLockoutExpiresDatetime) {
        	remoteResponseJSON.put("lockoutExpiresDateTime", new SimpleDateFormat(SIMPLE_DATE_FMT)
                    .format(lockoutExpiresDatetime).toUpperCase());		        			        
        } else {
        	remoteResponseJSON.remove("lockoutExpiresDateTime");
        	remoteResponseJSON.remove("lockoutStillInEffect");
        	remoteResponseJSON.remove("supplierEffectingLockout");
        }
        
 		if (commonRestService.isLowerEnvironment() && personVo.isReturnDebugData()) {
	        remoteResponseJSON.put("reason", reason);
	 		remoteResponseJSON.put("phoneVerificationResponse", personVo.getLexisPhoneVerificationResponseJson());
			remoteResponseJSON.put("passcodeDeliveryResponse", personVo.getLexisPasscodeDeliveryResponseJson());
			remoteResponseJSON.put("passcodeConfirmationResponse", personVo.getLexisPasscodeConfirmationResponseJson()); 
        }
 		
        CustomLogger.debug(this.getClass(), responseMessage);
  
		return remoteResponseJSON;
    }
 
    public JSONObject buildPassedSmfaValidationResponseJSON(PersonVo personVo, RefOtpSupplier phoneSupplier, RpEvent rpEvent, String serviceName, String velocityType, String reason) {
  		JSONObject remoteResponseJSON = new JSONObject();
        Timestamp lockoutExpiresDatetime = personVo.getLockoutExpiresDatetime();
        boolean hasLockoutExpiresDatetime = lockoutExpiresDatetime != null;
       	long smfaLinkAttemptsIn180Hours = personVo.getOtpOrSmfaRequestAttemptCount();
				//+ (hasLockoutExpiresDatetime ? 0 : 1);
       	long afterRequestAttemptCount = personVo.getPasscodeAttemptCountAfterRequest();
  		String responseMessage = String.format("Identity for customer %s successfully verified", personVo.getSponsorUserId());

  		if (personVo.getTransactionId() != null) {
        	remoteResponseJSON.put("transactionID", personVo.getTransactionId());
 		}
       
   		remoteResponseJSON.put("phoneVerified", String.valueOf(true));
 		remoteResponseJSON.put("identityVerified", String.valueOf(true));
        remoteResponseJSON.put("phoneVerificationDecision", RpPhoneVerification.DECISION_REVIEW);
  		
        if (personVo.getSmfaStatus() != null) {
				remoteResponseJSON.put("smfaStatus", personVo.getSmfaStatus());
		}
        
        remoteResponseJSON.put("smfaLinkSent", String.valueOf(true));
       	remoteResponseJSON.put("phoneAttemptsIn72Hours", personVo.getPhoneVerificationAttemptCount());
		remoteResponseJSON.put("smfaLinkAttemptsIn180Hours", smfaLinkAttemptsIn180Hours);
		remoteResponseJSON.put("passcodeAttemptsAfterLastRequest", afterRequestAttemptCount);
		remoteResponseJSON.put("phoneAttemptLimitReached", String.valueOf(personVo.getPhoneVerificationAttemptCount() >= 3));
		remoteResponseJSON.put("smfaLinkAttemptLimitReached", String.valueOf(smfaLinkAttemptsIn180Hours >= 8));
     	remoteResponseJSON.put("responseMessage", responseMessage);

        if (hasLockoutExpiresDatetime) {
        	remoteResponseJSON.put("lockoutExpiresDateTime", new SimpleDateFormat(SIMPLE_DATE_FMT)
                    .format(lockoutExpiresDatetime).toUpperCase());		        			        
        } else {
        	remoteResponseJSON.remove("lockoutExpiresDateTime");
        	remoteResponseJSON.remove("lockoutStillInEffect");
        	remoteResponseJSON.remove("supplierEffectingLockout");
        }
        
  		if (commonRestService.isLowerEnvironment() && personVo.isReturnDebugData()) {
	        remoteResponseJSON.put("reason", reason);
			remoteResponseJSON.put("smfaLinkDeliveryRequest", personVo.getEquifaxLinkDeliveryRequestJson());
			remoteResponseJSON.put("smfaLinkDeliveryResponse", personVo.getEquifaxLinkDeliveryResponseJson());
			remoteResponseJSON.put("smfaLinkValidationRequest", personVo.getEquifaxLinkValidationRequestJson()); 
			remoteResponseJSON.put("smfaLinkValidationResponse", personVo.getEquifaxLinkValidationResponseJson()); 
			remoteResponseJSON.put("responseData", personVo.getResponseData());
        }
  		
        CustomLogger.debug(this.getClass(), responseMessage);
        
		return remoteResponseJSON;
    }
 
    public JSONObject buildSuccessfulOtpDeliveryResponseJSON(PersonVo personVo, RefOtpSupplier phoneSupplier, RpEvent rpEvent, String serviceName, String velocityType, String reason) {
  		JSONObject remoteResponseJSON = new JSONObject();
        Timestamp lockoutExpiresDatetime = personVo.getLockoutExpiresDatetime();
        boolean hasLockoutExpiresDatetime = lockoutExpiresDatetime != null;
        long phoneVerificationAttemptCount = personVo.getPhoneVerificationAttemptCount();
        long passcodeAttemptsIn180Hours = personVo.getOtpOrSmfaRequestAttemptCount()
        	+ (hasLockoutExpiresDatetime ? 0 : 1);
        long afterRequestAttemptCount = personVo.getPasscodeAttemptCountAfterRequest();
        String responseMessage = String.format("Passcode was successfully sent to phone: %s.", personVo.getMobileNumber());

  		if (personVo.getTransactionId() != null) {
			 remoteResponseJSON.put("transactionID", personVo.getTransactionId());
		}
  		
 		remoteResponseJSON.put("phoneVerified", String.valueOf(true));
		remoteResponseJSON.put("identityVerified", String.valueOf(false));
        remoteResponseJSON.put("phoneVerificationDecision", RpPhoneVerification.DECISION_PASS);
        remoteResponseJSON.put("passcodeSent", String.valueOf(true));
  
        //if (VERIFY_PHONE.equalsIgnoreCase(serviceName)) {
        //	 phoneVerificationAttemptCount = phoneVerificationAttemptCount + 1;
     	//}
         
        remoteResponseJSON.put("phoneAttemptsIn72Hours", phoneVerificationAttemptCount);
        remoteResponseJSON.put("passcodeAttemptsIn180Hours", passcodeAttemptsIn180Hours);
        remoteResponseJSON.put("passcodeAttemptsAfterLastRequest", afterRequestAttemptCount);
        remoteResponseJSON.put("phoneAttemptLimitReached", String.valueOf(personVo.getPhoneVerificationAttemptCount() >= 3));
        remoteResponseJSON.put("passcodeAttemptLimitReached", String.valueOf(passcodeAttemptsIn180Hours >=  8));
        remoteResponseJSON.put("responseMessage", responseMessage);

        if (hasLockoutExpiresDatetime) {
        	remoteResponseJSON.put("lockoutExpiresDateTime", new SimpleDateFormat(SIMPLE_DATE_FMT)
                    .format(lockoutExpiresDatetime).toUpperCase());		        			        
        } else {
        	remoteResponseJSON.remove("lockoutExpiresDateTime");
        	remoteResponseJSON.remove("lockoutStillInEffect");
        	remoteResponseJSON.remove("supplierEffectingLockout");
        }
          
  		if (commonRestService.isLowerEnvironment() && personVo.isReturnDebugData()) {
	        remoteResponseJSON.put("reason", reason);
	 		remoteResponseJSON.put("phoneVerificationResponse", personVo.getLexisPhoneVerificationResponseJson());
			remoteResponseJSON.put("passcodeDeliveryResponse", personVo.getLexisPasscodeDeliveryResponseJson());
			remoteResponseJSON.put("passcodeConfirmationResponse", personVo.getLexisPasscodeConfirmationResponseJson()); 
        }
  		
        CustomLogger.debug(this.getClass(), responseMessage);

		return remoteResponseJSON;
    }
    
    public JSONObject buildSuccessfulSmfaDeliveryResponseJSON(PersonVo personVo, RefOtpSupplier phoneSupplier, RpEvent rpEvent, String serviceName, String velocityType, String reason) {
		JSONObject remoteResponseJSON = new JSONObject();
		Timestamp lockoutExpiresDatetime = personVo.getLockoutExpiresDatetime();
		boolean hasLockoutExpiresDatetime = lockoutExpiresDatetime != null;
		long smfaLinkAttemptsIn180Hours = personVo.getOtpOrSmfaRequestAttemptCount();
		//+ (hasLockoutExpiresDatetime ? 0 : 1));
		long afterRequestAttemptCount = personVo.getPasscodeAttemptCountAfterRequest();
		String responseMessage = String.format("SMFA Link was successfully sent to phone: %s.", personVo.getMobileNumber());

		if (personVo.getTransactionId() != null) {
		  	remoteResponseJSON.put("transactionID", personVo.getTransactionId());
		}
		
		remoteResponseJSON.put("phoneVerified", String.valueOf(true));
		remoteResponseJSON.put("identityVerified", String.valueOf(false));
		remoteResponseJSON.put("phoneVerificationDecision", RpPhoneVerification.DECISION_REVIEW);
		remoteResponseJSON.put("smfaLinkSent", String.valueOf(true));
		 
		if (personVo.getRedirectUrl() != null) {
			 remoteResponseJSON.put("redirectUrl", personVo.getRedirectUrl());
		}
			
		remoteResponseJSON.put("phoneAttemptsIn72Hours", personVo.getPhoneVerificationAttemptCount());
		remoteResponseJSON.put("smfaLinkAttemptsIn180Hours", smfaLinkAttemptsIn180Hours);
		remoteResponseJSON.put("passcodeAttemptsAfterLastRequest", afterRequestAttemptCount);
	    remoteResponseJSON.put("phoneAttemptLimitReached", String.valueOf(personVo.getPhoneVerificationAttemptCount() >= 3));
	    remoteResponseJSON.put("smfaLinkAttemptLimitReached", String.valueOf(smfaLinkAttemptsIn180Hours >= 8));
		remoteResponseJSON.put("responseMessage", responseMessage);
		 
		if (hasLockoutExpiresDatetime) {
			remoteResponseJSON.put("lockoutExpiresDateTime", new SimpleDateFormat(SIMPLE_DATE_FMT)
		        .format(lockoutExpiresDatetime).toUpperCase());		        			        
		} else {
			remoteResponseJSON.remove("lockoutExpiresDateTime");
			remoteResponseJSON.remove("lockoutStillInEffect");
			remoteResponseJSON.remove("supplierEffectingLockout");
		}
		
  		if (commonRestService.isLowerEnvironment() && personVo.isReturnDebugData()) {
	        remoteResponseJSON.put("reason", reason);
			remoteResponseJSON.put("smfaLinkDeliveryRequest", personVo.getEquifaxLinkDeliveryRequestJson());
			remoteResponseJSON.put("smfaLinkDeliveryResponse", personVo.getEquifaxLinkDeliveryResponseJson());
			remoteResponseJSON.put("smfaLinkValidationRequest", personVo.getEquifaxLinkValidationRequestJson()); 
			remoteResponseJSON.put("smfaLinkValidationResponse", personVo.getEquifaxLinkValidationResponseJson()); 
        }
  		
		CustomLogger.debug(this.getClass(), responseMessage, personVo.getSponsorUserId());

		return remoteResponseJSON;
    }
    
    public JSONObject buildDefaultCheckDeviceResponseJSON(PersonVo personVo, RefOtpSupplier phoneSupplier, RpEvent rpEvent, String serviceName, String velocityType, String reason) {
  		JSONObject remoteResponseJSON = new JSONObject();
  		String sponsorUserId = personVo != null? personVo.getSponsorUserId() : "";
  		String responseMessage = String.format("Customer %s requires additional proofing", sponsorUserId);

  		remoteResponseJSON.put("verified", String.valueOf(false));
  		remoteResponseJSON.put("reviewStatus", "review");
  		remoteResponseJSON.put("reasonCode", "device");
  		remoteResponseJSON.put("responseMessage", responseMessage);
		return remoteResponseJSON;
    }
    
    public JSONObject buildDefaultResponseJSON(PersonVo personVo, RefOtpSupplier phoneSupplier, RpEvent rpEvent, String serviceName, String velocityType, String reason) {
  		JSONObject remoteResponseJSON = new JSONObject();
  		String sponsorUserId = personVo != null? personVo.getSponsorUserId() : "";
  		String responseMessage = String.format("Customer %s requires additional proofing", sponsorUserId);

 		remoteResponseJSON.put("validatePasscodeResponse", personVo.getSendPasscodeResponseJson());
 		remoteResponseJSON.put("validatePasscodeResponse", personVo.getValidatePasscodeResponseJson());
  		remoteResponseJSON.put("reason", reason);
  		remoteResponseJSON.put("reasonCode", "device");
  		remoteResponseJSON.put("responseMessage", responseMessage);
		return remoteResponseJSON;
    }
    
    public boolean passVelocityCheck(String velocityType, Person person, PersonVo personVo, RefOtpSupplier phoneSupplier, RpEvent rpEvent) {
    	CustomLogger.enter(this.getClass());

    	boolean passVelocityCheck = false;

    	// Perform operation based on type parameter.
    	if (IPSConstants.VELOCITY_TYPE_PHONE.equalsIgnoreCase(velocityType)) {
    		RefOtpVelocity refOtpVelocity = proofingService.phoneVelocityCheck(person, personVo, phoneSupplier);
     		passVelocityCheck = !refOtpVelocity.isExceedPhoneVerificationLimit() && !refOtpVelocity.isLockoutStillInEffect();
    	}
    	else if (IPSConstants.VELOCITY_TYPE_PASSCODE.equalsIgnoreCase(velocityType)) {
    		passVelocityCheck = !otpVelocityCheckService.exceededPasscodeAttemptsLimit(person, personVo, phoneSupplier, rpEvent);
    	}
    	else if (IPSConstants.VELOCITY_TYPE_SMFA.equalsIgnoreCase(velocityType)){
    		passVelocityCheck = !smfaVelocityCheckService.exceededLinkAttemptsLimit(person, personVo, phoneSupplier, rpEvent);
        }
     	
    	return passVelocityCheck;
    }

    public String passcodeConfirmation(String origin, PersonVo personVo, RefOtpSupplier phoneSupplier) {
    	CustomLogger.enter(this.getClass());

        try {
            // First check if the passcode attempt exceeded or passcode expired or transaction expired
            // Must be done to prevent a SOAPFaultException being thrown
            // because the transactionID is valid, but the type of request being made is not

        	RpEvent rpEvent = rpEventDataService.findLatestEventByPersonId(personVo.getId());
            boolean passcodeConfirmed = phoneVerificationService.confirmPasscode(personVo, phoneSupplier, rpEvent);      
            
            if (!passcodeConfirmed) {
                CustomLogger.info(this.getClass(), "Passcode was not confirmed for customer " + personVo.getSponsorUserId());
               
             	return REASON_FAILED_OTP_CONFIRMATION_MISMATCH;
             }
        } catch (Exception e) {
		personVo.setErrorMessage(e.getMessage());
        	return "error in confirming passcode";
        }

        return null;
    }
    
    public String checkPhoneVerified(Person person, PersonVo personVo, RpEvent rpEvent, RefOtpSupplier phoneSupplier, String serviceName, String origin) {
    	CustomLogger.enter(this.getClass());

    	boolean phoneVerified = false;
    	boolean phoneApproved = false;
    	boolean phoneForReview = false;
    	String phoneVerificationDecision = DECISION_FAIL;
     	
    	RpPhoneVerification phoneVerification = rpPhoneVerificationService.findPhoneVerificationByEventId(rpEvent.getEventId());
 		if (phoneVerification != null) {
			phoneVerified = phoneVerification.isPhoneVerified();
			phoneApproved = phoneVerification.isEquifaxDITPhoneVerificationPassed() 
					|| phoneVerification.isExperianPhoneVerificationApproved();
			phoneForReview = phoneVerification.isEquifaxDITPhoneVerificationForReview()
	        		|| phoneVerification.isExperianPhoneVerificationForReview();
			
			if (phoneVerification.getTransactionKey() != null) {
	           	personVo.setTransactionId(phoneVerification.getTransactionKey());
            }
		} else {
        	return REASON_FAILED_PHONE_VERIFICATION;
        }
     
        if (phoneApproved) {
        	phoneVerificationDecision = RpPhoneVerification.DECISION_APPROVE;
        }
        else if (phoneForReview) {
        	phoneVerificationDecision = RpPhoneVerification.DECISION_REVIEW;
        }
        else if (phoneVerified) {
        	phoneVerificationDecision = RpPhoneVerification.DECISION_PASS;
        }

      	String velocityType = phoneSupplier.isEquifaxDITPhone() ? IPSConstants.VELOCITY_TYPE_SMFA : IPSConstants.VELOCITY_TYPE_PASSCODE;
      	String attemptType = CONFIRM_PASSCODE.equalsIgnoreCase(serviceName) || VALIDATE_LINK.equalsIgnoreCase(serviceName) ?
      			ATTEMPT_TYPE_CONFIRM : ATTEMPT_TYPE_REQUEST;
      	personVo.setAttemptType(attemptType);
        // Check the number of passcode or resend link attempts.
   		boolean passOtpVelocity = passVelocityCheck(velocityType, person, personVo, phoneSupplier, rpEvent);

   		String returnReason = null;
   		if (!passOtpVelocity) {
   			returnReason = phoneSupplier.isEquifaxDITPhone() ?  REASON_FAILED_SMFA_VELOCITY :  REASON_FAILED_OTP_VELOCITY;
   		}
   		else {
	        if (RpPhoneVerification.DECISION_FAIL.equalsIgnoreCase(phoneVerificationDecision)
	        		|| RpPhoneVerification.DECISION_DENY.equalsIgnoreCase(phoneVerificationDecision)) {
	            CustomLogger.info(this.getClass(), "Phone was not verified for customer " + personVo.getSponsorUserId());
	             
	            returnReason = REASON_FAILED_PHONE_VERIFICATION;
	         }
	        else if (RpPhoneVerification.DECISION_APPROVE.equalsIgnoreCase(phoneVerificationDecision)) {
	            CustomLogger.info(this.getClass(), "Phone was previously verified for customer " + personVo.getSponsorUserId());
	
	            returnReason = REASON_APPROVED_PHONE_VERIFICATION;
	        }
   		}
   		
   		return returnReason;
    }

    public String checkOTPSent(PersonVo personVo, RpEvent rp) {
    	CustomLogger.enter(this.getClass());

        List<PersonProofingStatus> proofingStatus = rp.getPerson().getProofingStatuses();
        Long statusCode = null;
        if (!proofingStatus.isEmpty()) {
            statusCode = proofingStatus.get(proofingStatus.size() - 1).getRefRpStatus().getStatusCode();
        }

        // If statusCode is equal to otp_sent then user is entering passcode for first time.
        // If statusCode is equal to initiated or failed, then user is retrying passcode.
        boolean otpSent = statusCode != null && (statusCode.longValue() == RefRpStatus.RpStatus.OTP_sent.getValue()
                || statusCode.longValue() == RefRpStatus.RpStatus.OTP_confirmation_initiated.getValue()
                || statusCode.longValue() == RefRpStatus.RpStatus.OTP_confirmation_failed.getValue());
        if (!otpSent) {
            CustomLogger.info(this.getClass(), "Passcode was not sent to phone for customer " + personVo.getSponsorUserId());
           	return REASON_FAILED_OTP_DELIVERY;           
        }

        return null;
    }

    /**
     * Builds the Response for OTP+PV endpoints
     * 
     * @param status
     *            - HTTP status code for the response. Pass in null to indicate a
     *            200 response
     * @param pvResponse
     *            - RemoteResponse object
     * @return a built Response object with the correct status code and values
     */
    public Response buildPVResponse(Integer status, JSONObject pvResponse, String origin) {
    	CustomLogger.enter(this.getClass());

        Response resp = status != null ? Response.status(status).entity(pvResponse).build()
                : Response.ok(pvResponse, MediaType.APPLICATION_JSON).build();
        ResponseBuilder rb = Response.fromResponse(resp);
        rb.header(ACCESS_CTL_ALLOW_ORIGIN, origin);
        rb.header(ACCESS_CTL_ALLOW_HEADERS, CONTENT_TYPE_HEADER_VALUE);
        resp = rb.build();

        return resp;
    }
    
    /**
     * Builds the Response for Device/Email Risk Assessment DA+ERA endpoints
     * 
     * @param status
     *            - HTTP status code for the response. Pass in null to indicate a
     *            200 response
     * @param pvResponse
     *            - RemoteResponse object
     * @return a built Response object with the correct status code and values
     */
    public Response buildCommonResponse(Integer status, JSONObject pvResponseJSON, String origin) {
    	CustomLogger.enter(this.getClass());
    	String requestStr = new Gson().toJson(pvResponseJSON);

        Response resp = status != null ? Response.status(status).entity(pvResponseJSON).build()
                : Response.ok(pvResponseJSON, MediaType.APPLICATION_JSON).build();
        ResponseBuilder rb = Response.fromResponse(resp);
        rb.header(ACCESS_CTL_ALLOW_ORIGIN, origin);
        rb.header(ACCESS_CTL_ALLOW_HEADERS, CONTENT_TYPE_HEADER_VALUE);
        resp = rb.build();
 
       	return resp;
    }
    
    /**
     * Builds the Error Response for Device/Email Risk Assessment DA+ERA endpoints
     * 
     * @param status
     *            - HTTP status code for the response. Pass in null to indicate a
     *            200 response
     * @param pvResponse
     *            - RemoteResponse object
     * @return a built Response object with the correct status code and values
     */
    public Response buildCheckDevicePlusErrorResponse(Integer status, JSONObject errorResponseJson, String origin) {
    	CustomLogger.enter(this.getClass());

        Response resp = status != null ? Response.status(status).entity(errorResponseJson).build()
                : Response.ok(errorResponseJson, MediaType.APPLICATION_JSON).build();
        ResponseBuilder rb = Response.fromResponse(resp);
        rb.header(ACCESS_CTL_ALLOW_ORIGIN, origin);
        rb.header(ACCESS_CTL_ALLOW_HEADERS, CONTENT_TYPE_HEADER_VALUE);
        resp = rb.build();

       	return resp;
    }
    
    /**
     * Builds the Response for OTP+PV endpoints for error codes
     * 
     * @param pvResponse - JSONObject object
     * @return a built Response object with the correct status code and values
     */
    public Response buildErrorPVResponse(Integer status, String message, String origin) {
    	CustomLogger.enter(this.getClass());

        JSONObject resp = new JSONObject();
        resp.put("responseMessage", message);
        return buildErrorPVResponse(status, resp, origin);
    }
    
    /**
     * Builds the Response for OTP+PV endpoints for error codes
     * 
     * @param pvResponse
     *            - JSONObject object
     * @return a built Response object with the correct status code and values
     */
    public Response buildErrorPVResponse(Integer status, JSONObject pvResponse, String origin) {
    	CustomLogger.enter(this.getClass());

        Response resp = Response.status(status).entity(pvResponse).type(MediaType.APPLICATION_JSON).build();
        ResponseBuilder rb = Response.fromResponse(resp);
        rb.header(ACCESS_CTL_ALLOW_ORIGIN, origin);
        rb.header(ACCESS_CTL_ALLOW_HEADERS, CONTENT_TYPE_HEADER_VALUE);
        resp = rb.build();
        return resp;
    }

    /**
     * Builds the response if an internal error occurs
     * 
     * @param userID
     *            - person user id
     * @param origin
     *            - header parameter origin
     * @param message
     *            - a message stating where the internal error occurred
     * @param e
     *            - the exception that occurred
     * @return a 500 Response object
     */
    public Response buildErrorResponse(String origin, long sponsorId, String requestId, String serviceName, String message) {
    	CustomLogger.enter(this.getClass());

    	/*
    	if (serviceName != null) {
	        sponsorIncomingRequestsService.documentAgencyRequest(sponsorId, requestId, serviceName,
	        		Response.Status.INTERNAL_SERVER_ERROR, message);
    	}
    	*/

        return buildErrorPVResponse(ErrorMessage.INVALID_TRANSACTION.getHttpResponseCode(), message, origin);
    }

    /**
     * Merge RemoteRequest into PersonVo object
     * 
     * @param remReq
     *            Request for verification from sponsor
     * @return Completed PersonVo object
     */
    public PersonVo mergeObjects(RemoteRequest remReq, String serviceName) {
 
        PersonVo personVo = new PersonVo();
        RefSponsor sponsor = null;
        String sponsorName = "";
        String appName = "";
        String sponsorCode = remReq.getSponsorCode();
        String appCode = remReq.getAppCode();
        long sponsorId = 0L;
        
        personVo.setCallingMethod(serviceName);     
      	personVo.setWebServiceCall(true);

        if (sponsorCode != null) {
        	if (!Utils.isEmptyString(sponsorCode)) {
        		sponsorName = determineSponsorName(sponsorCode);
        	}

	        if (!Utils.isEmptyString(sponsorName) && !SPONSOR_NAME_UNKNOWN.equalsIgnoreCase(sponsorName)) {
		        sponsor = refSponsorDataService.findBySponsorName(sponsorName);
		        if (sponsor != null) {
			        sponsorId = sponsor.getSponsorId();
		        }
	        }
        }
        else {
        	if (remReq.getSponsorID() != null) {
         		sponsor = refSponsorService.findByPK((long) remReq.getSponsorID());
         		if (sponsor != null) {
	         		sponsorName = sponsor.getSponsorName();
         		    sponsorId = sponsor.getSponsorId();
         		}
        	}
        }

        personVo.setSponsorId(sponsorId);
 		personVo.setSponsor(sponsorName);
         
        if (remReq.getCustomerUniqueID() != null) {
        	personVo.setSponsorUserId(remReq.getCustomerUniqueID());
        }
        if (remReq.getFirstName() != null) {
            personVo.setFirstName(remReq.getFirstName());
        }
        if (remReq.getLastName() != null) {
            personVo.setLastName(remReq.getLastName());
        }
        if (remReq.getStreetAddress1() != null) {
            personVo.setAddressLine1(remReq.getStreetAddress1());
        }
        if (remReq.getStreetAddress2() != null) {
            personVo.setAddressLine2(remReq.getStreetAddress2());
        }
        if (remReq.getCity() != null) {
            personVo.setCity(remReq.getCity());
        }
        if (remReq.getState() != null) {
            personVo.setStateProvince(remReq.getState().toUpperCase());
        }
        if (remReq.getZipCode() != null) {
            personVo.setPostalCode(remReq.getZipCode());
        }
        if (remReq.getEmailAddress() != null) {
            personVo.setEmailAddress(remReq.getEmailAddress());
        }
        if (remReq.getMobilePhone() != null) {
            personVo.setMobileNumber(remReq.getMobilePhone());
        }
        if (remReq.getDeviceTypeMobile() != null) {
            personVo.setDeviceTypeMobile(VALUE_TRUE.equalsIgnoreCase(remReq.getDeviceTypeMobile()));
        }
        if (remReq.getBirthDate() != null) {
            personVo.setDob(DateTimeUtil.getDate(remReq.getBirthDate(), MMDDYYYY_DATE_FMT));
        }
        if (remReq.getPasscode() != null) {
            personVo.setPasscode(remReq.getPasscode());
        }
        if (remReq.getOtpExpiresMinutes() != null) {
        	personVo.setOtpExpiresMinutes(remReq.getOtpExpiresMinutes());
        }
        if (remReq.getSmsUrl() != null) {
        	personVo.setSmsUrl(remReq.getSmsUrl());
        }
        if (remReq.getTargetUrl() != null) {
        	personVo.setTargetUrl(remReq.getTargetUrl());
        }
        if (remReq.getStubCaseKey() != null) {
        	personVo.setStubCaseKey(remReq.getStubCaseKey());
        }
        if (remReq.getReturnDebugData() != null) {
        	personVo.setReturnDebugData(true);
        }
        if (remReq.getDeviceTypeMobile() != null) {
        	personVo.setDeviceTypeMobile(VALUE_TRUE.equalsIgnoreCase(remReq.getDeviceTypeMobile()));
        }
        if (remReq.getProfilingSessionID() != null) {
        	personVo.setProfilingSessionID(remReq.getProfilingSessionID());
        }
        if (remReq.getWebSessionID() != null) {
        	personVo.setWebSessionID(remReq.getWebSessionID());
        }
        if (remReq.getTrueIPAddress() != null) {
        	personVo.setTrueIPAddress(remReq.getTrueIPAddress());
        }
        if (remReq.getCheckHighRiskAddress() != null) {
        	personVo.setCheckHighRiskAddress("true".equalsIgnoreCase(remReq.getCheckHighRiskAddress()));
        }
        
        personVo.setProofingLevelSought(PROOFING_LEVEL_1_5);
        personVo.setTermsConditionsAckDateTime(new Date());
 
        RefApp app = determineTransactionOriginApp(sponsor, remReq.getAppCode());
        personVo.setCallingAppName(app != null ? app.getAppName() : APP_NAME_UNKNOWN);
        personVo.setAppId(app != null ? app.getAppId() : 0L);
 
        if (!Utils.isEmptyString(appCode)) {
        	appName = determineAppName(appCode);
        }

    	personVo.setAppName(appName);

        return personVo;
    }
    
    public void populatePersonVo(RemoteRequest remReq, PersonVo personVo) {
         
        personVo.setCallingMethod(remReq.getAssessmentCall());     
      	personVo.setWebServiceCall(true);
      	
      	if (remReq.getCustomerUniqueID() != null) {
	      	personVo.setSponsorUserId(remReq.getCustomerUniqueID().trim());
      	}
      	if (remReq.getFirstName() != null) {
	      	personVo.setFirstName(remReq.getFirstName().trim());
      	}
      	if (remReq.getLastName() != null) {
	      	personVo.setLastName(remReq.getLastName().trim());
      	}
      	if (!Utils.isEmptyString(remReq.getMiddleName())) {
	      	personVo.setMiddleName(remReq.getMiddleName().trim());
      	} else {
      		personVo.setMiddleName("");
      	}
        if (remReq.getStreetAddress1() != null) {
            personVo.setAddressLine1(remReq.getStreetAddress1().trim());
        }    	
        if (remReq.getStreetAddress2() != null) {
            personVo.setAddressLine2(remReq.getStreetAddress2().trim());
        }
        if (remReq.getStreetAddress3() != null) {
            personVo.setAddressLine3(remReq.getStreetAddress3().trim());
        }
        if (remReq.getCity() != null) {
            personVo.setCity(remReq.getCity().trim());
        }
        if (remReq.getState() != null) {
        	personVo.setStateProvince(remReq.getState().trim().toUpperCase());
        }
        if (remReq.getCountry() != null) {
        	personVo.setCountry(remReq.getCountry().trim());
        }
        if (remReq.getZipCode() != null) {
        	personVo.setPostalCode(remReq.getZipCode().trim());
        }      
        if (remReq.getUrbanizationCode() != null) {
            personVo.setUrbanizationCode(remReq.getUrbanizationCode().trim());
        }
        if (remReq.getEmailAddress() != null) {
            personVo.setEmailAddress(remReq.getEmailAddress().trim());
        }
        if (remReq.getCompanyName() != null) {
            personVo.setCompanyName(remReq.getCompanyName().trim());
        }
        if (remReq.getCompanyFEIN() != null) {
            personVo.setCompanyFEIN(remReq.getCompanyFEIN().trim());
        }
        if (remReq.getMobilePhone() != null) {
            personVo.setMobileNumber(remReq.getMobilePhone().trim());
        }

        personVo.setDeviceTypeMobile(VALUE_TRUE.equalsIgnoreCase(remReq.getDeviceTypeMobile()));

        if (remReq.getPasscode() != null) {
            personVo.setPasscode(remReq.getPasscode().trim());
        }
        if (remReq.getOtpExpiresMinutes() != null) {
        	personVo.setOtpExpiresMinutes(remReq.getOtpExpiresMinutes().trim());
        }
        if (remReq.getSmsUrl() != null) {
        	personVo.setSmsUrl(remReq.getSmsUrl().trim());
        }
        if (remReq.getTargetUrl() != null) {
        	personVo.setTargetUrl(remReq.getTargetUrl().trim());
        }
        if (remReq.getStubCaseKey() != null) {
        	personVo.setStubCaseKey(remReq.getStubCaseKey().trim());
        }
        if (remReq.getReturnDebugData() != null) {
        	personVo.setReturnDebugData(true);
        }
        
    	personVo.setDeviceTypeMobile(VALUE_TRUE.equalsIgnoreCase(remReq.getDeviceTypeMobile()));
    	personVo.setProfilingSessionID(remReq.getProfilingSessionID());
    	personVo.setWebSessionID(remReq.getWebSessionID());
    	personVo.setTrueIPAddress(remReq.getTrueIPAddress());
      	personVo.setCheckHighRiskAddress(VALUE_TRUE.equalsIgnoreCase(remReq.getCheckHighRiskAddress()));
        personVo.setProofingLevelSought(PROOFING_LEVEL_1_5);
        personVo.setTermsConditionsAckDateTime(new Date());
    }
    
    public void populatePersonVoFromPerson(Person person, PersonVo personVo) {
       	personVo.setWebServiceCall(true);
      	
       	if (person.getSponsorUserId() != null) {
	      	personVo.setSponsorUserId(person.getSponsorUserId());
      	}
      	
      	PersonData personData = person.getPersonData();
      	if (personData != null) {
      		if (personData.getFirstName() != null) {
      			personVo.setFirstName(personData.getFirstName());
      		}
      		if (personData.getLastName() != null) {
      			personVo.setLastName(personData.getLastName());
      		}
      		if (!Utils.isEmptyString(personData.getMiddleName())) {
      			personVo.setMiddleName(personData.getMiddleName());
      		} 
      		if (personData.getAddressLine1() != null) {
      			personVo.setAddressLine1(personData.getAddressLine1());
      		}
      		if (personData.getAddressLine2() != null) {
      			personVo.setAddressLine2(personData.getAddressLine2());
      		}
      		if (personData.getCity() != null) {
      			personVo.setCity(personData.getCity());
      		}
      		if (personData.getStateProvince() != null) {
      			personVo.setStateProvince(personData.getStateProvince());
      		}
      		if (personData.getPostalCode() != null) {
      			personVo.setPostalCode(personData.getPostalCode());
      		}
      		if (personData.getCountryName() != null) {
      			personVo.setCountry(personData.getCountryName());
      		}
      		if (personData.getPhoneINT() != null) {
      			personVo.setMobileNumber(personData.getPhoneINT());
      			personVo.setPhoneNumber(personData.getPhoneINT());
      		}
       		if (personData.getEmailAddress() != null) {
      			personVo.setEmailAddress(personData.getEmailAddress());
      		}
       	}
    }
    
    public void setSponsorAppData(RemoteRequest remReq, PersonVo personVo) {

        RefSponsor refSponsor = null;
        String sponsorName = "";
        String appName = "";
        String sponsorCode = remReq.getSponsorCode();
        String appCode = remReq.getAppCode();
        long sponsorId = 0L;
           
        sponsorName = determineSponsorName(sponsorCode);

        if (!Utils.isEmptyString(sponsorName) && !SPONSOR_NAME_UNKNOWN.equalsIgnoreCase(sponsorName)) {
	        refSponsor = refSponsorDataService.findBySponsorName(sponsorName);
	        if (refSponsor != null) {
		        sponsorId = refSponsor.getSponsorId();
	        }
        }
      
        personVo.setSponsorId(sponsorId);
 		personVo.setSponsor(sponsorName);
 		
        if (!SPONSOR_NAME_UNKNOWN.equalsIgnoreCase(personVo.getSponsor())) {
	        refSponsor = refSponsorDataService.findBySponsorName(personVo.getSponsor());

	        if (refSponsor != null) {
		        sponsorId = refSponsor.getSponsorId();
		        personVo.setSponsorId(sponsorId);
	        }
        }
        
        RefApp refApp = determineTransactionOriginApp(refSponsor, remReq.getAppCode());
        personVo.setCallingAppName(refApp != null ? refApp.getAppName() : APP_NAME_UNKNOWN);
        personVo.setAppId(refApp != null ? refApp.getAppId() : 0L);
 
        if (!Utils.isEmptyString(appCode)) {
        	appName = determineAppName(appCode);
        }
        
        String assessmentCall = remReq.getAssessmentCall();
        if (RemoteProofingServiceImpl.CHECK_DEVICE_PLUS.equalsIgnoreCase(assessmentCall) || RemoteProofingServiceImpl.VERIFY_BUSINESS.equalsIgnoreCase(assessmentCall)) { 
	        String addressHash = calculateNameAddressHash(remReq);
	    	personVo.setSponsorUserId(addressHash);
        }
        
    	personVo.setAppName(appName);
    }
    
    /**
     * Calculate the hash from a customer name and address to be used as customerUniqueID. 
     */
    public String calculateNameAddressHash(PersonVo personVo) {
    	int postalCodeHashCode = 0;
    	if (personVo.getPostalCode() != null && personVo.getPostalCode().length() > 4) {
    		postalCodeHashCode = personVo.getPostalCode().substring(0, 5).hashCode();
    	}
       	String addressLine2 = personVo.getAddressLine2() != null? personVo.getAddressLine2() : "";
        int nameAddressHash = personVo.getFirstName().trim().toUpperCase().hashCode()
                + personVo.getLastName().trim().toUpperCase().hashCode()
        		+ personVo.getAddressLine1().toUpperCase().hashCode() 
                + addressLine2.toUpperCase().hashCode() 
                + personVo.getCity().toUpperCase().hashCode() 
                + personVo.getStateProvince().toUpperCase().hashCode() 
                + postalCodeHashCode;
               
        String nameAddressHashStr = String.valueOf(nameAddressHash).replace("-","");
        if (nameAddressHashStr.length() > 10) {
        	nameAddressHashStr = nameAddressHashStr.substring(0, 10);
        }
        
        return nameAddressHashStr;
    }
    
    /**
     * Calculate the hash from a customer name and address to be used as customerUniqueID. 
     */
    public String calculateNameAddressHash(RemoteRequest remReq) {
    	String companyName = remReq.getCompanyName() != null? remReq.getCompanyName().toUpperCase() : "";
    	String firstName = remReq.getFirstName() != null? remReq.getFirstName().toUpperCase() : "";
    	String lastName = remReq.getLastName() != null? remReq.getLastName().toUpperCase() : "";
    	String addressLine1 = remReq.getStreetAddress1() != null? remReq.getStreetAddress1().toUpperCase() : "";
       	String addressLine2 = remReq.getStreetAddress2() != null? remReq.getStreetAddress2().toUpperCase() : "";
       	String city = remReq.getCity() != null? remReq.getCity().toUpperCase() : "";
     	String stateProvince = remReq.getState() != null? remReq.getState().toUpperCase() : "";
     	String postalCode = remReq.getZipCode() != null? remReq.getZipCode().toUpperCase() : "";
    	String country = remReq.getCountry() != null? remReq.getCountry().toUpperCase() : "US";

     	int nameAddressHash = companyName.hashCode() + firstName.hashCode() + lastName.hashCode() 
                + addressLine1.hashCode() + addressLine2.hashCode() + city.hashCode()
        		+ stateProvince.hashCode() + postalCode.hashCode() + country.hashCode();
               
        String nameAddressHashStr = String.valueOf(nameAddressHash).replace("-","");
        if (nameAddressHashStr.length() > 10) {
        	nameAddressHashStr = nameAddressHashStr.substring(0, 10);
        }
        
        return nameAddressHashStr;
    }
    
    /**
     * Calculate the hash directly from RemoteRequest. 
     */
    public String calculateNameAddressHashFromRequest(RemoteRequest remoteReq, boolean forPersonId) {
    	int postalCodeHashCode = 0;
    	if (remoteReq.getZipCode() != null && remoteReq.getZipCode().length() > 4) {
    		postalCodeHashCode = remoteReq.getZipCode().substring(0, 5).hashCode();
    	}
       	String addressLine2 = remoteReq.getStreetAddress2() != null? remoteReq.getStreetAddress2().toUpperCase() : "";
       	String state = remoteReq.getState() != null? remoteReq.getState().toUpperCase() : "";
     	String firstName = remoteReq.getFirstName() != null? remoteReq.getFirstName().trim().toUpperCase() : "";
     	String lastName = remoteReq.getLastName() != null? remoteReq.getLastName().trim().toUpperCase() : "";
     	String streetAddress1 = remoteReq.getStreetAddress1() != null? remoteReq.getStreetAddress1().trim().toUpperCase() : "";
     	String city = remoteReq.getCity() != null? remoteReq.getCity().trim().toUpperCase() : "";
                  
        int nameAddressHash = firstName.hashCode()
                + lastName.hashCode()
        		+ streetAddress1.hashCode() 
                + addressLine2.hashCode() 
                + city.hashCode() 
                + state.hashCode() 
                + postalCodeHashCode;
               
        String nameAddressHashStr = String.valueOf(nameAddressHash).replace("-","");
        if (nameAddressHashStr.length() > 10) {
        	nameAddressHashStr = forPersonId ? nameAddressHashStr.substring(0, 8) : nameAddressHashStr.substring(0, 10);
        }
        
        return nameAddressHashStr;
    }

    /**
     * Merge RemoteRequest into DeviceAssessmentParamVo object
     * 
     * @param remReq
     * @return Completed DeviceAssessmentParamVo object
     */
    public CommonAssessmentParamVo mergeParamObjects(RemoteRequest remReq, PersonVo personVo) {
    	CustomLogger.enter(this.getClass());

    	CommonAssessmentParamVo assessmentParamVo = new CommonAssessmentParamVo();
        Long sponsorId = personVo.getSponsorId();

        if (remReq.getProfilingSessionID() != null) {
        	assessmentParamVo.setSessionId(remReq.getProfilingSessionID());
        }
        if (remReq.getWebSessionID() != null) {
        	assessmentParamVo.setWebSessionId(remReq.getWebSessionID());
        }
        
        assessmentParamVo.setSponsorUserId(Utils.isEmptyString(remReq.getCustomerUniqueID()) ? "" : personVo.getSponsorUserId());
    
        if (sponsorId != null && sponsorId != 0L) {
        	RefSponsor sponsor = refSponsorService.findByPK(sponsorId);
        	String appCode = remReq.getAppCode();
        	RefApp app = determineTransactionOriginApp(sponsor, appCode);
        	String callingAppName = app != null ? app.getAppName() : APP_NAME_UNKNOWN;
        	assessmentParamVo.setCallingAppName(callingAppName);
        }
        
        if (!Utils.isEmptyString(remReq.getMobilePhone())) {
        	assessmentParamVo.setMobilePhoneNumber(remReq.getMobilePhone());
        }
        assessmentParamVo.setWebServiceCall(true);
      	
        return assessmentParamVo;
    }
    
    public RefApp determineTransactionOriginApp(RefSponsor sponsor, String appCode) {

    	 RefApp selectedApp = null;

     	if (sponsor != null) {
            List<SponsorApplicationMap> sponsorAppList = sponsorApplicationMapService
                    .getRelationsBySponsor(sponsor.getSponsorId());
   
            if (sponsorAppList == null || sponsorAppList.isEmpty()) {
                 return null;
            } else {
                if (appCode != null) {
                	Map<String, String> appMap = getAppCodeNameMap();
                	
                	String inputAppName = appMap.get(appCode.trim().toUpperCase());
                	
                	
                	for (SponsorApplicationMap spApp : sponsorAppList) {
                		String appName = spApp.getApp().getAppName();
                		if (appName.equalsIgnoreCase(inputAppName)) {
                			selectedApp = spApp.getApp(); 
                			break;
                		}
                 	}
                	return selectedApp;
                }
                else {
                	selectedApp = sponsorAppList.get(0).getApp();
                	return selectedApp;
                }
            }
        }

    	return null;
    }

    /**
     * Retrieves the most recent event for the user to determine which supplier was
     * called
     * 
     * @param personId
     * @return
     */
    public RefOtpSupplier getLatestPhoneSupplier(long personId) {
    	CustomLogger.enter(this.getClass());

        RefOtpSupplier phoneSupplier = null;

        RpEvent rpEvent = rpEventDataService.findLatestEventByPersonId(personId);
        if (rpEvent != null) {
        	long supplierId = rpEvent.getRefOtpSupplier().getOtpSupplierId();
        	phoneSupplier = refOtpSupplierDataService.findBySupplierId(supplierId);
        }
      
	    return phoneSupplier;
    }
    
    public String checkSmfaLinkValidation(PersonVo personVo) {
    	CustomLogger.enter(this.getClass());

        try {
	         boolean linkPassedValidation = false;
	       	 String smfaStatus = equifaxService.getSmfaStatus(personVo, true);
	       	 
	       	 if (STATUS_TOKEN_EXPIRED.equalsIgnoreCase(smfaStatus)) {
				smfaStatus = GetBearerTokenAndSmfaStatus(personVo, smfaStatus);
	       	 }
				  
	       	 switch(smfaStatus) {
	   			case ResponseStatusModel.RESPONSE_PATH_GREEN:  linkPassedValidation = true;
	   				break;
	   			case ResponseStatusModel.RESPONSE_PATH_YELLOW:  linkPassedValidation = true;
	   				break;
	   			default:  
	       	 }

            if (!linkPassedValidation) {
                CustomLogger.info(this.getClass(), "SMFA Link has failed validation for customer " + personVo.getSponsorUserId());

                return REASON_FAILED_SMFA_VALIDATION;
            }
        } catch (Exception e) {
        	return "error in validating SMFA Link ";
        }

        return null;
    }

	private String GetBearerTokenAndSmfaStatus(PersonVo personVo, String smfaStatus) {
		try {
			String bearerToken = equifaxService.generateEquifaxBearerToken(EquifaxServiceImpl.EQUIFAX_SMFA_OAUTH_SCOPE,
					EquifaxServiceImpl.EQUIFAX_SMFA_TOKEN_TYPE, true);
			personVo.setSmfaToken(bearerToken);
			smfaStatus = equifaxService.getSmfaStatus(personVo, false);
		} catch (Exception e) {
		  	CustomLogger.error(this.getClass(), "Generic Exception occured in obtaining new token. Exception Message:" + e.getMessage(), e);  
		}
		return smfaStatus;
	}

	public SponsorConfigurationResponse retrieveSponsorConfiguration() {
    	CustomLogger.enter(this.getClass());
    	SponsorConfigurationResponse sponsorConfigurationResponse = new SponsorConfigurationResponse();

		try {
	        GetTimeConfigBetweenPolling(sponsorConfigurationResponse);
	        
	        GetTimeConfigBeforeResendLink(sponsorConfigurationResponse);
	        
	        getCustRegSponsor(sponsorConfigurationResponse);
	        
	        return sponsorConfigurationResponse;
	    } catch (Exception e) {
	    	String message = "fetching RefSponsorConfiguration values";
	    	CustomLogger.error(this.getClass(), "Exception thrown: " + message + ": ", e);
	
	    	sponsorConfigurationResponse.setResponseMessage(message);
	        return sponsorConfigurationResponse;
	    }
	}

	private void GetTimeConfigBeforeResendLink(SponsorConfigurationResponse sponsorConfigurationResponse)
			throws Exception {
		String configValue;
		RefSponsorConfiguration sponsorConfig;
		try {
		    // This method is only called from the front end so Sponsor will be CustReg.
		    RefSponsor sponsor = refSponsorService.findByPK(RefSponsor.SPONSOR_ID_CUSTREG);
			sponsorConfig = refSponsorConfigService.getConfigRecord(
					(int) sponsor.getSponsorId(), RefSponsorConfiguration.EQUIFAX_SECONDS_BEFORE_RESEND_LINK);
		    configValue = sponsorConfig.getValue();
		    long secondsBeforeResendLink = Long.parseLong(configValue);
			sponsorConfigurationResponse.setSecondsBeforeResendLink(secondsBeforeResendLink);

		} catch (Exception e) {
		    CustomLogger.error(this.getClass(), "Error in getting Equifax.SecondsBeforeResendLink config value.", e);
		    throw e;
		}
	}

	private void GetTimeConfigBetweenPolling(SponsorConfigurationResponse sponsorConfigurationResponse) throws Exception {
		String configValue;
		RefSponsorConfiguration sponsorConfig;
		try {
		    // This method is only called from the front end so Sponsor will be CustReg.
		    RefSponsor sponsor = refSponsorService.findByPK(RefSponsor.SPONSOR_ID_CUSTREG);
			sponsorConfig = refSponsorConfigService.getConfigRecord(
					(int) sponsor.getSponsorId(), RefSponsorConfiguration.EQUIFAX_SECONDS_BETWEEN_POLLING);
		    configValue = sponsorConfig.getValue();
		    long secondsBetweenPolling = Long.parseLong(configValue);
			sponsorConfigurationResponse.setSecondsBetweenPolling(secondsBetweenPolling);

		} catch (Exception e) {
		    CustomLogger.error(this.getClass(), "Error in getting Equifax.SecondsBetweenPolling config value.", e);
		    throw e;
		}
	}

	private void getCustRegSponsor(SponsorConfigurationResponse sponsorConfigurationResponse) throws Exception {
		String configValue;
		RefSponsorConfiguration sponsorConfig;
		try {
		    // This method is only called from the front end so Sponsor will be CustReg.
		    RefSponsor sponsor = refSponsorService.findByPK(RefSponsor.SPONSOR_ID_CUSTREG);
			sponsorConfig = refSponsorConfigService.getConfigRecord(
					(int) sponsor.getSponsorId(), RefSponsorConfiguration.EQUIFAX_POLLING_MINUTES);
		    configValue = sponsorConfig.getValue();
		    long pollingMinutes = Long.parseLong(configValue);
			sponsorConfigurationResponse.setPollingMinutes(pollingMinutes);

		} catch (Exception e) {
		    CustomLogger.error(this.getClass(), "Error in getting Equifax.PollingMinutes config value.", e);
		    throw e;
		}
	}
       
	public Response personNotFoundResponse(String sponsorUserId, String origin) {

		String message = String.format("Person with customer ID %s not found", sponsorUserId);
	    CustomLogger.info(this.getClass(), message);
	    
	    return buildErrorPVResponse(400, message, origin);
    }
	
	public Response personNotFoundResponse(long sponsorID, String sponsorUserId, String requestId, 
    		String serviceName, String origin) {

		String message = String.format("Person with customer ID %s not found", sponsorUserId);
	    CustomLogger.info(this.getClass(), message);
	    
	    return buildErrorPVResponse(400, message, origin);
    }
	
	public Response eventNotFoundResponse(long otpSupplierId, String sponsorUserId, String origin) {
		String message = String.format("Remote proofing event for customer ID %s with supplier ID %s not found",
				sponsorUserId, otpSupplierId);
	
	    return buildErrorPVResponse(400, message, origin);
    }
	
	public Response invalidSupplierResponse(long otpSupplierId, String sponsorUserId, String callerDesc, String origin) {
		String message = "";

		if (RefOtpSupplier.EQUIFAX_DIT_SMFA_SUPPLIER_ID == otpSupplierId) {
			message = String.format("%s failed since it is not implemented with Equifax DIT SMFA for customer ", callerDesc, sponsorUserId);
		} 
		else if (RefOtpSupplier.LEXISNEXIS_RDP_OTP_SUPPLIER_ID == otpSupplierId) {
			message = String.format("%s failed since it is not implemented with OTP supplier for customer ", callerDesc, sponsorUserId);
		}
		else {
			message = String.format("%s failed for customer ", callerDesc, sponsorUserId);
		}
	
	    return buildErrorPVResponse(400, message, origin);
    }
    
	public Response latestEventErrorResponse(PersonVo personVo, String serviceName, String origin, Exception e) {
   
	    String requestId = String.format("%s - %s", personVo.getSponsorUserId(), DateTimeUtil.getCurrentDateTime());
		String message = "while getting latest phone verification event";
		message = String.format(EXCEPTION_MSG_FMT, personVo.getSponsorUserId(), message);
	
	    return buildErrorResponse(origin, personVo.getSponsorId(), requestId, serviceName, message);
    }
    
	public Response exceptionErrorResponse(PersonVo personVo, String serviceName, String message, String origin, Exception e) {
	    String requestId = String.format("%s - %s", personVo.getSponsorUserId(), DateTimeUtil.getCurrentDateTime());

    	if (personVo.getErrorMessage() != null) {
    		message = personVo.getErrorMessage();
    	}
    	else {
	       	String userId = personVo.getSponsorUserId();
		    message = String.format("while %s",  message);
		    message = String.format(EXCEPTION_MSG_FMT, userId, message);
    	}
	    
	    if (e != null) {
	    	CustomLogger.error(this.getClass(), message, e);
	    }
	
	    return buildErrorResponse(origin, personVo.getSponsorId(), requestId, serviceName, message);
    }
  
	public void saveDeviceReputationAssessmentErrorResponse(PersonVo personVo, RemoteRequest remoteReq, Response errorResponse) {
		Gson g = new Gson();
		String responseStr = g.toJson(errorResponse);
	
        String requestId = getRequestId(personVo);
       	String requestStr = g.toJson(remoteReq);
         
       	saveDeviceReputationAssessmentErrorResponse(personVo, remoteReq, responseStr);
       	/*
        if (personVo.getId() == 0L) {
        	String personIdStr = calculateNameAddressHashFromRequest(remoteReq, true);
        	long personId = Long.valueOf(personIdStr);
        	personVo.setId(personId);
        }
        
        Person person = null;
		try {
			person = createPerson(personVo, remoteReq);
		} catch (Exception e) {
			String errorMsg = "Error in creating Person object.";
            CustomLogger.error(this.getClass(), errorMsg, e);
            responseStr = errorMsg;
        }
  
		if (person != null) {
			saveRpDeviceReputationErorResponse(person, requestStr, responseStr, requestId);
		}
		*/
	}

	public void saveDeviceReputationAssessmentErrorResponse(PersonVo personVo, RemoteRequest remoteReq, String responseStr) {
        String requestId = getRequestId(personVo);
 
        Gson g = new Gson();
       	String requestStr = g.toJson(remoteReq);
         
        if (personVo.getId() == 0L) {
        	String personIdStr = calculateNameAddressHashFromRequest(remoteReq, true);
        	long personId = Long.valueOf(personIdStr);
        	personVo.setId(personId);
        }
        
        Person person = null;
		try {
			person = createPerson(personVo, remoteReq);
		} catch (Exception e) {
			String errorMsg = "Error in creating Person object.";
            CustomLogger.error(this.getClass(), errorMsg, e);
            responseStr = errorMsg;
        }
  
		if (person != null) {
			saveRpDeviceReputationErorResponse(person, requestStr, responseStr, requestId);
		}
    }
 	
	public void saveRpDeviceReputationErorResponse(Person person, String requestStr, String responseStr, String requestId) {
        Timestamp currentDateTime = new Timestamp(new Date().getTime());
  
		if (person != null) {

			String saveRequest = String.format("Remote Request: %s", requestStr);
			if (saveRequest.length() > 2000) {
	        	saveRequest = saveRequest.substring(0, 1990);
	        }
	        
			String saveResponse = String.format("Error Response: %s", responseStr);
	        if (saveResponse.length() > 4000) {
	        	saveResponse = saveResponse.substring(0, 3900);
	        }
		        
	        RefCustomerCategory refCustomerCategory = refCustomerCategoryService.findByCategoryName(RefCustomerCategory.CUSTOMER_CATEGORY_NAME_BUSINESS);
	    	RefWorkflowApiType refWorkflowApiType = refWorkflowApiTypeService.findByApiTypeCode(RefWorkflowApiType.API_TYPE_CODE_DERA);

	        RpDeviceReputationResponse deviceReputationResponse = new RpDeviceReputationResponse();
	        deviceReputationResponse.setPerson(person);
	        deviceReputationResponse.setRequestId(requestId);
	        deviceReputationResponse.setRequest(saveRequest);
	        deviceReputationResponse.setResponse(saveResponse);
	        deviceReputationResponse.setOverallDecision(IPSConstants.TRANSACTION_STATUS_FAILED);
	        deviceReputationResponse.setRefApp(null);
	        deviceReputationResponse.setRefCustomerCategory(refCustomerCategory);
	        deviceReputationResponse.setRefWorkflowApiType(refWorkflowApiType);
	        deviceReputationResponse.setCreateDate(currentDateTime);
	        
	        try {
	            rpDeviceReputationResponseService.create(deviceReputationResponse);
	        } catch (Exception e) {
	            CustomLogger.error(this.getClass(), "Error in saving Device Reputation Response.", e);
	        }	
		}
    }
	
	public Map<String, String> getAppCodeNameMap() {
    	List<RefApp> appList = refAppService.list();
    	Map<String, String> appCodeNameMap = new LinkedHashMap<>();
    	
     	for(RefApp app : appList) {
    		String appName = app.getAppName().trim();
    		String[] nameParts = appName.split(" ");
    		String dbAppCode = "";
    		
    		if (nameParts.length > 0) {
    			for (int i = 0; i < nameParts.length; i++) {
    			    String part = nameParts[i];
    			    String startChar = part.substring(0,1).toUpperCase();
     			    dbAppCode += startChar;
     			}
    		}

    		appCodeNameMap.put(dbAppCode.toUpperCase(), appName);
       	}
    	
    	return appCodeNameMap;
	}
	
	private Person createPerson(PersonVo personVo, RemoteRequest remoteReq) throws Exception {
        CustomLogger.enter(this.getClass());
        String sponsorUserIdStr = personVo.getSponsorUserId();
        long sponsorUserId = 0L;
        
        if (sponsorUserIdStr == null) {
        	sponsorUserIdStr = calculateNameAddressHashFromRequest(remoteReq, false);
        	
    		sponsorUserId = Long.valueOf(sponsorUserIdStr.toString());
    		personVo.setSponsorId(sponsorUserId);
        }
        
        if (sponsorUserIdStr.length() > 18) {
        	sponsorUserIdStr = sponsorUserIdStr.substring(0, 18);
        }
          
        Person person = new Person();
        person.setSponsorUserId(sponsorUserIdStr);

        Date currentDate = new Date();

        RefSponsor refSponsor = null;
        Person existingPerson = null;

        try {
            refSponsor = refSponsorDataService.findBySponsorName(RefSponsor.SPONSOR_CUSTREG);

            if (refSponsor != null) {
            	person.setRefSponsor(refSponsor);
            	existingPerson = personDataService.findFirstBySponsor(person.getRefSponsor(), person.getSponsorUserId());
            	if (existingPerson != null) {
            		return existingPerson;
            	}
            }
        } catch (Exception e) {
            CustomLogger.error(this.getClass(), "Exception occurred while retrieving refSponsor of sponsorUserId: "
                    + person.getSponsorUserId() + ": ", e);
            throw e;
        }
  
        Timestamp currentDateTime = new Timestamp(new Date().getTime());

        // Do all of the database access up front to prevent duplicate person records
        // from being created

        RefLoaLevel noLevel = refLoaLevelService.findByCode(RefLoaLevel.NO_LEVEL_CODE);
        RefRpStatus newStatus = refRpStatusService.findByDescription(IPSConstants.STATUS_NEW_TO_IPS);

         String uid = proofingService.getUniqueUID();

        try {
            person.setKbaUid(uid);
            personVo.setKbaUid(uid);
            person.setCreateDate(currentDateTime);

            // LOA achieved must be set
            person.setAchievedLoaLevel(noLevel);

            PersonProofingStatus proofingStatus = new PersonProofingStatus();
            PersonProofingStatusPK proofingStatusPK = new PersonProofingStatusPK();
            proofingStatusPK.setPersonId(personVo.getId());
            proofingStatusPK.setProofingLevelSought(RefLoaLevel.LOA15_CODE);
           
            proofingStatus.setId(proofingStatusPK);
            RefLoaLevel refLoaLevel = refLoaLevelService.findByLevel(RefLoaLevel.LOA_15);
            proofingStatus.setRefLoaLevel(refLoaLevel);
            proofingStatus.setRefRpStatus(newStatus);
            proofingStatus.setProofingStatusDatetime(currentDateTime);
            proofingStatus.setCreateDate(currentDateTime);

            //PersonData will be removed for non-CustReg sponsor transaction
            //PersonData personData = getPersonData(null, personVo);
             
            person.setEnteredDateTime(currentDate);
            // Proofing level sought must be set so that proofing level can be captured in audit_person.
            // This allows reporting Entered IVS by proofing level.
            person.setSoughtLoaLevel(noLevel);
            
            if (refSponsor != null) {
            	 person.setRefSponsor(refSponsor);
           
            	 personDataService.create(person);
            }
        } catch (Exception e) {
            CustomLogger.error(this.getClass(),
                    "Error creating a person with SponsorUserId: " + person.getSponsorUserId() + ": ", e);
            throw e;
        }


        return person;
    }
	
	private String determineSponsorName(String sponsorCode) {
		String sponsorName = SPONSOR_NAME_UNKNOWN;	
		
		if (sponsorCode != null) {
			sponsorCode = sponsorCode.trim().toUpperCase();
			
			switch(sponsorCode) {
				case RefSponsor.SPONSOR_CODE_OS:
					sponsorName = RefSponsor.SPONSOR_OPERATION_SANTA;
					break;
				case RefSponsor.SPONSOR_CODE_COA:
					sponsorName = RefSponsor.SPONSOR_CHANGE_ADDRESS;
					break;
				case RefSponsor.SPONSOR_CODE_CR:
					sponsorName = RefSponsor.SPONSOR_CUSTREG;
					break;
				default:
					sponsorName = SPONSOR_NAME_UNKNOWN;				
			}
		}
		
    	return sponsorName;
	}
	
	private String determineAppName(String appCode) {
		String appName = "";
		appCode = appCode.toUpperCase();
		
		Map<String, String> appCodeNameMap = getAppCodeNameMap();
		appName = appCodeNameMap.get(appCode);
		
		if (appName == null) {
			appName = APP_NAME_UNKNOWN;		
		}

		return appName;
	}
	
	private String getRequestId(PersonVo personVo) {
        String requestId = personVo.getRequestId();
        
        if (requestId == null) {
        	requestId = String.format("DT%s",  DateTimeUtil.getCurrentDateTime());
        }
         
        return requestId;
	}
	
	public static InputValidationError getInputValidationError(String fieldName, String fieldValue, String validationErrorMsg) {   
		InputValidationError inputValidationError = new InputValidationError();
		inputValidationError.setFieldName(fieldName);
		
		if (fieldValue != null) {
			inputValidationError.setFieldValue(fieldValue);
		}
		
		inputValidationError.setErrorMessage(validationErrorMsg);
		
		return inputValidationError;
    }

}
