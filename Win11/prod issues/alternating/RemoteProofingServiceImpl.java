package com.ips.service;

import java.io.Serializable;
import java.util.List;

import javax.ws.rs.core.Response;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.gson.Gson;
import com.ibm.json.java.JSONObject;
import com.ips.common.common.CustomLogger;
import com.ips.common.common.DateTimeUtil;
import com.ips.common.common.DeviceReviewStatusEnum;
import com.ips.entity.Person;
import com.ips.entity.PersonProofingStatus;
import com.ips.entity.RefApp;
import com.ips.entity.RefCustomerCategory;
import com.ips.entity.RefLoaLevel;
import com.ips.entity.RefOtpSupplier;
import com.ips.entity.RefRpStatus;
import com.ips.entity.RefSponsor;
import com.ips.entity.RefWorkflowApiType;
import com.ips.entity.RpEvent;
import com.ips.entity.RpPhoneVerification;
import com.ips.persistence.common.CommonAssessmentParamVo;
import com.ips.persistence.common.IPSConstants;
import com.ips.persistence.common.PersonVo;
import com.ips.persistence.common.PhoneVerificationResponse;
import com.ips.proofing.ManageEventService;
import com.ips.proofing.PhoneVerificationService;
import com.ips.proofing.ProofingService;
import com.ips.request.RemoteRequest;
import com.ips.response.RemoteResponse;

@Service("remoteProofingService")
@Transactional
public class RemoteProofingServiceImpl implements Serializable, RemoteProofingService {
 
	@Autowired
	private ManageEventService manageEventService;
    @Autowired
    private RemoteSupportService remoteSupportService;
    @Autowired
    private RemoteUtilityService remoteUtilityService;
    @Autowired
    private PersonDataService personDataService;
    @Autowired
    private PersonProofingStatusService personProofingStatusService;
    @Autowired
    private PhoneVerificationService phoneVerificationService;
    @Autowired
    private ProofingService proofingService;
    @Autowired
    private RefOtpSupplierDataService refOtpSupplierDataService;
    @Autowired
    private RefSponsorDataService refSponsorService;
    @Autowired
    private RpDeviceReputationResponseService rpDeviceReputationResponseService;
    @Autowired
    private RpEventDataService rpEventDataService;
    @Autowired
    private RpPhoneVerificationService rpPhoneVerificationService;

    private final static long serialVersionUID = 1L;
    public final static String CHECK_DEVICE = "checkDevice";
    public final static String CHECK_DEVICE_PLUS = "checkDevicePlus";
    public final static String VERIFY_BUSINESS = "verifyBusiness";
    public final static String VERIFY_PHONE = "verifyPhone";
    public final static String CONFIRM_PASSCODE = "confirmPasscode";
    public final static String REQUEST_PASSCODE = "requestPasscode";
    public final static String VALIDATE_LINK = "validateLink";
    public final static String RESEND_LINK = "resendLink";

    /******** INDIVIDUAL CUSTOMER API PUBLIC METHODS ****************/
    @Override
    public Response checkDevice(RemoteRequest remoteReq, String origin) {
    	remoteReq.setAssessmentCall(RemoteProofingServiceImpl.CHECK_DEVICE);
    	PersonVo personVo = new PersonVo();
        Response response = remoteSupportService.buildRequestValidationResponse(remoteReq, personVo, origin);
           
        if (response != null) {
        	return response;
        }
          
        remoteSupportService.populatePersonVo(remoteReq, personVo);
        remoteSupportService.setSponsorAppData(remoteReq, personVo);
 
        try {
         	Person person = proofingService.updatePerson(personVo);
         	
         	if (person == null) {
         		return remoteSupportService.personNotFoundResponse(remoteReq.getCustomerUniqueID(), origin);
         	}
         	 
            proofingService.startProofingSession(person, personVo);

            CommonAssessmentParamVo assessmentVo = remoteSupportService.mergeParamObjects(remoteReq, personVo);
            response = remoteSupportService.checkDeviceReputation(origin, person, personVo, assessmentVo, remoteReq);
        } catch (Exception e) {
        	response = remoteSupportService.exceptionErrorResponse(personVo, null, "assessing Device Reputation", origin, e);
        }
        
        return response;
    }
    
    @Override
    public Response assessDevicePlusEmailRisk(RemoteRequest remoteReq, String origin) {
    	CustomLogger.enter(this.getClass());
      	remoteReq.setAssessmentCall(RemoteProofingServiceImpl.CHECK_DEVICE_PLUS);

    	PersonVo personVo = new PersonVo();
        Response response = remoteSupportService.buildRequestValidationResponse(remoteReq, personVo, origin);
           
        if (response != null) {
        	return response;
        }
          
        remoteSupportService.populatePersonVo(remoteReq, personVo);
        remoteSupportService.setSponsorAppData(remoteReq, personVo);
 
        try {
         	// This call will create the person and person_proofing_status record if they do not exist.
        	if (personVo.getSponsorUserId() == null) {
        		String addressHash = remoteSupportService.calculateNameAddressHash(remoteReq);
            	personVo.setSponsorUserId(addressHash);
        	}

        	Person person = proofingService.updatePerson(personVo);
            proofingService.startProofingSession(person, personVo);

            CommonAssessmentParamVo assessmentVo = remoteSupportService.mergeParamObjects(remoteReq, personVo);
            
            assessmentVo.setEndpoint(CommonAssessmentParamVo.ASSESS_DEVICE_PLUS_EMAIL_RISK);
            response = remoteSupportService.assessDevicePlusEmailRisk(origin, remoteReq, person, personVo, assessmentVo);
             
            return response;
        } catch (Exception e) {
        	response = remoteSupportService.exceptionErrorResponse(personVo, null, "assessing DevicePlusEmailRisk", origin, e);
        }
        
		return response;
    }
    
    
    @Override
    public Response assessDeviceEmail(RemoteRequest remoteReq, String origin) {
    	CustomLogger.enter(this.getClass());
      	remoteReq.setAssessmentCall(RemoteProofingServiceImpl.CHECK_DEVICE_PLUS);

    	PersonVo personVo = new PersonVo();
        Response response = remoteSupportService.buildRequestValidationResponse(remoteReq, personVo, origin);
           
        if (response != null) {
        	return response;
        }
          
        remoteSupportService.populatePersonVo(remoteReq, personVo);
        remoteSupportService.setSponsorAppData(remoteReq, personVo);
 
        try {
         	// This call will create the person and person_proofing_status record if they do not exist.
        	if (personVo.getSponsorUserId() == null) {
        		String addressHash = remoteSupportService.calculateNameAddressHash(remoteReq);
            	personVo.setSponsorUserId(addressHash);
        	}

        	Person person = proofingService.updatePerson(personVo);
            proofingService.startProofingSession(person, personVo);

            CommonAssessmentParamVo assessmentVo = remoteSupportService.mergeParamObjects(remoteReq, personVo);
            
            assessmentVo.setEndpoint(CommonAssessmentParamVo.ASSESS_DEVICE_EMAIL);
            response = remoteSupportService.assessDevicePlusEmailRisk(origin, remoteReq, person, personVo, assessmentVo);
             
            return response;
        } catch (Exception e) {
        	response = remoteSupportService.exceptionErrorResponse(personVo, null, "assessing DevicePlusEmailRisk", origin, e);
        }
        
		return response;
    }
    
