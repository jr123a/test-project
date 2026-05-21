# RemoteRest + RemoteServices — Complete Architecture Analysis
**Sources:** RemoteRest.zip, RemoteServices.zip  
**Analysis date:** May 2026

---

## A. Executive Summary

**RemoteRest** is a WAR-packaged JAX-RS web application deployed on IBM WebSphere. It has essentially **zero business logic of its own** — it is a thin HTTP adapter containing only two Java classes: the JAX-RS application registration class and a single REST resource class that immediately delegates to services. All actual business logic lives in the **RemoteServices** module, which is a JAR library bundled into RemoteRest's WAR.

**RemoteServices** is the B2B (business-to-business) API layer of the USPS Identity Verification System. It exposes a REST API that external USPS applications call **without requiring a CustReg SSO session**. This is what differentiates it from IPSWeb — it is a stateless, session-free REST API consumed by backend services like:
- **BCG (Business Customer Gateway)** — via the `AssessDevicePlusEmailRisk` endpoint
- **Operation Santa** / **Change of Address** — via `CheckDevice`
- Any calling application that wants to drive a phone verification workflow programmatically

The module has two functional planes:
1. **Proofing plane** (`RemoteProofingService`) — Drives the remote identity verification workflow: check device, verify phone, send/confirm OTP, send/validate/resend SMFA link
2. **Utility/admin plane** (`RemoteUtilityService`) — Manages system configuration at runtime: supplier allocations, sponsor configs, Experian credentials, high-risk addresses, phone velocity windows

**There is no authentication on any endpoint.** The only security is network-level access control (not visible in this code). All 26 endpoints are open POST endpoints that accept JSON.

---

## B. Module Architecture Overview

```
RemoteRest WAR
├── web.xml                         → registers IBMRestServlet on /resources/*
├── application-context.xml         → Spring DI, EclipseLink JPA (RemoteRestPUN), ipsWebDS, JtaTransactionManager
├── ips.properties                  → ALL external endpoints, credentials, env-specific URLs
│
└── RemoteServices JAR (bundled)
    ├── com.ips.jaxrs
    │   └── RemoteRestResource.java  → 26 POST endpoints, all delegate immediately
    │
    ├── com.ips.service
    │   ├── RemoteProofingServiceImpl.java    → 7 proofing workflow methods
    │   ├── RemoteSupportServiceImpl.java     → shared utilities: validation, response building, velocity checks
    │   └── RemoteUtilityServiceImpl.java     → 17 admin/config management methods
    │
    ├── com.ips.request
    │   ├── RemoteRequest.java        → PII + device/session/passcode fields (all String)
    │   └── RemoteUtilityRequest.java → Config management request (sponsor, app, supplier allocations)
    │
    ├── com.ips.response
    │   ├── RemoteResponse.java       → transactionID, verified, reviewStatus, reasonCode, attempt counts
    │   ├── RemoteUtilityResponse.java
    │   ├── RemoteErrorResponse.java
    │   ├── ErrorResponseModel.java
    │   ├── ErrorResponseStatus.java
    │   ├── InputValidationError.java
    │   ├── ResponseStatusModel.java
    │   └── ValidationError.java
    │
    └── com.ips.validation
        ├── RemoteProofingValidation.java       → field-by-field validation per endpoint
        ├── RemoteProofingValidatedField.java   → enum of field names + regex + required flags
        └── ErrorMessage.java                  → error code + HTTP response code mapping
```

**Key dependencies bundled into RemoteRest WAR:**
- `IVSPersistence` — all entity, DAO, and service logic
- `RemoteServices` — this module
- `Common` — `CustomLogger`, `Utils`, `DateTimeUtil`, `SpringUtil`
- `LexisNexisRDP` — LexisNexis REST client library
- `EquifaxIDFS` — Equifax SOAP client (JAXB)
- `EquifaxRest` — Equifax DIT/SMFA REST client
- `POLocator` — USPS PO Locator REST client
- `EntRegWebFilters-7.0.1.jar` — CustReg SSO (local JAR)

---

## C. Technology / Configuration Inventory

| Item | Value |
|---|---|
| **Java** | 8+ (inferred from JJWT 0.10.5 + Gradle 8.1.1) |
| **App Server** | IBM WebSphere 9.x (`IBMRestServlet`, `ibm-web-bnd.xml`) |
| **Build** | Gradle 8.1.1 (WAR plugin) |
| **REST Framework** | JAX-RS 2.1 via IBM `IBMRestServlet` + `JacksonJsonProvider` 2.15.2 |
| **DI** | Spring 5.3.33 `@Autowired` + `@Service` |
| **JPA / ORM** | EclipseLink (weaving=false), persistence unit `RemoteRestPUN`, datasource `ipsWebDS` |
| **Transactions** | JTA via `JtaTransactionManager` (WebSphere-managed) |
| **JSON** | Jackson 2.15.2 + IBM JSON4J |
| **Logging** | Log4j2 2.17.1 via custom `CustomLogger` wrapper |
| **Security** | **None in code.** No filters, no authentication checks, no `@RolesAllowed`. Network-level only. |
| **Session** | **Completely stateless.** No `HttpSession` usage anywhere. |
| **External Config** | `ips.properties` — all URLs, credentials, LDAP settings, ThreatMetrix keys, Experian secrets |

**Notable: `ips.properties` contains plaintext credentials including:**
- Experian JWT username/password (`crosscore_uat@usps.gov` / `25234!QAZ2wsx#EDC`)
- Experian client ID and client secret (`K4QAnajcQwKQK3dtDyYtL5GgcRmJC2V2` / `GOwWBpih5aIyjUPR`)
- 30 Experian test user records with full name, address, phone numbers
- ThreatMetrix API keys and block/safe/watch list IDs
- SSA OAuth client IDs and key IDs

---

## D. REST Endpoint Inventory

**Base path:** `POST /resources/remote/{path}`  
**Content-Type:** `application/json` (both request and response)  
**Auth:** None  
**Single resource class:** `RemoteRestResource` (`@Path("remote")`)

### Proofing Endpoints