    @Override
    public Response verifyBusiness(RemoteRequest remoteReq, String origin) {
    	CustomLogger.enter(this.getClass());
      	remoteReq.setAssessmentCall(RemoteProofingServiceImpl.VERIFY_BUSINESS);

    	PersonVo personVo = new PersonVo();
        Response response = remoteSupportService.buildRequestValidationResponse(remoteReq, personVo, origin);
           
        if (response != null) {
        	return response;
        }
          
        remoteSupportService.populatePersonVo(remoteReq, personVo);
        remoteSupportService.setSponsorAppData(remoteReq, personVo);
 
        try {
         	// This call will create the person and person_proofing_status record if they do not exist.
        	if (personVo.getSponsorUserId() == null) {
        		String addressHash = remoteSupportService.calculateNameAddressHash(remoteReq);
            	personVo.setSponsorUserId(addressHash);
        	}

        	Person person = proofingService.updatePerson(personVo);
            proofingService.startProofingSession(person, personVo);

            CommonAssessmentParamVo assessmentVo = remoteSupportService.mergeParamObjects(remoteReq, personVo);
            response = remoteSupportService.getVerifyBusinessResponse(origin, remoteReq, person, personVo, assessmentVo);
             
            return response;
        } catch (Exception e) {
        	response = remoteSupportService.exceptionErrorResponse(personVo, null, "assessing DevicePlusEmailRisk", origin, e);
        }
        
		return response;
    }
     
    @Override
    public Response verifyBusinessAddressByEndpoint(RemoteRequest remoteReq, String origin) {
    	CustomLogger.enter(this.getClass());
      	remoteReq.setAssessmentCall(RemoteProofingServiceImpl.VERIFY_BUSINESS);

    	PersonVo personVo = new PersonVo();
        Response response = remoteSupportService.buildRequestValidationResponse(remoteReq, personVo, origin);
           
        if (response != null) {
        	return response;
        }
          
        remoteSupportService.populatePersonVo(remoteReq, personVo);
        remoteSupportService.setSponsorAppData(remoteReq, personVo);
 
        try {
         	// This call will create the person and person_proofing_status record if they do not exist.
        	if (personVo.getSponsorUserId() == null) {
        		String addressHash = remoteSupportService.calculateNameAddressHash(remoteReq);
            	personVo.setSponsorUserId(addressHash);
        	}

        	Person person = proofingService.updatePerson(personVo);
            proofingService.startProofingSession(person, personVo);

            CommonAssessmentParamVo assessmentVo = remoteSupportService.mergeParamObjects(remoteReq, personVo);
            
            assessmentVo.setEndpoint(remoteReq.getEndpoint());
            response = remoteSupportService.getVerifyBusinessAddressResponseByEndpoint(origin, remoteReq, person, personVo, assessmentVo);
             
            return response;
        } catch (Exception e) {
        	response = remoteSupportService.exceptionErrorResponse(personVo, null, "assessing DevicePlusEmailRisk", origin, e);
        }
        
		return response;
    }
     
    @Override
    public Response verifyTestBusiness(RemoteRequest remoteReq, String origin) {
    	remoteReq.setAssessmentCall(RemoteProofingServiceImpl.VERIFY_BUSINESS);
      	String propertyKey = remoteReq.getGenericParam1();
      	String workflow = remoteReq.getGenericParam2();
      	String directCall = remoteReq.getGenericParam3();
      	
      	String propertyValue = "";
       	
      	if (propertyKey != null) {
      		propertyValue = remoteUtilityService.getPropertyValue(propertyKey);
      	} else {
      		return remoteSupportService.buildErrorPVResponse(200, "propertyKey field is missing.", origin);
       	}
      	
      	if (workflow != null) {
      		if ("AMS-BIID".equalsIgnoreCase(workflow) && directCall != null) {
      			directCall = "false";
      		}
      	} else {
      		return remoteSupportService.buildErrorPVResponse(200, "workflow field is missing.", origin);
       	}
 
      	if ("DERA".equalsIgnoreCase(workflow)) {
      		remoteReq.setAssessmentCall(RemoteProofingServiceImpl.CHECK_DEVICE_PLUS);
      	} else {
      		remoteReq.setAssessmentCall(RemoteProofingServiceImpl.VERIFY_BUSINESS);
      	}
      	
     	String[] propertyValueArr = propertyValue.split(",");   	     	
       	
       	remoteReq.setSponsorCode(RefSponsor.SPONSOR_CODE_CR.toLowerCase());
       	remoteReq.setCountry("US");
       
       	if (remoteReq.getCompanyName() == null) {
       		remoteReq.setCompanyName(propertyValueArr[0]);
       	}
       	     	
      	if (remoteReq.getCompanyFEIN() == null) {
       		remoteReq.setCompanyFEIN(propertyValueArr[1]);
      	}     	
      	
      	if (remoteReq.getMobilePhone() == null) {
       		remoteReq.setMobilePhone(propertyValueArr[2]);
      	}
      	
      	if (remoteReq.getFirstName() == null) {
      		remoteReq.setFirstName(propertyValueArr[3]);
      	}
      	
      	if (remoteReq.getLastName() == null) {
      		remoteReq.setLastName(propertyValueArr[4]);
      	}
    	
    	if (remoteReq.getStreetAddress1	() == null) {
    		remoteReq.setStreetAddress1(propertyValueArr[5]);
    	}
       	
       	if (remoteReq.getCity() == null) {
       		remoteReq.setCity(propertyValueArr[6]);
       	}
       	
       	if (remoteReq.getState() == null) {
       		remoteReq.setState(propertyValueArr[7]);
       	}
       	
       	if (remoteReq.getZipCode() == null) {
       		remoteReq.setZipCode(propertyValueArr[8]);
       	}
       	
       	if ("DERA".equalsIgnoreCase(workflow)) {
       		if (remoteReq.getEmailAddress() == null) {
           		remoteReq.setEmailAddress(propertyValueArr[9]);
           	}
       		if (remoteReq.getProfilingSessionID() == null) {
           		remoteReq.setProfilingSessionID("8236EA586816-19c77d19-e778-442c");
           	}
       		if (remoteReq.getWebSessionID() == null) {
           		remoteReq.setWebSessionID("rDDJL5AUy2eTmXjm3L_sb-Z");
           	}
       		if (remoteReq.getTrueIPAddress() == null) {
           		remoteReq.setTrueIPAddress("127.0.0.1");
           	}
       	}

    	PersonVo personVo = new PersonVo();
        Response response = remoteSupportService.buildRequestValidationResponse(remoteReq, personVo, origin);
           
        if (response != null) {
        	return response;
        }
          
        remoteSupportService.populatePersonVo(remoteReq, personVo);
        remoteSupportService.setSponsorAppData(remoteReq, personVo);
 
        try {
         	// This call will create the person and person_proofing_status record if they do not exist.
        	if (personVo.getSponsorUserId() == null) {
        		String addressHash = remoteSupportService.calculateNameAddressHash(remoteReq);
            	personVo.setSponsorUserId(addressHash);
        	}

        	Person person = proofingService.updatePerson(personVo);
            proofingService.startProofingSession(person, personVo);

            CommonAssessmentParamVo assessmentVo = remoteSupportService.mergeParamObjects(remoteReq, personVo);
            response = remoteSupportService.getVerifyTestBusinessResponse(origin, remoteReq, person, personVo, assessmentVo);
             
            return response;
        } catch (Exception e) {
        	response = remoteSupportService.exceptionErrorResponse(personVo, null, "assessing DevicePlusEmailRisk", origin, e);
        }
        
		return response;
    }
   