| # | Path | Service Method | Purpose | Key Inputs | Key Response Fields |
|---|---|---|---|---|---|
| 1 | `POST /AssessDevicePlusEmailRisk` | `remoteProofingService.assessDevicePlusEmailRisk()` | Device + email risk assessment (LexisNexis bundled product). No `sponsorUserId` required — uses address hash as ID. | sponsorCode/ID, appCode/ID, name, address, email, profilingSessionID, webSessionID | reviewStatus, reasonCode, verified |
| 2 | `POST /CheckDevice` | `remoteProofingService.checkDevice()` | Device reputation check only (LexisNexis). Requires `sponsorUserId`. | sponsorCode/ID, appCode/ID, name, address, mobilePhone, profilingSessionID, customerUniqueID | reviewStatus, reasonCode, verified |
| 3 | `POST /VerifyPhone` | `remoteProofingService.verifyPhone()` | Phone verification + passcode/SMFA delivery. Most complex endpoint. | All PII fields, mobilePhone, birthDate, profilingSessionID, deviceTypeMobile, otpExpiresMinutes, smsUrl, targetUrl | transactionID, conversationID, verified, phoneVerified, passcodeSent, reasonCode |
| 4 | `POST /ConfirmPasscode` | `remoteProofingService.confirmPasscode()` | Validate OTP entered by user against vendor records. | sponsorCode/ID, customerUniqueID, passcode | verified, reasonCode, confirmationAttemptsForPasscode, confirmationLimitReached |
| 5 | `POST /RequestPasscode` | `remoteProofingService.requestPasscode()` | Resend OTP to previously-verified phone. | sponsorCode/ID, customerUniqueID | passcodeSent, transactionID, reasonCode |
| 6 | `POST /ResendLink` | `remoteProofingService.resendLink()` | Resend Equifax SMFA link (DIT only). | sponsorCode/ID, customerUniqueID | passcodeSent, transactionID, reasonCode |
| 7 | `POST /ValidateLink` | `remoteProofingService.validateLink()` | Check if user tapped the SMFA link (DIT only). Polls Equifax SMFA status. | sponsorCode/ID, customerUniqueID | verified, sessionID, reasonCode |

### Admin/Utility Endpoints

| # | Path | Purpose | Key Inputs |
|---|---|---|---|
| 8 | `POST /CreateRefSponsorConfigurationRecords` | Bootstrap sponsor config rows in DB for a new remote client | sponsorCode |
| 9 | `POST /CreateRpOtpAttemptConfigRecords` | Bootstrap `rp_kba_attempts` rows for new remote client | sponsorCode |
| 10 | `POST /CreateRpInfPvAttemptConfigRecords` | Bootstrap `rp_inf_pv_attempts` rows | sponsorCode |
| 11 | `POST /CreateRpFeatureAttemptRecords` | Bootstrap `rp_features_attempts` rows | sponsorCode, appCode |
| 12 | `POST /RemoveRefSponsorConfigurationRecords` | Remove sponsor config rows | sponsorCode |
| 13 | `POST /RemoveRpFeatureAttemptRecords` | Remove feature attempt rows | sponsorCode, appId |
| 14 | `POST /RemoveRpOtpAttemptConfigRecords` | Remove OTP attempt config rows | sponsorCode |
| 15 | `POST /GetModificationFingerprint` | Returns the module's build-time fingerprint string (`REMOTE_SERVICES_MOD_FP = "04-24-2023-1"`) | sponsorCode |
| 16 | `POST /ConfigVerificationMethod` | Set supplier split percentages (LexisNexisOTPAllocation, EquifaxSMFAAllocation, EquifaxIDFSAllocation, ExperianCCAllocation) | sponsorCode, allocation integers |
| 17 | `POST /RetrieveSupplierConfig` | Read current supplier allocation config for a sponsor | sponsorCode |
| 18 | `POST /ConfigPhoneVelocityWindow` | Update the phone velocity window (hours) for a sponsor | sponsorCode, phoneVelocityWindow |
| 19 | `POST /UpdatePersonProofingStatus` | Directly update a person's proofing status in the DB | personId, proofingStatus |
| 20 | `POST /SaveExperianAltApiInfo` | Write Experian JWT/XCORE credentials to `rp_supplier_token` table | experianTokenCode, experianTokenType, experianAccessToken |
| 21 | `POST /RemoveExperianAltApiInfo` | Delete Experian credential from `rp_supplier_token` | experianTokenCode, experianTokenType |
| 22 | `POST /SaveRefSponsorConfiguration` | Write a key-value config record to `ref_sponsor_configuration` | sponsorCode, sponsorConfigName, sponsorConfigValue |
| 23 | `POST /RetrieveRefSponsorConfiguration` | Read a key-value config record | sponsorCode, sponsorConfigName |
| 24 | `POST /RemoveRefSponsorConfiguration` | Delete a key-value config record | sponsorCode, sponsorConfigName |
| 25 | `POST /ObtainExperianWebToken` | Directly obtain and return an Experian JWT from CrossCore — for debugging | experianJwtUserId, experianJwtPasscode |
| 26 | `POST /GetHighRiskAddresses` | List high-risk address records | checkHighRiskAddress |
| 27 | `POST /UpdateHighRiskAddress` | Add/update a high-risk address | highRiskAddressId |
| 28 | `POST /CheckRegExForMatch` | Test a value against a regex — validation utility | fieldValue, regEx |

---

## E. Service Class Inventory

### `RemoteProofingServiceImpl` — Proofing Workflow Orchestrator

**Spring bean name:** `remoteProofingService`  
**Role:** Orchestrates the full phone verification lifecycle. Calls `RemoteSupportService` for every cross-cutting concern (validation, DB lookup, response building, velocity checks) and `PhoneVerificationService` (from IVSPersistence) for vendor calls.

**Dependencies autowired:**
- `RemoteSupportService` — nearly every operation delegates here
- `PersonDataService` — person lookups
- `PhoneVerificationService` — vendor phone calls (LexisNexis, Equifax, Experian)
- `ProofingService` — person upsert, session start
- `RefSponsorDataService` — sponsor lookup
- `RpDeviceReputationResponseService` — INF flag check
- `SponsorApplicationMapService` — sponsor/app validation
- `SponsorIncomingRequestsService` — audit logging

**Key internal constant strings** (used as `reasonCode` values in responses):