    @Override
    public Response verifyPhone(RemoteRequest remoteReq, String origin) throws Throwable {
     	remoteReq.setAssessmentCall(RemoteProofingServiceImpl.VERIFY_PHONE);
    	PersonVo personVo = new PersonVo();
        // Validate the data that was passed in determining any missing or incorrect formatted values.
        Response response = remoteSupportService.buildRequestValidationResponse(remoteReq, personVo, origin);
        if (response != null) {
        	return response;
        }
          
        remoteSupportService.populatePersonVo(remoteReq, personVo);
        remoteSupportService.setSponsorAppData(remoteReq, personVo);
 
        JSONObject remoteResponseJSON = null;
       	RefOtpSupplier refOtpSupplier = null;
       	Person person = null;
  
       	try {
         	person = proofingService.updatePerson(personVo);
         	
         	if (person == null) {
         		return remoteSupportService.personNotFoundResponse(remoteReq.getCustomerUniqueID(), origin);            
            }
         	
            proofingService.startProofingSession(person, personVo);

            boolean isIndividualNotFound = rpDeviceReputationResponseService.isIndividualNotFound(person.getPersonId());
            personVo.setLexisNexisIndividualNotFound(isIndividualNotFound);
            
            refOtpSupplier = remoteSupportService.determineVerificationMethod(personVo);

            if (refOtpSupplier == null || refOtpSupplier.getOtpSupplierId() == 0L) {
            	refOtpSupplier = refOtpSupplierDataService.findBySupplierId(RefOtpSupplier.LEXISNEXIS_RDP_OTP_SUPPLIER_ID);
            }
        } catch (Exception e) {
           	return remoteSupportService.exceptionErrorResponse(personVo, RemoteSupportServiceImpl.VERIFY_PHONE, "determining verification method", origin, e);
        }

        RpEvent rpEvent = null;
        try {
          	rpEvent = rpEventDataService.findLatestEventByPersonId(person.getPersonId());
          	boolean createEvent = false;

          	if (rpEvent != null && rpEvent.getRefOtpSupplier() != null) {
          		long supplierId = rpEvent.getRefOtpSupplier().getOtpSupplierId();
   
          		if (supplierId > RefOtpSupplier.UNKNOWN_SUPPLIER_ID) {
          			//When VerifyPhone is called subsequently without calling CheckDevice again,
          			//it will create a new event with Device Reputation decision source as Previous Result.
          			createEvent = true;
          		}         	
          	} else {
          		//For returning users who had not have event created yet with device reputation assessment, 
        		//it will create a new event with Device Reputation decision source as Previous Result.
      			createEvent = true;
          	}
          	
          	if (createEvent) {
          		personVo.setDeviceAssessmentStatus(DeviceReviewStatusEnum.REVIEW.getReviewStatus());
       			personVo.setWorkflowDecision(RefWorkflowApiType.DECISION_REVIEW);
            	personVo.setWorkflowDecisonSource(RefWorkflowApiType.DECISION_SRC_PREV_RESULT);
            	personVo.setWorkflowApiTypeCode(RefWorkflowApiType.API_TYPE_CODE_DA);
            	personVo.setCustomerCategoryName(RefCustomerCategory.CUSTOMER_CATEGORY_NAME_INDIVIDUAL);
            	
            	if ("coa".equalsIgnoreCase(remoteReq.getSponsorCode())) {
            		personVo.setCallingAppName(RefApp.CHANGE_OF_ADDRESS);
            	}
            	else if ("os".equalsIgnoreCase(remoteReq.getSponsorCode())) {
            		personVo.setCallingAppName(RefApp.OPERATION_SANTA);
            	}
         		
      			manageEventService.createEvent(personVo);
      			rpEvent = rpEventDataService.findLatestEventByPersonId(person.getPersonId());
          	} 
          	
          	if (rpEvent == null) {
          		return remoteSupportService.eventNotFoundResponse(refOtpSupplier.getOtpSupplierId(), remoteReq.getCustomerUniqueID(), origin);
            }
        } catch (Exception e) {
           	return remoteSupportService.exceptionErrorResponse(personVo, RemoteSupportServiceImpl.VERIFY_PHONE, "creating event", origin, e);
        }
        
        try {
        	if (rpEvent.getRefOtpSupplier() != null && rpEvent.getRefOtpSupplier().getOtpSupplierId() == RefOtpSupplier.UNKNOWN_SUPPLIER_ID) {
                rpEvent.setRefOtpSupplier(refOtpSupplier);
                rpEventDataService.update(rpEvent);
        	}
        	
            // Perform a phone velocity check; if check fails, return a fail response.
            boolean passPhoneVelocity = remoteSupportService.passPhoneVelocity(person, personVo, refOtpSupplier, RemoteSupportServiceImpl.VERIFY_PHONE);
             
            long phoneVerificationAttemptCount = personVo.getPhoneVerificationAttemptCount();
         	boolean phoneAttemptLimitReached = phoneVerificationAttemptCount >= 3;
         	
             if (!passPhoneVelocity || phoneAttemptLimitReached) {
               	remoteResponseJSON = remoteSupportService.buildRemoteResponseJSON(personVo, refOtpSupplier, rpEvent, RemoteSupportServiceImpl.VERIFY_PHONE, IPSConstants.VELOCITY_TYPE_PHONE , RemoteSupportServiceImpl.REASON_FAILED_PHONE_VELOCITY);
                return remoteSupportService.buildPVResponse(null, remoteResponseJSON, origin);
            }
         } 
  		catch (Exception e) {
        	return remoteSupportService.exceptionErrorResponse(personVo, RemoteSupportServiceImpl.VERIFY_PHONE, "checking phone velocity", origin, e);
        }
              
        PhoneVerificationResponse pvResponse = null;
       
	    // Set DeviceTypeMobile to false for Experian so it won't be directed to silent authentication process.
        //Silent authentication was only implemented in Informed Delivery/Hold Mail applications.
        if (refOtpSupplier.isExperianPhone()) {
            personVo.setDeviceTypeMobile(false);
        }
        
        try {
        	String customerCategoryName = RefCustomerCategory.CUSTOMER_CATEGORY_NAME_INDIVIDUAL;
        	personVo.setCustomerCategoryName(customerCategoryName);
          	
            // Verify the phone number; return a response on a failed verification.
          	pvResponse = phoneVerificationService.verifyPhone(personVo, refOtpSupplier);

          	if (personVo.hasError()) {
           		JSONObject remResponseJSON = remoteSupportService.buildRemoteResponseJSON(personVo, refOtpSupplier, null, RemoteSupportServiceImpl.VERIFY_PHONE, IPSConstants.VELOCITY_TYPE_PHONE , RemoteSupportServiceImpl.REASON_FAILED_SUPPLIER_API_CALL);
  	   	        return remoteSupportService.buildPVResponse(null, remResponseJSON, origin);
          	}
			
         	// pvResponse is null when all available suppliers web service failed.
         	if (pvResponse == null) {
         		JSONObject remResponseJSON = remoteSupportService.buildRemoteResponseJSON(personVo, refOtpSupplier, rpEvent, RemoteSupportServiceImpl.VERIFY_PHONE, IPSConstants.VELOCITY_TYPE_PHONE , RemoteSupportServiceImpl.REASON_FAILED_SUPPLIER_SELECTION);
  	   	        return remoteSupportService.buildPVResponse(null, remResponseJSON, origin);
         	}           	
         } catch (Exception e) {  
        	 return remoteSupportService.exceptionErrorResponse(personVo, null, "verifying phone", origin, e);
        }

        // Get the latest phone verification for the user in order to return the transaction id.
 	   	RpPhoneVerification rpPhoneVerification = rpPhoneVerificationService.findPhoneVerificationByEventId(rpEvent.getEventId());
      	if (rpPhoneVerification != null) {
      		personVo.setMobileNumber(rpPhoneVerification.getMobilePhoneNumber());
         	personVo.setTransactionId(rpPhoneVerification.getTransactionKey());
       } 
      	
   	 	if (rpEvent.getLatestOtpAttempt() != null) {
         	personVo.setTransactionId(rpEvent.getLatestOtpAttempt().getTransactionKey());
        }

     	String pvDecision = pvResponse.getPhoneVerificationDecision();
          
     	if (RpPhoneVerification.DECISION_FAIL.equalsIgnoreCase(pvDecision) 
     			|| RpPhoneVerification.DECISION_DENY.equalsIgnoreCase(pvDecision)) {
     		 return failedVerifyPhoneResponse(remoteReq, person, personVo, refOtpSupplier, rpEvent, origin);
         }        	

         // If phone verification decision is "Approve", Equifax DIT (with all Trust is Y) 
     	 // or Experian (with successful Silent Authentication) was used as phone verification method.
         // The verification process stops here and the user has achieved the LOA 1.5. 
         if (RpPhoneVerification.DECISION_APPROVE.equalsIgnoreCase(pvDecision) || RpPhoneVerification.DECISION_PASS.equalsIgnoreCase(pvDecision)) {
        	 remoteResponseJSON = remoteSupportService.buildRemoteResponseJSON(personVo, refOtpSupplier, rpEvent, RemoteSupportServiceImpl.VERIFY_PHONE, IPSConstants.VELOCITY_TYPE_PHONE , RemoteSupportServiceImpl.REASON_APPROVED_PHONE_VERIFICATION);
 	   	     return remoteSupportService.buildPVResponse(null, remoteResponseJSON, origin);
         } 
         
         // If phone verification decision is "Review", Experian (with OTPFlow) may be was used as phone verification method.
         // The verification process sends the OTP right after the phone was verified. 
         if (RpPhoneVerification.DECISION_REVIEW.equalsIgnoreCase(pvDecision) 
        		 && refOtpSupplier.isExperianPhone()) {
       
             remoteResponseJSON = remoteSupportService.buildRemoteResponseJSON(personVo, refOtpSupplier, rpEvent, RemoteSupportServiceImpl.VERIFY_PHONE, 
   	       			IPSConstants.VELOCITY_TYPE_PASSCODE , RemoteSupportServiceImpl.REASON_SUCCESSS_OTP_DELIVERY);
    	     return remoteSupportService.buildPVResponse(null, remoteResponseJSON, origin);
         } 
        
         // If phone verification decision is "Pass" or "Review", the verification process continues.
      	String velocityType = refOtpSupplier.isEquifaxDITPhone() ? IPSConstants.VELOCITY_TYPE_SMFA : IPSConstants.VELOCITY_TYPE_PASSCODE;
      	personVo.setAttemptType(RemoteSupportServiceImpl.ATTEMPT_TYPE_REQUEST);
        
      	try {
             // Check if number of passcode or link attempts is exceeded; if exceeded, return a fail response.
        		boolean passOtpVelocity = remoteSupportService.passVelocityCheck(velocityType, person, personVo, refOtpSupplier, rpEvent);
        		if (!passOtpVelocity) {
          			String reason =  refOtpSupplier.isEquifaxDITPhone() ?  RemoteSupportServiceImpl.REASON_FAILED_SMFA_VELOCITY :  RemoteSupportServiceImpl.REASON_FAILED_OTP_VELOCITY;
        			remoteResponseJSON = remoteSupportService.buildRemoteResponseJSON(personVo, refOtpSupplier, rpEvent, RemoteSupportServiceImpl.VERIFY_PHONE, velocityType , reason);
 	   	       		return remoteSupportService.buildPVResponse(null, remoteResponseJSON, origin);
        		}
         } catch (Exception e) {
            	return remoteSupportService.exceptionErrorResponse(personVo, RemoteSupportServiceImpl.VERIFY_PHONE, "checkOTPLinkVelocity", origin, e);
         }
         
         try {
             if (refOtpSupplier.isEquifaxDITPhone()) {
              	// isDesktop value is set to true as default. 
             	// For web service call, this flag is not evaluated as it is being done at the caller's side.
                boolean isDesktop = true; 
              	int isLinkSuccessfullySent = phoneVerificationService.sendSmfaLink(personVo, refOtpSupplier, isDesktop);

              	// values from sendSMFALink were changed from true/false (boolean) to:
              	// 0 success
              	// 200 unsuccessful and land line
              	// 422 unsuccessful and land line
              	// 500 error 'display unable to verify message'
              	// 401 error 'display unable to verify message'                
                 if (isLinkSuccessfullySent != 0) {
                     CustomLogger.info(this.getClass(), "SMFA Link failed to send for customer " + personVo.getSponsorUserId());

                     remoteResponseJSON = remoteSupportService.buildRemoteResponseJSON(personVo, refOtpSupplier, rpEvent, RemoteSupportServiceImpl.VERIFY_PHONE, velocityType , RemoteSupportServiceImpl.REASON_FAILED_SMFA_DELIVERY);
     	   	       	 return remoteSupportService.buildPVResponse(null, remoteResponseJSON, origin);
                 }           
             }
             else {
             	 phoneVerificationService.sendPasscodeSuccessful(personVo, refOtpSupplier);

                 // Get the most up to date person record so otp sent comparison can be accurate
                 person = personDataService.findByPK(personVo.getId());

                 // Get proofing status to determine if passcode was sent
                 int personId = (int) person.getPersonId();
                 PersonProofingStatus latestProofingStatus = personProofingStatusService.getByPersonId(personId);
                 boolean otpSent = latestProofingStatus != null && latestProofingStatus.getRefRpStatus().getStatusCode() == RefRpStatus.RpStatus.OTP_sent.getValue();

                 if (!otpSent) {
                     CustomLogger.info(this.getClass(), "Passcode failed to send for customer " + personVo.getSponsorUserId());

                     if (rpPhoneVerification != null) {
 	                    String transactionKey = rpPhoneVerification.getTransactionKey();
 	                    
 	                    if (transactionKey != null) {
 	                    	personVo.setTransactionId(transactionKey);
 	                    }
                     }

                     remoteResponseJSON = remoteSupportService.buildRemoteResponseJSON(personVo, refOtpSupplier, rpEvent, RemoteSupportServiceImpl.VERIFY_PHONE, velocityType , RemoteSupportServiceImpl.REASON_FAILED_OTP_DELIVERY);
     	   	       	 return remoteSupportService.buildPVResponse(null, remoteResponseJSON, origin);
                  }
              }    
         } catch (Exception e) {
           	if (personVo.isOtpSmsLandline()) {
          		remoteResponseJSON = remoteSupportService.buildRemoteResponseJSON(personVo, refOtpSupplier, rpEvent, RemoteSupportServiceImpl.VERIFY_PHONE, IPSConstants.VELOCITY_TYPE_PHONE , RemoteSupportServiceImpl.REASON_FAILED_PHONE_VERIFICATION);
      	   	    return remoteSupportService.buildPVResponse(null, remoteResponseJSON, origin);
         	}
          	else {
          		return remoteSupportService.exceptionErrorResponse(personVo, RemoteSupportServiceImpl.VERIFY_PHONE, "checking Passcode or SMFA Link velocity", origin, e);
          	}
         }
         
         try {
              if (rpEvent != null && rpEvent.getLatestOtpAttempt() != null) {
              	personVo.setTransactionId(rpEvent.getLatestOtpAttempt().getTransactionKey());
             }
             
             String reason = refOtpSupplier.isEquifaxDITPhone() ? RemoteSupportServiceImpl.REASON_SUCCESSSFUL_SMFA_DELIVERY : RemoteSupportServiceImpl.REASON_SUCCESSS_OTP_DELIVERY;
             remoteResponseJSON = remoteSupportService.buildRemoteResponseJSON(personVo, refOtpSupplier, rpEvent, RemoteSupportServiceImpl.VERIFY_PHONE, velocityType , reason);
    	     
             return remoteSupportService.buildPVResponse(null, remoteResponseJSON, origin);
         } catch (Exception e) {
         	 return remoteSupportService.exceptionErrorResponse(personVo, RemoteSupportServiceImpl.VERIFY_PHONE, "sending passcode or SMFA Link to phone", origin, e);
         }
    }
    