| Constant | Value | Meaning |
|---|---|---|
| `REASON_APPROVED_PHONE_VERIFICATION` | `"ApprovedPhoneVerification"` | Equifax DIT or Experian silent auth passed immediately |
| `REASON_FAILED_PHONE_VERIFICATION` | `"FailedPhoneVerification"` | Vendor returned fail/deny |
| `REASON_FAILED_PHONE_VELOCITY` | `"FailedPhoneVelocity"` | Too many phone verification attempts |
| `REASON_FAILED_OTP_DELIVERY` | `"FailedOTPDelivery"` | Could not send passcode |
| `REASON_FAILED_OTP_VELOCITY` | `"FailedOTPVelocity"` | Too many passcode send attempts |
| `REASON_FAILED_OTP_CONFIRMATION` | `"FailedOTPConfirmation"` | Wrong passcode entered |
| `REASON_PASSED_OTP_CONFIRMATION` | `"PassedOTPConfirmation"` | Correct passcode, LOA achieved |
| `REASON_SUCCESSS_OTP_DELIVERY` | `"SuccessfulOTPDelivery"` | Passcode sent, await confirm |
| `REASON_FAILED_SMFA_DELIVERY` | `"FailedSMFADelivery"` | SMFA link failed to send |
| `REASON_PASSED_SMFA_VALIDATION` | `"PassedSMFAValidation"` | User tapped link, verified |
| `REASON_FAILED_SMFA_VALIDATION` | `"FailedSMFAValidation"` | SMFA link result was RED |
| `REASON_FAILED_SUPPLIER_SELECTION` | `"FailedSupplierSelection"` | All suppliers unavailable |

---

### `RemoteSupportServiceImpl` — Shared Infrastructure (God Class)

**Spring bean name:** `remoteSupportService`  
**Role:** Contains every shared operation used by both proofing methods. This is the effective business logic hub.

**Key methods:**

| Method | What it does |
|---|---|
| `buildRequestValidationResponse()` | Calls `RemoteProofingValidation.validateRemoteProofingData()`, checks if sponsor+app combo is enabled, returns 400 if invalid |
| `populatePersonVo()` | Maps `RemoteRequest` fields → `PersonVo` (PII, phone, session IDs) |
| `setSponsorAppData()` | Looks up `RefSponsor` + `RefApp` by code/name, sets IDs on `PersonVo` |
| `determineVerificationMethod()` | Calls `VerificationProviderService.determineVerificationMethod()` → the DB row-lock hotspot |
| `checkDeviceReputation()` | Calls `CheckDeviceService` (LexisNexis device assessment), saves result, builds response |
| `assessDevicePlusEmailRisk()` | Calls `CheckDevicePlusService` (LexisNexis bundled product including email risk) |
| `checkPhoneVerified()` | Determines if phone is already verified for `confirmPasscode`/`requestPasscode`/`validateLink`. Checks proofing status, velocity, event record. Returns a reason code string or null |
| `passPhoneVelocity()` | Wraps `ProofingService.phoneVelocityCheck()` — checks `kba_lockout_info` + event count in window |
| `passVelocityCheck()` | Routes to `OtpVelocityCheckService` (passcode) or `SmfaVelocityCheckService` (SMFA link) based on velocity type |
| `passcodeConfirmation()` | Calls `PhoneVerificationService.confirmPasscode()` — the vendor SOAP/REST confirm call |
| `sendPasscodeToPhone()` | Calls `PhoneVerificationService.sendPasscodeSuccessful()` |
| `checkSmfaLinkValidation()` | Calls `EquifaxService` to check SMFA status (green/red/pending) |
| `getLatestPV()` | Fetches the latest `RpEvent` with phone verification for a person + supplier |
| `getLatestPhoneSupplier()` | Gets `RefOtpSupplier` from the most recent `RpEvent` |
| `buildRemoteResponse()` | Core response builder — populates `RemoteResponse` with verified status, attempts, lockout, transactionID, reasonCode based on velocity stats and proofing state |
| `buildPVResponse()` | Wraps `RemoteResponse` into JAX-RS `Response` with CORS headers |
| `buildErrorResponse()` | Builds a 400 error response |
| `exceptionErrorResponse()` | Catches exceptions, logs them, returns 500 error response |
| `personNotFoundResponse()` | Standard 404-style response when person record not found |
| `eventNotFoundResponse()` | Standard error when no `RpEvent` exists for the person |
| `saveDeviceReputationAssessmentResponse()` | Persists the raw device assessment to `rp_device_reputation_response` |
| `calculateNameAddressHash()` | Used for `AssessDevicePlusEmailRisk` when no `customerUniqueID` is provided |
| `mergeParamObjects()` | Builds `DeviceAssessmentParamVo` from both `RemoteRequest` and `PersonVo` |

**Important: `RemoteSupportServiceImpl` adds CORS headers on every response:**
```java
Access-Control-Allow-Origin: [origin value from header]
Access-Control-Allow-Headers: Content-Type, Authorization
```
No whitelist check on the origin. Any origin is reflected back.

---

### `RemoteUtilityServiceImpl` — Admin/Config Manager

**Spring bean name:** `remoteUtilityService`  
**Role:** Runtime management of IVS configuration. Used by USPS operations teams and automated CI/CD pipelines to configure sponsor settings without a deployment.

**Key responsibilities:**
- Bootstrap and teardown per-sponsor DB rows (OTP attempt config, INF attempt config, feature attempts, sponsor configuration)
- Read/write supplier split percentages live
- Read/write phone velocity window
- Directly force-update person proofing status (admin override)
- Save/remove Experian alternative API credentials (`rp_supplier_token`)
- Save/retrieve/remove `ref_sponsor_configuration` key-value pairs
- Obtain Experian JWT tokens on demand (for debugging)
- Query and update high-risk address records
- Test regex patterns

---

### `RemoteProofingValidation` — Input Validator

**Not a Spring bean — called statically from `RemoteSupportServiceImpl.buildRequestValidationResponse()`.**

**What it validates (per endpoint):**
- `sponsorCode` — non-null, alphanumeric, max 10 chars
- `customerUniqueID` — non-null for all except `AssessDevicePlusEmailRisk`
- `firstName`, `lastName` — non-null for device/phone endpoints; regex `^[a-zA-Z0-9' -,.#\/]+$`
- `streetAddress1`, `city`, `state`, `zipCode` — non-null for device/phone; field-specific length limits
- `country` — ISO alpha-2 or alpha-3; defaults to "US" if blank; converts GB → UK
- `mobilePhone` — required for `VerifyPhone`, `CheckDevice`; 10-digit format
- `birthDate` — required for `VerifyPhone`; format `MMddyyyy`
- `passcode` — required for `ConfirmPasscode`; numeric only
- `webSessionID`, `profilingSessionID` — optional strings

**Validation behavior:** Returns a `HashMap<String, ValidationError>` — if not empty, `buildRequestValidationResponse()` returns a 400 response with the error details before any business logic runs.

**Sponsor/App disabled check:** If `SponsorApplicationMapService.isDisabled(sponsorId, appId)` returns true, the endpoint returns `"Web service call for {app} application of {sponsor} sponsor is disabled"`.

---

## F. Major Workflow Walkthroughs

### Workflow 1 — Phone Verification (OTP Path, LexisNexis)

**Entry:** `POST /resources/remote/VerifyPhone`  
**Consumer:** BCG, CoA, HoldMail, Informed Delivery

```
POST /VerifyPhone {sponsorCode, customerUniqueID, firstName, lastName, address, mobilePhone, birthDate, profilingSessionID, ...}

1. RemoteRestResource.verifyPhone(remoteReq, origin)
   └─ RemoteProofingServiceImpl.verifyPhone(remoteReq, origin)

2. Validation:
   RemoteSupportService.buildRequestValidationResponse()
     └─ RemoteProofingValidation.validateRemoteProofingData()
         → Validate all fields; if errors → return 400

3. Person/Sponsor Setup:
   RemoteSupportService.populatePersonVo(remoteReq, personVo)
   RemoteSupportService.setSponsorAppData(remoteReq, personVo)
     └─ DB: SELECT ref_sponsor, ref_app by code

4. Person Upsert + Session:
   ProofingService.updatePerson(personVo)
     └─ DB: INSERT/UPDATE person, person_data, kba_lockout_info
   ProofingService.startProofingSession(person, personVo)
     └─ DB: INSERT/UPDATE rp_proofing_session, person_proofing_status

5. INF Flag Check:
   RpDeviceReputationResponseService.isIndividualNotFound(personId)
     └─ DB: SELECT rp_device_reputation_response

6. Supplier Selection:
   RemoteSupportService.determineVerificationMethod(personVo)
     └─ VerificationProviderService.determineVerificationMethod()
         └─ DB: SELECT rp_kba_attempts ORDER BY attempts ASC ← supplier routing
         └─ DB: UPDATE rp_kba_attempts attempts+1 ← LOCK HOTSPOT

7. Phone Velocity Check:
   RemoteSupportService.passPhoneVelocity(person, personVo, supplier, "verifyPhone")
     └─ ProofingService.phoneVelocityCheck()
         └─ DB: SELECT kba_lockout_info, COUNT rp_event in window
     → If exceeded: document audit + return FAILED_PHONE_VELOCITY response

8. Phone Verification (vendor call):
   PhoneVerificationService.verifyPhone(personVo, supplier)
     └─ ManageEventService.createEvent() → DB: INSERT rp_event, rp_phone_verifications
     └─ [LexisNexis] LexisNexisService.verifyPhone()
           → REST POST to LexisNexis PhoneFinder workflow
           → REST POST to LexisNexis OTP workflow
           → DB: UPDATE rp_event, rp_lexisnexis_result
     └─ [Equifax IDFS] SOAP call → DB: UPDATE rp_event, rp_equifax_result
     └─ [Experian] JWT + CrossCore REST → DB: UPDATE rp_event, rp_experian_decision_result

9. Decision branching on pvResponse.decision:
   FAILED/DENIED → document audit + return FAILED_PHONE_VERIFICATION
   APPROVED/PASSED → return APPROVED_PHONE_VERIFICATION (LOA achieved immediately, no OTP needed)
   FOR_REVIEW (Experian OTP flow) → get transactionId, return SUCCESSFUL_OTP_DELIVERY

10. OTP/SMFA Velocity check:
    RemoteSupportService.passVelocityCheck(VELOCITY_TYPE_PASSCODE or SMFA, ...)
      → OtpVelocityCheckService or SmfaVelocityCheckService
      → DB: SELECT rp_otp_attempts or rp_smfa_attempts

11. Send OTP or SMFA:
    [Equifax DIT/SMFA] PhoneVerificationService.sendSmfaLink()
      → Equifax SMFA REST POST → returns int (0=success, 422=landline, 500=error)
    [All others] PhoneVerificationService.sendPasscodeSuccessful()
      → Equifax IDFS SOAP / LexisNexis REST / Experian CrossCore

12. Return transactionID + reason:
    SuccessfulOTPDelivery or SuccessfulSMFADelivery
    → document audit in sponsor_incoming_requests
    → buildPVResponse() adds CORS headers, serializes RemoteResponse to JSON
```

---

### Workflow 2 — Confirm Passcode (LexisNexis or Equifax IDFS)

**Entry:** `POST /resources/remote/ConfirmPasscode`