    public Response failedVerifyPhoneResponse(RemoteRequest remoteReq, Person person, PersonVo personVo, RefOtpSupplier refOtpSupplier, RpEvent rpEvent, String origin) throws Throwable {
    	try {
		    String message = String.format(RemoteSupportServiceImpl.PV_FAILED_MSG_FMT, personVo.getSponsorUserId());
		    CustomLogger.info(this.getClass(), message);
		    proofingService.updateProofingStatus(RefRpStatus.RpStatus.Phone_verification_failed.getValue(),
						person, RefLoaLevel.LOA_15);

	       	if (personVo.getMobileNumber() == null && person.getPersonData() != null) {
	    		 personVo.setMobileNumber(person.getPersonData().getPhoneINT());
	    	}

		    JSONObject remoteResponseJSON = remoteSupportService.buildRemoteResponseJSON(personVo, refOtpSupplier, rpEvent, RemoteSupportServiceImpl.VERIFY_PHONE, IPSConstants.VELOCITY_TYPE_PHONE , RemoteSupportServiceImpl.REASON_FAILED_PHONE_VERIFICATION);
		    return remoteSupportService.buildPVResponse(null, remoteResponseJSON, origin);
    	} catch (Exception e) {
         	return remoteSupportService.exceptionErrorResponse(personVo, RemoteSupportServiceImpl.VERIFY_PHONE, "sending passcode or SMFA Link to phone", origin, e);
        }
    }