```
POST /ConfirmPasscode {sponsorCode, customerUniqueID, passcode}

1. Validation: sponsorCode + customerUniqueID + passcode required
2. populatePersonVo() + setSponsorAppData()
3. personDataService.findFirstBySponsor(sponsor, sponsorUserId) → DB: SELECT person
   → If null: personNotFoundResponse()
4. getLatestPhoneSupplier(personId) → last RpEvent.refOtpSupplier
   → If Equifax DIT: return 400 (not implemented for SMFA)
   → If Experian: populatePersonVoFromPerson()
5. getLatestPV(personId, supplierId) → SELECT rp_event + children
   → If null: eventNotFoundResponse()
6. Set mobileNumber on personVo from rpEvent.rpPhoneVerification
7. checkPhoneVerified(origin, person, personVo, rpEvent, supplier, "confirmPasscode")
   → Checks proofing status, velocity, whether phone was verified
   → Returns reason code string or null
   → If FAILED_OTP_VELOCITY: document audit + return velocity fail response
   → If FAILED_PHONE_VERIFICATION: document audit + return fail response
   → If APPROVED: return APPROVED response (already done)
8. Set personVo.transactionId from rpEvent.latestOtpAttempt.transactionKey
9. passcodeConfirmation(origin, personVo, supplier)
   → PhoneVerificationService.confirmPasscode(personVo, supplier, event)
      → [Equifax IDFS] SOAP ConfirmPasscodeRequest
      → [LexisNexis] REST callConfirmPasscodeWorkflow(conversationId)
      → [Experian] CrossCore resume flow
   → Returns reason code string (null = success)
10. passVelocityCheck() again — just to count the attempt
11. If FAILED_OTP_CONFIRMATION: document audit + return fail response
12. If null (confirmed): document audit + return PASSED_OTP_CONFIRMATION
    → ProofingService.updateProofingStatus(LOA_ACHIEVED) (inside confirmPasscode chain)
    → TruthDataReturnService → CustReg SOAP assertion
```

---

### Workflow 3 — Device Assessment (BCG via AssessDevicePlusEmailRisk)

**Entry:** `POST /resources/remote/AssessDevicePlusEmailRisk`  
**Consumer:** BCG (Business Customer Gateway) through CustReg

```
POST /AssessDevicePlusEmailRisk {sponsorCode, appCode, name, address, email, profilingSessionID, webSessionID}
Note: customerUniqueID is optional — if absent, address hash is computed as the ID

1. Validation: sponsorCode, name, address fields, country validated
2. If sponsorUserId null: calculateNameAddressHash(remoteReq)
   → SHA-256 of normalized name+address fields → used as IPS person key
3. updatePerson(personVo) + startProofingSession()
4. mergeParamObjects(remoteReq, personVo) → builds DeviceAssessmentParamVo with:
   - profilingSessionID, webSessionID, trueIPAddress, emailAddress
5. assessDevicePlusEmailRisk(origin, remoteReq, person, personVo, assessmentVo)
   └─ CheckDevicePlusService.assessDevicePlusEmailRisk()
      → LexisNexis RDP bundled product REST call (device + email risk in one)
      → Saves result to rp_device_reputation_response
      → Returns DeviceAssessmentResult with reviewStatus, reasonCode
6. saveDeviceReputationAssessmentResponse()
7. Return JSON: { reviewStatus, reasonCode, verified }
   → PASS/REVIEW/REJECT status from LexisNexis
```

---

### Workflow 4 — Runtime Config Management (ConfigVerificationMethod)

**Entry:** `POST /resources/remote/ConfigVerificationMethod`  
**Consumer:** USPS ops team or CI/CD

```
POST /ConfigVerificationMethod {
  sponsorCode: "COA",
  lexisNexisOTPAllocation: 60,
  equifaxSMFAAllocation: 40,
  equifaxIDFSAllocation: 0,
  experianCCAllocation: 0
}

1. Validate: sponsorCode required; allocations must sum to 100 (validated in service)
2. Lookup RefSponsor by code → DB: SELECT ref_sponsor
3. For each allocation value != null:
   OtpAttemptConfigService.adminUpdate() or RpInfPvAttemptConfigService.adminUpdate()
     → DB: UPDATE rp_kba_attempts SET total_attempts = {value}
         WHERE sponsor_id = {id} AND kba_supplier_id = {supplierConst}
4. Return success response
```

---

## G. Database / Table Usage Analysis

### Tables read per endpoint

| Table | Endpoints Reading | Purpose |
|---|---|---|
| `ref_sponsor` | All | Sponsor lookup by code/name |
| `ref_app` | All | App lookup by code/name |
| `ref_kba_supplier` | VerifyPhone, ConfirmPasscode, RequestPasscode, ValidateLink, ResendLink | Get supplier type flags |
| `rp_kba_attempts` | VerifyPhone | Supplier routing query (sorted by attempts) |
| `rp_inf_pv_attempts` | VerifyPhone (if INF flag set) | Supplier routing for INF path |
| `rp_event` | ConfirmPasscode, RequestPasscode, ValidateLink, ResendLink | Latest phone verification event |
| `rp_phone_verifications` | ConfirmPasscode, RequestPasscode, ValidateLink, ResendLink | Mobile phone on file |
| `rp_otp_attempts` | ConfirmPasscode, RequestPasscode | Passcode attempt count |
| `rp_smfa_attempts` | ValidateLink, ResendLink | SMFA attempt count |
| `kba_lockout_info` | VerifyPhone, RequestPasscode, ResendLink | Lockout status |
| `person` | All (create/find) | Person record |
| `person_data` | All | PII including address_hash |
| `person_proofing_status` | ConfirmPasscode, RequestPasscode, ResendLink | LOA status check |
| `rp_device_reputation_response` | VerifyPhone | INF flag check |
| `sponsor_application_map` | All | Is sponsor+app combo enabled? |
| `sponsor_incoming_requests` | All (writes) | Audit log |
| `ref_sponsor_configuration` | Various | Sponsor feature flags |
| `rp_supplier_token` | Experian flows | Alternative JWT credentials |

### Tables written per endpoint

| Endpoint | Tables Written |
|---|---|
| `CheckDevice`, `AssessDevicePlusEmailRisk` | `person`, `person_data`, `kba_lockout_info`, `rp_proofing_session`, `person_proofing_status`, `rp_device_reputation`, `rp_device_reputation_response`, `sponsor_incoming_requests` |
| `VerifyPhone` | All above + `rp_kba_attempts` (UPDATE ← **lock**), `rp_event`, `rp_phone_verifications`, `rp_otp_attempts` or `rp_smfa_attempts`, `rp_lexisnexis_result` or `rp_equifax_result` or `rp_experian_decision_result` |
| `ConfirmPasscode` | `rp_otp_attempts`, `person_proofing_status`, `rp_truth_data_send_response`, `sponsor_incoming_requests` |
| `RequestPasscode` | `rp_otp_attempts`, `sponsor_incoming_requests` |
| `ValidateLink` / `ResendLink` | `rp_smfa_attempts`, `sponsor_incoming_requests` |
| `ConfigVerificationMethod` | `rp_kba_attempts` total_attempts columns |
| `UpdatePersonProofingStatus` | `person_proofing_status` directly (admin override) |