    @Override
    public Response confirmPasscode(RemoteRequest remoteReq, String origin) throws Throwable {
     	CustomLogger.enter(this.getClass());
    	remoteReq.setAssessmentCall(RemoteProofingServiceImpl.CONFIRM_PASSCODE);
    	PersonVo personVo = new PersonVo();
    	
        // Validate the data that was passed in determining any missing or incorrect formatted values.
        Response response = remoteSupportService.buildRequestValidationResponse(remoteReq, personVo, origin);
           
        if (response != null) {
        	return response;
        }
          
        remoteSupportService.populatePersonVo(remoteReq, personVo);
        remoteSupportService.setSponsorAppData(remoteReq, personVo);

        RemoteResponse pvResponse = new RemoteResponse();
        JSONObject remoteResponseJSON = null;
    	Person person =  null;
        long sponsorId = personVo.getSponsorId();
        String sponsorUserId = personVo.getSponsorUserId();

        try {
             // Check if web service calls for sponsorID are disabled.
             RefSponsor sponsor = refSponsorService.findByPK(sponsorId);
             person = personDataService.findFirstBySponsor(sponsor, sponsorUserId);
             
             if (person == null) {
            	 person = proofingService.updatePerson(personVo);
             }

             if (person == null) {
          		return remoteSupportService.personNotFoundResponse(remoteReq.getCustomerUniqueID(), origin);            
             }
            
           	 long personId = person.getPersonId();

             personVo.setId(personId);
             personVo.setSponsorId(person.getRefSponsor().getSponsorId());
 
             long otpSupplierId = RefOtpSupplier.LEXISNEXIS_RDP_OTP_SUPPLIER_ID;
             RefOtpSupplier refOtpSupplier = refOtpSupplierDataService.findBySupplierId(otpSupplierId); 

             RpEvent rpEvent = rpEventDataService.findLatestEventByPersonId(personId);
             
             if (rpEvent == null) {
          		return remoteSupportService.eventNotFoundResponse(otpSupplierId, remoteReq.getCustomerUniqueID(), origin);
             }

             /*
             if (rpEvent.getRefOtpSupplier() != null) {
            	 if (rpEvent.getRefOtpSupplier().getOtpSupplierId() == RefOtpSupplier.UNKNOWN_SUPPLIER_ID) {
            		 return failedVerifyPhoneResponse(remoteReq, person, personVo, refOtpSupplier, rpEvent, origin);
             	 }
            	 else if (rpEvent.getRefOtpSupplier().getOtpSupplierId() != otpSupplierId) {
            	 	return remoteSupportService.invalidSupplierResponse(rpEvent.getRefOtpSupplier().getOtpSupplierId(), remoteReq.getCustomerUniqueID(), "Passcode confirmation", origin); 
            	 }
             }
             */
             
      	   	 RpPhoneVerification rpPhoneVerification = rpPhoneVerificationService.findPhoneVerificationByEventId(rpEvent.getEventId());
             if (rpPhoneVerification != null) {
             	personVo.setMobileNumber(rpPhoneVerification.getMobilePhoneNumber());
             }

             personVo.setTransactionKeyHasExpired(rpEvent.transactionKeyHasExpired());
             
             //For future implementation of COA Integration with Experian
             /*
             if (refOtpSupplier.isExperianPhone()) {
            	 remoteSupportService.populatePersonVoFromPerson(person, personVo);
             }
             */
             
             // If phone is not verified on file, return fail response.
             String reason = remoteSupportService.checkPhoneVerified(person, personVo, rpEvent, refOtpSupplier, RemoteSupportServiceImpl.CONFIRM_PASSCODE, origin);
             
             if (reason != null) {    
	             if (RemoteSupportServiceImpl.REASON_FAILED_OTP_VELOCITY.equalsIgnoreCase(reason)) {
	                 remoteResponseJSON = remoteSupportService.buildRemoteResponseJSON(personVo, refOtpSupplier, rpEvent, RemoteSupportServiceImpl.CONFIRM_PASSCODE, IPSConstants.VELOCITY_TYPE_PASSCODE , reason);
	    	   	     return remoteSupportService.buildPVResponse(null, remoteResponseJSON, origin);
	             }
	             /*
	             else if (RemoteSupportServiceImpl.REASON_FAILED_PHONE_VERIFICATION.equalsIgnoreCase(reason)) {	            	 
	            	 if (personVo.getMobileNumber() == null && person.getPersonData() != null) {
	            		 personVo.setMobileNumber(person.getPersonData().getPhoneINT());
	            	 }
	                 remoteResponseJSON = remoteSupportService.buildRemoteResponseJSON(personVo, refOtpSupplier, rpEvent, RemoteSupportServiceImpl.CONFIRM_PASSCODE, IPSConstants.VELOCITY_TYPE_PASSCODE , reason);
	    	   	     return remoteSupportService.buildPVResponse(null, remoteResponseJSON, origin);
	             }
	             */
	             else if (RemoteSupportServiceImpl.REASON_APPROVED_PHONE_VERIFICATION.equalsIgnoreCase(reason)) {
	            	 remoteResponseJSON = remoteSupportService.buildRemoteResponseJSON(personVo, refOtpSupplier, rpEvent, RemoteSupportServiceImpl.CONFIRM_PASSCODE, IPSConstants.VELOCITY_TYPE_PASSCODE , reason);
	 	   	       	 return remoteSupportService.buildPVResponse(null, remoteResponseJSON, origin);
	             }  
	         }

	     if (rpEvent.getLatestOtpAttempt() != null) {
            	 pvResponse.setTransactionID(rpEvent.getLatestOtpAttempt().getTransactionKey());
             }

             // Set the ID for PersonVo to Person ID.
             // Must do this so confirmPasscode call can find latest phone verification.
             personVo.setId(person.getPersonId());
             personVo.setSponsorId(person.getRefSponsor().getSponsorId());

             // Check if passcode sent by user matches with what was sent to the phone.
             // Return a fail response if passcode does not match.
             reason = remoteSupportService.passcodeConfirmation(origin, personVo, refOtpSupplier);

             // PassVelocityCheck has already passed at this point. 
             // This is just to get the number of passcode attempts.
             personVo.setAttemptType(RemoteSupportServiceImpl.ATTEMPT_TYPE_CONFIRM);
             remoteSupportService.passVelocityCheck(IPSConstants.VELOCITY_TYPE_PASSCODE, person, personVo, refOtpSupplier, rpEvent);

             if (reason != null) {
            	if (RemoteSupportServiceImpl.REASON_FAILED_OTP_CONFIRMATION_MISMATCH.equalsIgnoreCase(reason)) {
   	                 remoteResponseJSON = remoteSupportService.buildRemoteResponseJSON(personVo, refOtpSupplier, rpEvent, RemoteSupportServiceImpl.CONFIRM_PASSCODE, IPSConstants.VELOCITY_TYPE_PASSCODE , reason);
    	   	       	 return remoteSupportService.buildPVResponse(null, remoteResponseJSON, origin);
                }
            	else {
              		reason = RemoteSupportServiceImpl.REASON_FAILED_OTP_CONFIRMATION_OTHER;
            		remoteResponseJSON = remoteSupportService.buildRemoteResponseJSON(personVo, refOtpSupplier, rpEvent, RemoteSupportServiceImpl.CONFIRM_PASSCODE, IPSConstants.VELOCITY_TYPE_PASSCODE , reason);
   	   	       	 	return remoteSupportService.buildPVResponse(null, remoteResponseJSON, origin);
            	}
             }
            	
             remoteResponseJSON = remoteSupportService.buildRemoteResponseJSON(personVo, refOtpSupplier, rpEvent, RemoteSupportServiceImpl.CONFIRM_PASSCODE, IPSConstants.VELOCITY_TYPE_PASSCODE , RemoteSupportServiceImpl.REASON_PASSED_OTP_CONFIRMATION);
             return remoteSupportService.buildPVResponse(null, remoteResponseJSON, origin);
             
          } catch (Exception e) {
         	 return remoteSupportService.exceptionErrorResponse(personVo, RemoteSupportServiceImpl.CONFIRM_PASSCODE, "confirming passcode", origin, e);
          }
    }
     
 	@SuppressWarnings("unused")
	@Override
    public Response requestPasscode(RemoteRequest remoteReq, String origin) throws Throwable {
    	CustomLogger.enter(this.getClass());
    	
    	remoteReq.setAssessmentCall(RemoteProofingServiceImpl.REQUEST_PASSCODE);
    	PersonVo personVo = new PersonVo();
		
		 // Validate the data that was passed in determining any missing or incorrect formatted values.
        Response response = remoteSupportService.buildRequestValidationResponse(remoteReq, personVo, origin);
           
        if (response != null) {
        	return response;
        }
          
        remoteSupportService.populatePersonVo(remoteReq, personVo);
        remoteSupportService.setSponsorAppData(remoteReq, personVo);
  
        JSONObject remoteResponseJSON = new JSONObject();
        long sponsorId = personVo.getSponsorId();
        String requestId = getRequestId(personVo);
        String sponsorUserId = personVo.getSponsorUserId();
    	String requestStr = new Gson().toJson(remoteReq);

        // Check if web service calls for sponsorID are disabled.
        RefSponsor sponsor = refSponsorService.findByPK(sponsorId);
 
        Person person = personDataService.findFirstBySponsor(sponsor, sponsorUserId);
          
        if (person == null) {
       	 	person = proofingService.updatePerson(personVo);
       	 	
       	 	if (person == null) {
       	 		return remoteSupportService.personNotFoundResponse(remoteReq.getCustomerUniqueID(), origin);            
       	 	}
        }
       
      	long personId = person.getPersonId();

        personVo.setId(personId);
        personVo.setSponsorId(person.getRefSponsor().getSponsorId());

        long otpSupplierId = RefOtpSupplier.LEXISNEXIS_RDP_OTP_SUPPLIER_ID;
        RefOtpSupplier refOtpSupplier = refOtpSupplierDataService.findBySupplierId(otpSupplierId); 
 
        RpEvent rpEvent = rpEventDataService.findLatestEventByPersonId(personId);

        if (rpEvent == null) {
     		return remoteSupportService.eventNotFoundResponse(otpSupplierId, remoteReq.getCustomerUniqueID(), origin);
        }

        personVo.setId(personId);
        personVo.setSponsorId(person.getRefSponsor().getSponsorId());
   
        /*
        if (rpEvent.getRefOtpSupplier() != null) {
       	 	if (rpEvent.getRefOtpSupplier().getOtpSupplierId() == RefOtpSupplier.UNKNOWN_SUPPLIER_ID) {
       		 	return failedVerifyPhoneResponse(remoteReq, person, personVo, refOtpSupplier, rpEvent, origin);
        	}
       	 	else if (rpEvent.getRefOtpSupplier().getOtpSupplierId() != otpSupplierId) {
       	 		return remoteSupportService.invalidSupplierResponse(rpEvent.getRefOtpSupplier().getOtpSupplierId(), remoteReq.getCustomerUniqueID(), "Passcode delivery", origin); 
       	 	}
        }
        */

        // Get proofing status to determine if passcode was already confirmed and user has achieved LOA level.
        List<PersonProofingStatus> proofingStatus = person.getProofingStatuses();
        boolean loaAchieved = !proofingStatus.isEmpty() && proofingStatus.get(0).getRefRpStatus()
                .getStatusCode() == RefRpStatus.RpStatus.LOA_level_achieved.getValue();

        if (loaAchieved) {
       	 	String logMsg = "Previous passcode was confirmed and LOA level already achieved. New passcode could not be sent to customer " + personVo.getSponsorUserId();
            CustomLogger.info(this.getClass(), logMsg);

            remoteResponseJSON.put("responseMessage", logMsg);
        	return remoteSupportService.buildPVResponse(null, remoteResponseJSON, origin);
        }
        personVo.setPhoneVerificationSupplierName(refOtpSupplier.getOtpSupplierName());
      
        // Get verified phone number on file.
 	   	RpPhoneVerification rpPhoneVerification = rpPhoneVerificationService.findPhoneVerificationByEventId(rpEvent.getEventId());
        if (rpPhoneVerification != null) {
        	personVo.setMobileNumber(rpPhoneVerification.getMobilePhoneNumber());
        }

        try {
         	// When phone is not verified on file, return fail response.
            String reason = remoteSupportService.checkPhoneVerified(person, personVo, rpEvent, refOtpSupplier, RemoteSupportServiceImpl.REQUEST_PASSCODE, origin);

            if (reason != null) {
   	            if (RemoteSupportServiceImpl.REASON_FAILED_OTP_VELOCITY.equalsIgnoreCase(reason)) {
	                 remoteResponseJSON = remoteSupportService.buildRemoteResponseJSON(personVo, refOtpSupplier, rpEvent, RemoteSupportServiceImpl.CONFIRM_PASSCODE, IPSConstants.VELOCITY_TYPE_PASSCODE , reason);
   	    	   	     return remoteSupportService.buildPVResponse(null, remoteResponseJSON, origin);
   	             }
   	            /*
   	             else if (RemoteSupportServiceImpl.REASON_FAILED_PHONE_VERIFICATION.equalsIgnoreCase(reason)) {
	            	 if (personVo.getMobileNumber() == null && person.getPersonData() != null) {
	            		 personVo.setMobileNumber(person.getPersonData().getPhoneINT());
	            	 }
 	                 remoteResponseJSON = remoteSupportService.buildRemoteResponseJSON(personVo, refOtpSupplier, rpEvent, RemoteSupportServiceImpl.CONFIRM_PASSCODE, IPSConstants.VELOCITY_TYPE_PASSCODE , reason);
   	    	   	     return remoteSupportService.buildPVResponse(null, remoteResponseJSON, origin);
   	             }
   	             */
   	             else if (RemoteSupportServiceImpl.REASON_APPROVED_PHONE_VERIFICATION.equalsIgnoreCase(reason)) {
  	                remoteResponseJSON = remoteSupportService.buildRemoteResponseJSON(personVo, refOtpSupplier, rpEvent, RemoteSupportServiceImpl.CONFIRM_PASSCODE, IPSConstants.VELOCITY_TYPE_PASSCODE , reason);
   	 	   	       	return remoteSupportService.buildPVResponse(null, remoteResponseJSON, origin);
   	             }
            }

            reason = remoteSupportService.sendPasscodeToPhone(personVo, refOtpSupplier, rpEvent);
             
            if (reason != null) {
        	   remoteResponseJSON = remoteSupportService.buildRemoteResponseJSON(personVo, refOtpSupplier, rpEvent, RemoteSupportServiceImpl.REQUEST_PASSCODE, IPSConstants.VELOCITY_TYPE_PASSCODE , RemoteSupportServiceImpl.REASON_FAILED_OTP_DELIVERY);
	   	       return remoteSupportService.buildPVResponse(null, remoteResponseJSON, origin);
            }
 
            remoteResponseJSON = remoteSupportService.buildRemoteResponseJSON(personVo, refOtpSupplier, rpEvent, RemoteSupportServiceImpl.REQUEST_PASSCODE, IPSConstants.VELOCITY_TYPE_PASSCODE , RemoteSupportServiceImpl.REASON_SUCCESSS_OTP_DELIVERY);
  	       	return remoteSupportService.buildPVResponse(null, remoteResponseJSON, origin);      	       	
        } catch (Exception e) {
        	remoteResponseJSON = remoteSupportService.buildRemoteResponseJSON(personVo, refOtpSupplier, rpEvent, RemoteSupportServiceImpl.REQUEST_PASSCODE, IPSConstants.VELOCITY_TYPE_PASSCODE , RemoteSupportServiceImpl.REASON_FAILED_OTP_DELIVERY);
	   	    return remoteSupportService.buildPVResponse(null, remoteResponseJSON, origin);
        }
    }
 	