### Concurrency / locking risks

**Same as IVSPersistence:** The `rp_kba_attempts` UPDATE inside `VerifyPhone` → `determineVerificationMethod()` is the primary Oracle row-lock hotspot under high CoA volume. The in-memory `SupplierAttemptTracker` fix resolves this.

**Additional risk:** `sponsor_incoming_requests` receives a write on every single API call (audit). Under high concurrent traffic, this table is an insert-heavy table with no visible read pressure but potentially high write contention if indexed heavily.

---

## H. Integration Map

All vendor integrations are in `IVSPersistence` and called via delegated service calls. From the RemoteServices perspective:

| Vendor | Triggered By | Via Service | Protocol |
|---|---|---|---|
| **LexisNexis RDP** | `VerifyPhone` (OTP flow), `CheckDevice`, `AssessDevicePlusEmailRisk` | `PhoneVerificationService.verifyPhone()` → `LexisNexisServiceImpl` | HTTPS REST (conversations API, `usps.otp.online`, `usps.phonefinder.online`) |
| **LexisNexis Device** | `CheckDevice`, `AssessDevicePlusEmailRisk` | `CheckDeviceService`, `CheckDevicePlusService` | HTTPS REST (sponsor-specific device workflow URL) |
| **Equifax IDFS** | `VerifyPhone`, `ConfirmPasscode`, `RequestPasscode` | `PhoneVerificationService` → `EquifaxServiceImpl` | HTTPS SOAP (`ifs-uat.us.equifax.com` / `ifs.us.equifax.com`) |
| **Equifax DIT** | `VerifyPhone` | `EquifaxServiceImpl.verifyPhoneWithEquifaxDIT()` | HTTPS REST + OAuth2 (`api.uat.equifax.com` / `api.equifax.com`) |
| **Equifax SMFA** | `VerifyPhone` (SMFA path), `ValidateLink`, `ResendLink` | `EquifaxServiceImpl` | HTTPS REST + OAuth2 (`secure-mfa/v2/authentications`) |
| **Experian CrossCore** | `VerifyPhone` (Experian path), `ConfirmPasscode` (Experian) | `ExperianServiceImpl` | HTTPS REST + JWT (`us-api.experian.com/decisionanalytics/crosscore`) |
| **CustReg / EntReg** | `ConfirmPasscode` (final step — asserts LOA) | `TruthDataReturnService` → `CustRegService` | HTTPS SOAP (`EntRegProxy.userAssertion()`) |
| **ThreatMetrix** | `AssessDevicePlusEmailRisk`, `CheckDevice` (via LexisNexis bundled result) | `CheckDeviceService` / `CheckDevicePlusService` | Browser JS profiling + LexisNexis RDP Device API |
| **Oracle DB** | All | JPA/EclipseLink via IVSPersistence DAOs | JDBC via JNDI `ipsWebDS` |

---

## I. Error Handling and Transaction Boundaries

### Error Handling Strategy

Every method in `RemoteProofingServiceImpl` wraps each logical phase in its own `try-catch`. This produces a granular chain:

```java
// Typical pattern across all methods:
try {
    // Phase: do X
} catch (Exception e) {
    return remoteSupportService.exceptionErrorResponse(personVo, methodName, "doing X", origin, e);
}
```

`exceptionErrorResponse()` logs the exception with `CustomLogger.error()` and returns a structured JSON error response. **Exceptions are never re-thrown** — every exception is converted to a response object. This means callers always get a JSON body even on 500 errors, but it also means that partial state (DB inserts already committed before the exception) is never rolled back.

### Transaction Boundaries

**No `@Transactional` in RemoteServices.** All transactional behavior is inherited from IVSPersistence service methods. The key transaction non-atomicity:

| Phase | Committed separately |
|---|---|
| Person upsert | `ProofingService.updatePerson()` — its own `@Transactional` |
| Session start | `ProofingService.startProofingSession()` — its own `@Transactional` |
| Supplier counter increment | `OtpAttemptConfigService.callingOTP()` — its own `@Transactional` |
| Event creation | `ManageEventService.createEvent()` — its own `@Transactional` |
| Vendor call | **No transaction** |
| OTP status update | Separate `@Transactional` |

If a vendor call succeeds (LexisNexis/Equifax/Experian) but the subsequent DB write fails (e.g., `UPDATE person_proofing_status`), the vendor has been billed for the call and the user's phone is "verified" at the vendor but not recorded in IPS. Manual intervention is required.

### Response HTTP Status Codes

All proofing responses use **HTTP 200** regardless of whether verification passed or failed. The business outcome is conveyed through `reasonCode` in the JSON body. Only structural errors (invalid input, missing person) return non-200 status codes (400). This means callers cannot rely on HTTP status alone — they must parse `reasonCode`.

---

## J. Mermaid Diagrams

### Module Dependency Map

```mermaid
graph TD
    subgraph "RemoteRest WAR"
        APP[IPSRemoteRestApplication\nJAX-RS registration]
        RR[RemoteRestResource\n26 POST endpoints]
    end
    subgraph "RemoteServices JAR"
        RPS[RemoteProofingServiceImpl\nProofing workflows]
        RSS[RemoteSupportServiceImpl\nShared utilities]
        RUS[RemoteUtilityServiceImpl\nConfig management]
        VAL[RemoteProofingValidation\nInput validator]
    end
    subgraph "IVSPersistence JAR"
        PS[ProofingService]
        PVS[PhoneVerificationService]
        VPS[VerificationProviderService]
        CDS[CheckDeviceService]
        SIR[SponsorIncomingRequestsService]
        DAO[100+ DAOs]
    end
    subgraph "Vendor JARs"
        LN[LexisNexisRDP]
        EFX[EquifaxIDFS + EquifaxRest]
        EXP[Experian via IVSPersistence]
    end
    subgraph "External"
        ORACLE[(Oracle DB\nipsWebDS)]
        CR[CustReg SOAP]
        LN_API[LexisNexis REST API]
        EFX_API[Equifax SOAP/REST]
        EXP_API[Experian CrossCore REST]
    end

    APP --> RR
    RR --> RPS
    RR --> RUS
    RPS --> RSS
    RPS --> PS
    RPS --> PVS
    RSS --> VPS
    RSS --> CDS
    RSS --> VAL
    RSS --> SIR
    PS --> DAO --> ORACLE
    PVS --> LN --> LN_API
    PVS --> EFX --> EFX_API
    PVS --> EXP --> EXP_API
    PS --> CR
```