 	 @Override
     public Response validateLink(RemoteRequest remoteReq, String origin) throws Throwable {
     	remoteReq.setAssessmentCall(RemoteProofingServiceImpl.VALIDATE_LINK);
     	PersonVo personVo = new PersonVo();
     	JSONObject remoteResponseJSON = null;
		
        // Validate the data that was passed in determining any missing or incorrect formatted values.
        Response response = remoteSupportService.buildRequestValidationResponse(remoteReq, personVo, origin);
            
        if (response != null) {
         	return response;
        }
           
        remoteSupportService.populatePersonVo(remoteReq, personVo);
        remoteSupportService.setSponsorAppData(remoteReq, personVo);
 
        RemoteResponse pvResponse = new RemoteResponse();
        long sponsorId = personVo.getSponsorId();
        String sponsorUserId = personVo.getSponsorUserId();
      	Person person =  null;

        try {
             // Check if web service calls for sponsorID are disabled.
             RefSponsor sponsor = refSponsorService.findByPK(sponsorId);
             person = personDataService.findFirstBySponsor(sponsor, sponsorUserId);
              
             if (person == null) {
            	 person = proofingService.updatePerson(personVo);
            	 
            	 if (person == null) {
              		 return remoteSupportService.personNotFoundResponse(remoteReq.getCustomerUniqueID(), origin);            
                 }
             }

           	 long personId = person.getPersonId();

             personVo.setId(personId);
             personVo.setSponsorId(person.getRefSponsor().getSponsorId());

             RpEvent rpEvent = rpEventDataService.findLatestEventByPersonId(personId);

             long otpSupplierId = RefOtpSupplier.EQUIFAX_DIT_SMFA_SUPPLIER_ID;
             RefOtpSupplier refOtpSupplier = refOtpSupplierDataService.findBySupplierId(otpSupplierId); 

             if (rpEvent == null) {
          		return remoteSupportService.eventNotFoundResponse(otpSupplierId, remoteReq.getCustomerUniqueID(), origin);
             }
 
             /*
             if (rpEvent.getRefOtpSupplier() != null) {
            	 if (rpEvent.getRefOtpSupplier().getOtpSupplierId() == RefOtpSupplier.UNKNOWN_SUPPLIER_ID) {
            		 	return failedVerifyPhoneResponse(remoteReq, person, personVo, refOtpSupplier, rpEvent, origin);
             	 }
        	 	 else if (rpEvent.getRefOtpSupplier().getOtpSupplierId() != otpSupplierId) {
        	 		return remoteSupportService.invalidSupplierResponse(rpEvent.getRefOtpSupplier().getOtpSupplierId(), remoteReq.getCustomerUniqueID(), "SMFA Link validation", origin); 
        	 	 }
             }
             */
                         
             // Get verified phone number on file.
      	   	 RpPhoneVerification rpPhoneVerification = rpPhoneVerificationService.findPhoneVerificationByEventId(rpEvent.getEventId());
             if (rpPhoneVerification != null) {
             	personVo.setMobileNumber(rpPhoneVerification.getMobilePhoneNumber());
             }
             
             personVo.setPasscodeExpired(rpEvent.lastPasscodeAttemptExpired() || rpEvent.transactionKeyHasExpired());

             if(rpEvent.getLatestSmfaAttempt() != null) {
             	pvResponse.setSessionID(rpEvent.getLatestSmfaAttempt().getSessionId());
             }
             
             // Set the ID for PersonVo to Person ID.
             // Must do this so validateLink call can find latest phone verification.
             personVo.setId(person.getPersonId());
             personVo.setSponsorId(person.getRefSponsor().getSponsorId());

             // First check if the link attempt exceeded.
             boolean lastPasscodeAttemptExceededSubmit = rpEvent.lastPasscodeAttemptExceededSubmit();
             // Check if user tapped the link sent to the user's phone.
             // If so, check what color was returned from the Equifax SMFA validation.
             String reason = remoteSupportService.checkSmfaLinkValidation(personVo);
             
             if (!lastPasscodeAttemptExceededSubmit && reason != null) {  
            	 reason = RemoteSupportServiceImpl.REASON_FAILED_SMFA_VALIDATION;
                 remoteResponseJSON = remoteSupportService.buildRemoteResponseJSON(personVo, refOtpSupplier, rpEvent, RemoteSupportServiceImpl.VALIDATE_LINK, IPSConstants.VELOCITY_TYPE_SMFA , reason);
	 	   	      return remoteSupportService.buildPVResponse(null, remoteResponseJSON, origin);
             }
                           
             CustomLogger.info(this.getClass(), pvResponse.getResponseMessage());

             remoteResponseJSON = remoteSupportService.buildRemoteResponseJSON(personVo, refOtpSupplier, rpEvent, RemoteSupportServiceImpl.VALIDATE_LINK, IPSConstants.VELOCITY_TYPE_SMFA , RemoteSupportServiceImpl.REASON_PASSED_SMFA_VALIDATION);
 	   	     return remoteSupportService.buildPVResponse(null, remoteResponseJSON, origin);
         } catch (Exception e) {
           	return remoteSupportService.exceptionErrorResponse(personVo, null, "validating SMFA Link", origin, e);
         }
    }
 	