### VerifyPhone Sequence (Happy Path — LexisNexis OTP)

```mermaid
sequenceDiagram
    participant C as Caller (BCG/CoA)
    participant RR as RemoteRestResource
    participant RPS as RemoteProofingService
    participant RSS as RemoteSupportService
    participant PS as ProofingService
    participant VPS as VerificationProviderService
    participant PVS as PhoneVerificationService
    participant LN as LexisNexis API
    participant DB as Oracle DB

    C->>RR: POST /VerifyPhone {sponsorCode, customerUniqueID, mobilePhone, ...}
    RR->>RPS: verifyPhone(remoteReq, origin)
    RPS->>RSS: buildRequestValidationResponse() → validate fields
    RSS-->>RPS: null (valid)
    RPS->>RSS: populatePersonVo() + setSponsorAppData()
    RSS->>DB: SELECT ref_sponsor, ref_app
    RPS->>PS: updatePerson(personVo)
    PS->>DB: INSERT/UPDATE person, person_data
    RPS->>PS: startProofingSession(person, personVo)
    PS->>DB: INSERT rp_proofing_session, UPDATE person_proofing_status
    RPS->>RSS: determineVerificationMethod(personVo)
    RSS->>VPS: determineVerificationMethod()
    VPS->>DB: SELECT rp_kba_attempts ORDER BY attempts ASC
    VPS->>DB: UPDATE rp_kba_attempts SET attempts+1 ← LOCK
    VPS-->>RSS: RefOtpSupplier(LexisNexis)
    RSS-->>RPS: supplier
    RPS->>RSS: passPhoneVelocity(person, personVo, supplier)
    RSS->>DB: SELECT kba_lockout_info + COUNT rp_event in window
    RSS-->>RPS: true (not locked)
    RPS->>PVS: verifyPhone(personVo, supplier)
    PVS->>DB: INSERT rp_event, rp_phone_verifications
    PVS->>LN: POST PhoneFinder workflow (REST)
    LN-->>PVS: precheck result
    PVS->>LN: POST OTP workflow (REST)
    LN-->>PVS: conversationId + OTP sent to phone
    PVS->>DB: UPDATE rp_event, rp_lexisnexis_result
    PVS-->>RPS: PhoneVerificationResponse(PASS)
    RPS->>RSS: passVelocityCheck(VELOCITY_TYPE_PASSCODE)
    RSS->>DB: SELECT rp_otp_attempts COUNT
    RPS->>PVS: sendPasscodeSuccessful()
    PVS->>LN: POST OTP send (included in workflow above)
    RPS->>RSS: getLatestPV(personId, supplierId)
    RSS->>DB: SELECT rp_event + rp_otp_attempts
    RPS->>RSS: buildRemoteResponse(SuccessfulOTPDelivery)
    RSS->>DB: INSERT sponsor_incoming_requests (audit)
    RSS->>RPS: RemoteResponse{transactionID, reasonCode="SuccessfulOTPDelivery"}
    RPS->>RR: Response 200 with CORS headers
    RR-->>C: 200 {"transactionID":"...", "reasonCode":"SuccessfulOTPDelivery", "passcodeSent":"true"}
```

### VerifyPhone Decision Tree

```mermaid
flowchart TD
    START[POST /VerifyPhone] --> VAL{Input valid?}
    VAL -- No --> R400[400 Validation Error]
    VAL -- Yes --> SUPP{Supplier selected?}
    SUPP -- No --> RFAIL[reasonCode: FailedSupplierSelection]
    SUPP -- Yes --> PVEL{Phone velocity passed?}
    PVEL -- No --> PVFAIL[reasonCode: FailedPhoneVelocity]
    PVEL -- Yes --> VENDOR{Vendor verifyPhone}
    VENDOR -- FAILED/DENIED --> PVFAIL2[reasonCode: FailedPhoneVerification]
    VENDOR -- APPROVED/PASSED\nEquifax DIT all-trust or\nExperian silent auth --> APPROVE[reasonCode: ApprovedPhoneVerification\nLOA achieved — no OTP needed]
    VENDOR -- FOR_REVIEW\nExperian OTP flow --> EXPOTP[reasonCode: SuccessfulOTPDelivery\nExperian OTP already sent]
    VENDOR -- PASS/REVIEW\nLexisNexis or Equifax IDFS --> OTPVEL{OTP/SMFA velocity passed?}
    OTPVEL -- No --> OTPVFAIL[FailedOTPVelocity or FailedSMFAVelocity]
    OTPVEL -- Yes --> SEND{Send OTP or SMFA link}
    SEND -- Equifax DIT --> SMFASEND[sendSmfaLink\nreturn SuccessfulSMFADelivery]
    SEND -- Others --> OTPSEND{sendPasscodeSuccessful}
    OTPSEND -- OTP not sent\nproofingStatus != OTP_SENT --> OTPFAIL[FailedOTPDelivery]
    OTPSEND -- Landline --> PFAIL[FailedPhoneVerification]
    OTPSEND -- Success --> SUCCESS[reasonCode: SuccessfulOTPDelivery]
```

---

## K. Production Risks and Technical Debt

### 🔴 Critical

**1. No authentication on any of 26 endpoints**  
All endpoints are completely open REST calls with no token, no API key, no session check. Security relies entirely on network-level controls (not visible in code). If the `/resources/remote/*` path is reachable from an unintended network segment, any caller can:
- Trigger phone verifications for any user
- Directly force-set a person's proofing status (`UpdatePersonProofingStatus`)
- Wipe/replace Experian credentials (`SaveExperianAltApiInfo`)
- Change supplier allocations (`ConfigVerificationMethod`)

**2. Plaintext credentials in `ips.properties`**  
The following are stored in a properties file that ships with the WAR:
- Experian JWT username + password (all environments, including prod)
- Experian client ID + client secret (same value for all environments — no secret rotation)
- Experian Tenant ID, Subscriber Sub Code
- ThreatMetrix API key (prod: `lrcbsgu2ion6lzv0`)
- SSA OAuth client IDs and KIDs
- 30 real-looking Experian test user records including full names, addresses, and phone numbers

**3. Arbitrary CORS origin reflection**  
`Access-Control-Allow-Origin` is set to whatever the `Origin` header contains, with no whitelist validation. Any cross-origin request from any domain gets an explicit CORS approval.

**4. All HTTP responses are 200 regardless of business outcome**  
Callers must parse `reasonCode` to understand the outcome. A `FailedPhoneVerification` returns HTTP 200. This means any caller that checks only HTTP status will silently treat failures as successes.

**5. No transaction wrapping vendor call + DB write**  
If LexisNexis/Equifax/Experian succeeds and the subsequent `person_proofing_status` update fails, the user is verified at the vendor but not in IPS. There is no compensating transaction or event log that enables recovery.

### 🟡 High Risk

**6. `RemoteSupportServiceImpl` is a God class with 500+ lines**  
It is responsible for: input validation, response building, CORS headers, person lookups, velocity checks, phone verification delegation, device assessment delegation, SMFA validation, audit logging, error response formatting, address hashing, DB event retrieval. Every method in `RemoteProofingServiceImpl` calls into it for nearly every operation. Any change here has blast radius across all 7 proofing workflows.

**7. `verifyPhone()` method is ~200 lines with 8+ nested try-catch blocks**  
The method handles every decision point inline. It is nearly impossible to unit test individual branches. A bug in any branch silently produces an incorrect `reasonCode` — the caller gets HTTP 200 and must know to interpret the reason code correctly.

**8. `checkPhoneVerified()` in `RemoteSupportServiceImpl` is the same cross-server state problem**  
For `confirmPasscode` and `requestPasscode`, this method checks if the phone is verified by looking at the `RpPhoneVerification` record in the DB. In a round-robin load-balanced environment (BC4/BC5), `verifyPhone()` runs on BC4, writes the phone verification record, and the subsequent `confirmPasscode()` call routes to BC5. BC5 calls `checkPhoneVerified()` which re-queries the DB — if BC4's transaction hasn't propagated yet, `checkPhoneVerified()` returns null and the call fails with `FailedPhoneVerification`. **This is the root cause of the alternating 400/200 bug documented in the transcripts.** The fix (`SupplierAttemptTracker`) addresses the lock hotspot, but the stateful dependency in `checkPhoneVerified()` remains.

**9. `GetModificationFingerprint` returns a hardcoded build-time string**  
`REMOTE_SERVICES_MOD_FP = "04-24-2023-1"` is a static constant that never changes automatically. If the module is deployed without updating this string, callers that use it to detect config changes will get false negatives.

**10. `UpdatePersonProofingStatus` — direct DB override with no audit trail**  
This endpoint directly calls `proofingService.updateProofingStatus(personId, proofingStatus)` with no validation of the status transition, no check that the person exists, and no entry in `sponsor_incoming_requests`. An operator typo can put a person in an impossible state (e.g., LOA_ACHIEVED without completing verification).

**11. `AssessDevicePlusEmailRisk` — anonymous users via address hash**  
When `customerUniqueID` is null, a SHA-256 hash of name+address is computed and used as the person key. If two users share the same name and address (e.g., a family at the same address), they will share the same IPS person record and `rp_device_reputation_response` history. The INF flag from one user's LexisNexis result will influence the other user's supplier selection in `VerifyPhone`.

### 🟢 Informational

**12. Duplicate `ips.properties`**  
The identical file appears in both `RemoteRest` and `IPSWeb`. Any environment-specific config change must be made in both places. A production URL change in one but not the other would cause divergent behavior between the JSF flow and the REST API flow.

**13. `IPSRestApplication.java` is a dead class**  
`IPSRestApplication` registers `RemoteRestResource` but is never referenced in `web.xml`. Only `IPSRemoteRestApplication` is registered. This file is either legacy or was never activated. It will still be compiled into the WAR and may cause confusion.

**14. `sendPasscodeSuccessful` return value is not used in verifyPhone**  
At line ~160 of `verifyPhone()`, `sendPasscodeSuccessful()` is called and its return is stored, but the actual OTP-sent check is done by re-reading `person.getProofingStatuses()` from the DB. The return value of `sendPasscodeSuccessful()` is functionally ignored in the determination of whether to return success. This is intentional (the DB state is more reliable) but confusing.

---

## L. Recommended Onboarding Order

| Order | File | Why |
|---|---|---|
| 1 | `ips.properties` | Every external URL, credential, and environment URL in one file. Read this first. |
| 2 | `RemoteRestResource.java` | 26 endpoints in 200 lines. See the full API surface. |
| 3 | `RemoteRequest.java` + `RemoteResponse.java` | The input/output contract for every proofing endpoint. |
| 4 | `RemoteSupportServiceImpl.java` (constants block only, lines 67–160) | All reason codes and operation name constants. These are the vocabulary for debugging. |
| 5 | `RemoteProofingValidation.java` | Understand what gets rejected at the door before any business logic runs. |
| 6 | `RemoteProofingServiceImpl.verifyPhone()` | The most complex and most-called method. Read it end-to-end once. |
| 7 | `RemoteSupportServiceImpl.checkPhoneVerified()` | The alternating-400 bug root cause. Understand this deeply. |
| 8 | `RemoteProofingServiceImpl.confirmPasscode()` | The companion to verifyPhone. The success path ends here. |
| 9 | `RemoteSupportServiceImpl.buildRemoteResponse()` | Every response goes through this. Understand what fields are set and why. |
| 10 | `RemoteUtilityServiceImpl.configVerificationMethod()` | The supplier allocation control. Understand how to change CoA split percentages. |
| 11 | `RemoteUtilityServiceImpl.saveRpSupplierToken()` | The Experian credential management. Understand how prod credentials are rotated. |
| 12 | `RemoteProofingServiceImpl.requestPasscode()` | The resend flow. Key for diagnosing "user never got their code" incidents. |