 	@SuppressWarnings("unused")
	@Override
    public Response resendLink(RemoteRequest remoteReq, String origin) throws Throwable {
     	JSONObject remoteResponseJSON = null;
    	remoteReq.setAssessmentCall(RemoteProofingServiceImpl.RESEND_LINK);
    	PersonVo personVo = new PersonVo();
		
        // Validate the data that was passed in determining any missing or incorrect formatted values.
        Response response = remoteSupportService.buildRequestValidationResponse(remoteReq, personVo, origin);
           
        if (response != null) {
        	return response;
        }
          
        remoteSupportService.populatePersonVo(remoteReq, personVo);
        remoteSupportService.setSponsorAppData(remoteReq, personVo);
        
        JSONObject pvResponseJSON = new JSONObject();
        long sponsorId = personVo.getSponsorId();
        String sponsorUserId = personVo.getSponsorUserId();
        String requestId = getRequestId(personVo);  	        
         
        try {
	        // Check if web service calls for sponsorID are disabled.
	        RefSponsor sponsor = refSponsorService.findByPK(sponsorId);       
	        Person person = personDataService.findFirstBySponsor(sponsor, sponsorUserId);
	        
	        if (person == null) {
	       	 	person = proofingService.updatePerson(personVo);
	       	 	
		      	if (person == null) {
	         		 return remoteSupportService.personNotFoundResponse(remoteReq.getCustomerUniqueID(), origin);            
		        }
	        }

	      	long personId = person.getPersonId();
	
	        personVo.setId(personId);
	        personVo.setSponsorId(person.getRefSponsor().getSponsorId());
	
	        long otpSupplierId = RefOtpSupplier.EQUIFAX_DIT_SMFA_SUPPLIER_ID;
	        RefOtpSupplier refOtpSupplier = refOtpSupplierDataService.findBySupplierId(otpSupplierId); 

	        RpEvent rpEvent = rpEventDataService.findLatestEventByPersonId(personId);
        
	        if (rpEvent == null) {
          		return remoteSupportService.eventNotFoundResponse(otpSupplierId, remoteReq.getCustomerUniqueID(), origin);
	        }
	
	        personVo.setId(personId);
	        personVo.setSponsorId(person.getRefSponsor().getSponsorId());
	 
	        // Get proofing status to determine if SMFA link was already validated successfully and user has achieved LOA level.
	        List<PersonProofingStatus> proofingStatus = person.getProofingStatuses();
	        boolean loaAchieved = !proofingStatus.isEmpty() && proofingStatus.get(0).getRefRpStatus()
	                .getStatusCode() == RefRpStatus.RpStatus.LOA_level_achieved.getValue();

	        if (loaAchieved) {
	       	 	String logMsg = "Previous authentication link was successfully validated and LOA level already achieved. New authentication link could not be sent to customer " + personVo.getSponsorUserId();
	            CustomLogger.info(this.getClass(), logMsg);
	
	            pvResponseJSON.put("responseMessage", logMsg);
	        	return remoteSupportService.buildPVResponse(null, pvResponseJSON, origin);
	        }
 
            personVo.setPhoneVerificationSupplierName(refOtpSupplier.getOtpSupplierName());
         
            // Get verified phone number on file.
     	   	RpPhoneVerification rpPhoneVerification = rpPhoneVerificationService.findPhoneVerificationByEventId(rpEvent.getEventId());
            if (rpPhoneVerification != null) {
            	personVo.setMobileNumber(rpPhoneVerification.getMobilePhoneNumber());
            }
            
         	// isDesktop value is set to true as default. 
        	// For web service call, this flag is not evaluated as it is being done at the caller's side.
           	boolean isDesktop = true; 
         	int isLinkSuccessfullySent = phoneVerificationService.sendSmfaLink(personVo, refOtpSupplier, isDesktop);

         	// Values from sendSMFALink were changed from true/false (boolean) to:
         	// 0 success
         	// 200 unsuccessful and land line
         	// 422 unsuccessful and land line
         	// 500 error 'display unable to verify message'
         	// 401 error 'display unable to verify message'                
            if (isLinkSuccessfullySent != 0) {
            	String errorMsg = "SMFA Link failed to send for customer " + personVo.getSponsorUserId();
                CustomLogger.info(this.getClass(), errorMsg);

                if (rpPhoneVerification != null) {
                	personVo.setTransactionId(rpPhoneVerification.getTransactionKey());
                }
                
                remoteResponseJSON = remoteSupportService.buildRemoteResponseJSON(personVo, refOtpSupplier, rpEvent, RemoteSupportServiceImpl.RESEND_LINK, IPSConstants.VELOCITY_TYPE_SMFA , RemoteSupportServiceImpl.REASON_FAILED_SMFA_DELIVERY);
	   	       	
                return remoteSupportService.buildPVResponse(null, remoteResponseJSON, origin);
            }         
           
            pvResponseJSON = remoteSupportService.buildRemoteResponseJSON(personVo, refOtpSupplier, rpEvent, RemoteSupportServiceImpl.RESEND_LINK, IPSConstants.VELOCITY_TYPE_SMFA, RemoteSupportServiceImpl.REASON_SUCCESSSFUL_SMFA_DELIVERY);
            return remoteSupportService.buildPVResponse(null, pvResponseJSON, origin);
        } catch (Exception e) {
         	return remoteSupportService.exceptionErrorResponse(personVo, RemoteSupportServiceImpl.RESEND_LINK, "resending SMFA Link", origin, e);
        }
    }

 	private String getRequestId(PersonVo personVo) {
        String requestId = personVo.getRequestId();
        
        if (requestId == null) {
        	String sponsorUserId = personVo.getSponsorUserId();
  
      		requestId = sponsorUserId != null? String.format("%s - %s", sponsorUserId, DateTimeUtil.getCurrentDateTime())
      			: String.format("DT%s",  DateTimeUtil.getCurrentDateTime());
         }
         
        personVo.setRequestId(requestId);
        return requestId;
	}
}
