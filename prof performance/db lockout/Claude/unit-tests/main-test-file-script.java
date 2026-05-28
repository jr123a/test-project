package com.ips.proofing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ips.entity.RefLoaLevel;
import com.ips.entity.RefOtpSupplier;
import com.ips.entity.RefSponsor;
import com.ips.entity.RpInfPvAttemptConfig;
import com.ips.entity.RpOtpAttemptConfig;
import com.ips.persistence.common.PersonVo;
import com.ips.service.OtpAttemptConfigService;
import com.ips.service.RefLoaLevelService;
import com.ips.service.RefSponsorConfigurationService;
import com.ips.service.RefSponsorDataService;
import com.ips.service.RpInfPvAttemptConfigService;

/**
 * Unit tests for VerificationProviderServiceImpl.
 *
 * Design:
 * - All @Autowired services injected as Mockito mocks via @InjectMocks.
 * - No Spring context, no WebSphere, no Oracle.
 * - CustomLogger calls are no-ops (Log4j falls back gracefully without config).
 * - Utils.getEnvironmentWithoutDot() is a static call; the refSponsorConfigurationService
 *   stub returns null for Test.Supplier.Options, skipping the non-prod override branch.
 *
 * Naming: methodName_shouldExpectedBehavior_whenCondition()
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VerificationProviderServiceImpl")
class VerificationProviderServiceImplTest {

    @Mock private RefLoaLevelService             refLoaLevelService;
    @Mock private OtpAttemptConfigService        otpAttemptConfigService;
    @Mock private RefSponsorDataService          refSponsorService;
    @Mock private RpInfPvAttemptConfigService    rpInfPvAttemptConfigService;
    @Mock private RefSponsorConfigurationService refSponsorConfigurationService;

    @InjectMocks
    private VerificationProviderServiceImpl service;

    private static final long SPONSOR_ID     = 32L;
    private static final long PROOFING_LEVEL = 1L;

    private RefLoaLevel loaLevel;
    private RefSponsor  sponsor;

    @BeforeEach
    void setUp() {
        loaLevel = TestFixtures.loaLevel(PROOFING_LEVEL);
        sponsor  = TestFixtures.sponsor(SPONSOR_ID, "COA");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Public entry point (two-arg overload): level/sponsor resolved from PersonVo
    // ──────────────────────────────────────────────────────────────────────────

    @Nested @DisplayName("Public entry point — determineVerificationMethod(PersonVo)")
    class PublicEntryPointTests {

        @Test
        void determineVerificationMethod_shouldReturnSupplier_whenConfigIsValid() {
            PersonVo pv = TestFixtures.standardPersonVo(SPONSOR_ID, "COA");
            when(refLoaLevelService.findByLevel(anyInt())).thenReturn(loaLevel);
            when(refSponsorService.findBySponsorName("COA")).thenReturn(sponsor);

            RpOtpAttemptConfig lnFull = TestFixtures.otpConfig(
                    RefOtpSupplier.LEXISNEXIS_RDP_OTP_SUPPLIER_ID, 0, 100, loaLevel, sponsor);
            when(otpAttemptConfigService.getByProofingLevel(anyLong(), anyLong())).thenReturn(List.of(lnFull));
            when(otpAttemptConfigService.getByProofingLevelSorted(anyList(), anyLong(), anyLong()))
                    .thenReturn(List.of(lnFull));

            RefOtpSupplier result = service.determineVerificationMethod(pv);

            assertNotNull(result);
            assertEquals(RefOtpSupplier.LEXISNEXIS_RDP_OTP_SUPPLIER_ID, result.getOtpSupplierId());
        }

        @Test
        void determineVerificationMethod_shouldSetErrorOnPersonVo_whenNoSupplierSelected() {
            PersonVo pv = TestFixtures.standardPersonVo(SPONSOR_ID, "COA");
            when(refLoaLevelService.findByLevel(anyInt())).thenReturn(loaLevel);
            when(refSponsorService.findBySponsorName("COA")).thenReturn(sponsor);
            when(otpAttemptConfigService.getByProofingLevel(anyLong(), anyLong()))
                    .thenReturn(Collections.emptyList());
            when(otpAttemptConfigService.getByProofingLevelSorted(anyList(), anyLong(), anyLong()))
                    .thenReturn(Collections.emptyList());

            RefOtpSupplier result = service.determineVerificationMethod(pv);

            assertNull(result);
            assertTrue(pv.isHasError());
            assertNotNull(pv.getErrorMessage());
        }

        @Test
        void determineVerificationMethod_shouldSetSupplierNameOnPersonVo_whenSupplierSelected() {
            PersonVo pv = TestFixtures.standardPersonVo(SPONSOR_ID, "COA");
            when(refLoaLevelService.findByLevel(anyInt())).thenReturn(loaLevel);
            when(refSponsorService.findBySponsorName("COA")).thenReturn(sponsor);

            RpOtpAttemptConfig cfg = TestFixtures.otpConfig(
                    RefOtpSupplier.EQUIFAX_IDFS_OTP_SUPPLIER_ID, 0, 100, loaLevel, sponsor);
            when(otpAttemptConfigService.getByProofingLevel(anyLong(), anyLong())).thenReturn(List.of(cfg));
            when(otpAttemptConfigService.getByProofingLevelSorted(anyList(), anyLong(), anyLong()))
                    .thenReturn(List.of(cfg));

            service.determineVerificationMethod(pv);

            assertNotNull(pv.getPhoneVerificationSupplierName());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 100% single-supplier configuration
    // ──────────────────────────────────────────────────────────────────────────

    @Nested @DisplayName("100% single-supplier configuration")
    class SingleSupplierConfigTests {

        @Test
        void determineVerificationMethod_shouldReturnLexisNexis_whenLexisNexisIsAt100Percent() throws Exception {
            PersonVo pv = TestFixtures.standardPersonVo(SPONSOR_ID, "COA");
            RpOtpAttemptConfig lnFull = TestFixtures.otpConfig(
                    RefOtpSupplier.LEXISNEXIS_RDP_OTP_SUPPLIER_ID, 0, 100, loaLevel, sponsor);

            when(otpAttemptConfigService.getByProofingLevel(anyLong(), anyLong())).thenReturn(List.of(lnFull));
            when(otpAttemptConfigService.getByProofingLevelSorted(anyList(), anyLong(), anyLong()))
                    .thenReturn(List.of(lnFull));

            RefOtpSupplier result = service.determineVerificationMethod(pv, loaLevel, sponsor);

            assertNotNull(result);
            assertEquals(RefOtpSupplier.LEXISNEXIS_RDP_OTP_SUPPLIER_ID, result.getOtpSupplierId());
        }

        @Test
        void determineVerificationMethod_shouldReturnEquifaxIdfs_whenEquifaxIdfsIsAt100Percent() throws Exception {
            PersonVo pv = TestFixtures.standardPersonVo(SPONSOR_ID, "COA");
            RpOtpAttemptConfig efxFull = TestFixtures.otpConfig(
                    RefOtpSupplier.EQUIFAX_IDFS_OTP_SUPPLIER_ID, 42, 100, loaLevel, sponsor);

            when(otpAttemptConfigService.getByProofingLevel(anyLong(), anyLong())).thenReturn(List.of(efxFull));
            when(otpAttemptConfigService.getByProofingLevelSorted(anyList(), anyLong(), anyLong()))
                    .thenReturn(List.of(efxFull));

            RefOtpSupplier result = service.determineVerificationMethod(pv, loaLevel, sponsor);

            assertEquals(RefOtpSupplier.EQUIFAX_IDFS_OTP_SUPPLIER_ID, result.getOtpSupplierId());
        }

        @Test
        void determineVerificationMethod_shouldNeverCallCallingOtp_whenSingleSupplierAt100Percent() throws Exception {
            PersonVo pv = TestFixtures.standardPersonVo(SPONSOR_ID, "COA");
            RpOtpAttemptConfig cfg = TestFixtures.otpConfig(
                    RefOtpSupplier.LEXISNEXIS_RDP_OTP_SUPPLIER_ID, 0, 100, loaLevel, sponsor);

            when(otpAttemptConfigService.getByProofingLevel(anyLong(), anyLong())).thenReturn(List.of(cfg));
            when(otpAttemptConfigService.getByProofingLevelSorted(anyList(), anyLong(), anyLong()))
                    .thenReturn(List.of(cfg));

            service.determineVerificationMethod(pv, loaLevel, sponsor);

            // THE KEY ASSERTION for the DB lock fix:
            // 100% config → isSwitchingConfigured=false → NO DB UPDATE must happen
            verify(otpAttemptConfigService, never()).callingOTP(any());
            verify(otpAttemptConfigService, never()).reset(anyLong(), anyLong());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Split-supplier configuration
    // ──────────────────────────────────────────────────────────────────────────

    @Nested @DisplayName("Split-supplier configuration")
    class SplitSupplierConfigTests {

        @Test
        void determineVerificationMethod_shouldSelectLexisNexis_whenItHasFewerAttemptsThanEquifax() throws Exception {
            PersonVo pv = TestFixtures.standardPersonVo(SPONSOR_ID, "COA");
            RpOtpAttemptConfig ln    = TestFixtures.otpConfig(RefOtpSupplier.LEXISNEXIS_RDP_OTP_SUPPLIER_ID, 10, 60, loaLevel, sponsor);
            RpOtpAttemptConfig equif = TestFixtures.otpConfig(RefOtpSupplier.EQUIFAX_IDFS_OTP_SUPPLIER_ID,    40, 40, loaLevel, sponsor);

            when(otpAttemptConfigService.getByProofingLevel(anyLong(), anyLong())).thenReturn(List.of(ln, equif));
            when(otpAttemptConfigService.getByProofingLevelSorted(anyList(), anyLong(), anyLong()))
                    .thenReturn(List.of(ln, equif)); // sorted ASC by attempts → LexisNexis first

            RefOtpSupplier result = service.determineVerificationMethod(pv, loaLevel, sponsor);

            assertEquals(RefOtpSupplier.LEXISNEXIS_RDP_OTP_SUPPLIER_ID, result.getOtpSupplierId());
        }

        @Test
        void determineVerificationMethod_shouldSelectEquifax_whenLexisNexisAtItsLimit() throws Exception {
            PersonVo pv = TestFixtures.standardPersonVo(SPONSOR_ID, "COA");
            RpOtpAttemptConfig ln    = TestFixtures.otpConfig(RefOtpSupplier.LEXISNEXIS_RDP_OTP_SUPPLIER_ID, 60, 60, loaLevel, sponsor);
            RpOtpAttemptConfig equif = TestFixtures.otpConfig(RefOtpSupplier.EQUIFAX_IDFS_OTP_SUPPLIER_ID,    38, 40, loaLevel, sponsor);

            when(otpAttemptConfigService.getByProofingLevel(anyLong(), anyLong())).thenReturn(List.of(ln, equif));
            when(otpAttemptConfigService.getByProofingLevelSorted(anyList(), anyLong(), anyLong()))
                    .thenReturn(List.of(equif, ln)); // Equifax (38) first because LexisNexis is at limit

            RefOtpSupplier result = service.determineVerificationMethod(pv, loaLevel, sponsor);

            assertEquals(RefOtpSupplier.EQUIFAX_IDFS_OTP_SUPPLIER_ID, result.getOtpSupplierId());
        }

        @Test
        void determineVerificationMethod_shouldCallCallingOtp_whenSplitConfigured() throws Exception {
            PersonVo pv = TestFixtures.standardPersonVo(SPONSOR_ID, "COA");
            RpOtpAttemptConfig ln    = TestFixtures.otpConfig(RefOtpSupplier.LEXISNEXIS_RDP_OTP_SUPPLIER_ID, 5, 60, loaLevel, sponsor);
            RpOtpAttemptConfig equif = TestFixtures.otpConfig(RefOtpSupplier.EQUIFAX_IDFS_OTP_SUPPLIER_ID,   5, 40, loaLevel, sponsor);

            when(otpAttemptConfigService.getByProofingLevel(anyLong(), anyLong())).thenReturn(List.of(ln, equif));
            when(otpAttemptConfigService.getByProofingLevelSorted(anyList(), anyLong(), anyLong()))
                    .thenReturn(List.of(ln, equif));

            service.determineVerificationMethod(pv, loaLevel, sponsor);

            verify(otpAttemptConfigService, times(1)).callingOTP(any(RpOtpAttemptConfig.class));
        }

        @Test
        void determineVerificationMethod_shouldFallbackToFirstEntry_whenBothSuppliersAreAtTheirLimit() throws Exception {
            PersonVo pv = TestFixtures.standardPersonVo(SPONSOR_ID, "COA");
            RpOtpAttemptConfig ln    = TestFixtures.otpConfig(RefOtpSupplier.LEXISNEXIS_RDP_OTP_SUPPLIER_ID, 60, 60, loaLevel, sponsor);
            RpOtpAttemptConfig equif = TestFixtures.otpConfig(RefOtpSupplier.EQUIFAX_IDFS_OTP_SUPPLIER_ID,   40, 40, loaLevel, sponsor);

            when(otpAttemptConfigService.getByProofingLevel(anyLong(), anyLong())).thenReturn(List.of(ln, equif));
            when(otpAttemptConfigService.getByProofingLevelSorted(anyList(), anyLong(), anyLong()))
                    .thenReturn(List.of(ln, equif));

            RefOtpSupplier result = service.determineVerificationMethod(pv, loaLevel, sponsor);

            // Falls back to list.get(0) = LexisNexis
            assertNotNull(result);
            assertEquals(RefOtpSupplier.LEXISNEXIS_RDP_OTP_SUPPLIER_ID, result.getOtpSupplierId());
            // Reset must be triggered because attempts >= totalAttempts
            verify(otpAttemptConfigService, atLeastOnce()).reset(anyLong(), anyLong());
        }

        @Test
        void determineVerificationMethod_shouldCallCallingOtpWithCorrectConfig_whenLexisNexisSelected() throws Exception {
            PersonVo pv = TestFixtures.standardPersonVo(SPONSOR_ID, "COA");
            RpOtpAttemptConfig ln    = TestFixtures.otpConfig(RefOtpSupplier.LEXISNEXIS_RDP_OTP_SUPPLIER_ID, 5,  60, loaLevel, sponsor);
            RpOtpAttemptConfig equif = TestFixtures.otpConfig(RefOtpSupplier.EQUIFAX_IDFS_OTP_SUPPLIER_ID,   35, 40, loaLevel, sponsor);

            when(otpAttemptConfigService.getByProofingLevel(anyLong(), anyLong())).thenReturn(List.of(ln, equif));
            when(otpAttemptConfigService.getByProofingLevelSorted(anyList(), anyLong(), anyLong()))
                    .thenReturn(List.of(ln, equif));

            service.determineVerificationMethod(pv, loaLevel, sponsor);

            ArgumentCaptor<RpOtpAttemptConfig> captor = ArgumentCaptor.forClass(RpOtpAttemptConfig.class);
            verify(otpAttemptConfigService).callingOTP(captor.capture());
            assertEquals(RefOtpSupplier.LEXISNEXIS_RDP_OTP_SUPPLIER_ID,
                    captor.getValue().getRefOtpSupplier().getOtpSupplierId());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Zero-percent supplier (totalAttempts = 0) excluded by NamedQuery
    // ──────────────────────────────────────────────────────────────────────────

    @Nested @DisplayName("Zero-percent supplier is excluded by NamedQuery")
    class ZeroPercentTests {

        @Test
        void determineVerificationMethod_shouldSelectActiveSupplier_whenZeroPercentOnesAreFilteredByQuery()
                throws Exception {
            PersonVo pv = TestFixtures.standardPersonVo(SPONSOR_ID, "COA");
            // The NamedQuery has "AND totalAttempts <> 0" so 0% suppliers never appear
            RpOtpAttemptConfig activeOnly = TestFixtures.otpConfig(
                    RefOtpSupplier.LEXISNEXIS_RDP_OTP_SUPPLIER_ID, 0, 100, loaLevel, sponsor);

            when(otpAttemptConfigService.getByProofingLevel(anyLong(), anyLong()))
                    .thenReturn(List.of(activeOnly));
            when(otpAttemptConfigService.getByProofingLevelSorted(anyList(), anyLong(), anyLong()))
                    .thenReturn(List.of(activeOnly));

            RefOtpSupplier result = service.determineVerificationMethod(pv, loaLevel, sponsor);

            assertNotNull(result);
            assertEquals(RefOtpSupplier.LEXISNEXIS_RDP_OTP_SUPPLIER_ID, result.getOtpSupplierId());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // No eligible supplier
    // ──────────────────────────────────────────────────────────────────────────

    @Nested @DisplayName("No eligible supplier")
    class NoEligibleSupplierTests {

        @Test
        void determineVerificationMethod_shouldReturnNull_whenSortedListIsEmpty() throws Exception {
            PersonVo pv = TestFixtures.standardPersonVo(SPONSOR_ID, "COA");
            when(otpAttemptConfigService.getByProofingLevel(anyLong(), anyLong()))
                    .thenReturn(Collections.emptyList());
            when(otpAttemptConfigService.getByProofingLevelSorted(anyList(), anyLong(), anyLong()))
                    .thenReturn(Collections.emptyList());

            RefOtpSupplier result = service.determineVerificationMethod(pv, loaLevel, sponsor);

            assertNull(result);
            verify(otpAttemptConfigService, never()).callingOTP(any());
        }

        @Test
        void determineVerificationMethod_shouldReturnNull_whenSortedListIsNull() throws Exception {
            PersonVo pv = TestFixtures.standardPersonVo(SPONSOR_ID, "COA");
            when(otpAttemptConfigService.getByProofingLevel(anyLong(), anyLong()))
                    .thenReturn(Collections.emptyList());
            when(otpAttemptConfigService.getByProofingLevelSorted(anyList(), anyLong(), anyLong()))
                    .thenReturn(null);

            RefOtpSupplier result = service.determineVerificationMethod(pv, loaLevel, sponsor);

            assertNull(result);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Excluded supplier — failover
    // ──────────────────────────────────────────────────────────────────────────

    @Nested @DisplayName("Excluded supplier — failover")
    class ExcludedSupplierTests {

        @Test
        void determineVerificationMethod_shouldExcludeLexisNexisFromList_whenPassedAsExcludedSupplierId()
                throws Exception {
            PersonVo pv = TestFixtures.standardPersonVo(SPONSOR_ID, "COA");
            RpOtpAttemptConfig equif = TestFixtures.otpConfig(
                    RefOtpSupplier.EQUIFAX_IDFS_OTP_SUPPLIER_ID, 0, 100, loaLevel, sponsor);

            when(otpAttemptConfigService.getByProofingLevel(anyLong(), anyLong())).thenReturn(List.of(equif));
            when(otpAttemptConfigService.getByProofingLevelSorted(anyList(), anyLong(), anyLong()))
                    .thenReturn(List.of(equif));

            RefOtpSupplier result = service.determineVerificationMethod(
                    pv, loaLevel, sponsor, RefOtpSupplier.LEXISNEXIS_RDP_OTP_SUPPLIER_ID);

            assertNotNull(result);
            assertEquals(RefOtpSupplier.EQUIFAX_IDFS_OTP_SUPPLIER_ID, result.getOtpSupplierId());

            // Verify LexisNexis was removed before the query
            ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
            verify(otpAttemptConfigService).getByProofingLevelSorted(captor.capture(), anyLong(), anyLong());
            assertFalse(captor.getValue().contains(RefOtpSupplier.LEXISNEXIS_RDP_OTP_SUPPLIER_ID));
        }

        @Test
        void determineVerificationMethod_shouldPassAllFourIds_whenExcludedSupplierIdIsZero() throws Exception {
            PersonVo pv = TestFixtures.standardPersonVo(SPONSOR_ID, "COA");
            RpOtpAttemptConfig lnFull = TestFixtures.otpConfig(
                    RefOtpSupplier.LEXISNEXIS_RDP_OTP_SUPPLIER_ID, 0, 100, loaLevel, sponsor);

            when(otpAttemptConfigService.getByProofingLevel(anyLong(), anyLong())).thenReturn(List.of(lnFull));
            when(otpAttemptConfigService.getByProofingLevelSorted(anyList(), anyLong(), anyLong()))
                    .thenReturn(List.of(lnFull));

            service.determineVerificationMethod(pv, loaLevel, sponsor, 0L);

            ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
            verify(otpAttemptConfigService).getByProofingLevelSorted(captor.capture(), anyLong(), anyLong());
            List<Long> ids = captor.getValue();
            assertTrue(ids.contains(RefOtpSupplier.LEXISNEXIS_RDP_OTP_SUPPLIER_ID));
            assertTrue(ids.contains(RefOtpSupplier.EQUIFAX_IDFS_OTP_SUPPLIER_ID));
            assertTrue(ids.contains(RefOtpSupplier.EQUIFAX_DIT_SMFA_SUPPLIER_ID));
            assertTrue(ids.contains(RefOtpSupplier.EXPERIAN_CROSSCORE_SUPPLIER_ID));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // INF path — LexisNexis IndividualNotFound
    // ──────────────────────────────────────────────────────────────────────────

    @Nested @DisplayName("INF path — LexisNexis IndividualNotFound")
    class LexisNexisInfTests {

        @Test
        void determineVerificationMethod_shouldUseInfTable_whenLexisNexisIndividualNotFoundIsTrue()
                throws Exception {
            PersonVo pv = TestFixtures.standardPersonVo(SPONSOR_ID, "COA");
            pv.setLexisNexisIndividualNotFound(true);

            RpInfPvAttemptConfig efxInf = TestFixtures.infConfig(
                    RefOtpSupplier.EQUIFAX_IDFS_OTP_SUPPLIER_ID, 0, 60, loaLevel, sponsor);

            when(rpInfPvAttemptConfigService.getByProofingLevel(anyLong(), anyLong()))
                    .thenReturn(List.of(efxInf));
            when(rpInfPvAttemptConfigService.getByProofingLevelSorted(anyList(), anyLong(), anyLong()))
                    .thenReturn(List.of(efxInf));
            when(refSponsorConfigurationService.getConfigRecord(anyInt(), eq("Test.Supplier.Options")))
                    .thenReturn(null);

            RefOtpSupplier result = service.determineVerificationMethod(pv, loaLevel, sponsor);

            assertNotNull(result);
            // Must use INF service, NOT the standard OTP service
            verify(rpInfPvAttemptConfigService).getByProofingLevelSorted(anyList(), anyLong(), anyLong());
            verify(otpAttemptConfigService, never()).getByProofingLevelSorted(any(), anyLong(), anyLong());
        }

        @Test
        void determineVerificationMethod_shouldExcludeLexisNexisFromInfQuery_whenIndividualNotFound()
                throws Exception {
            PersonVo pv = TestFixtures.standardPersonVo(SPONSOR_ID, "COA");
            pv.setLexisNexisIndividualNotFound(true);

            RpInfPvAttemptConfig efxInf = TestFixtures.infConfig(
                    RefOtpSupplier.EQUIFAX_IDFS_OTP_SUPPLIER_ID, 0, 40, loaLevel, sponsor);

            when(rpInfPvAttemptConfigService.getByProofingLevel(anyLong(), anyLong()))
                    .thenReturn(List.of(efxInf));
            when(rpInfPvAttemptConfigService.getByProofingLevelSorted(anyList(), anyLong(), anyLong()))
                    .thenReturn(List.of(efxInf));
            when(refSponsorConfigurationService.getConfigRecord(anyInt(), anyString())).thenReturn(null);

            service.determineVerificationMethod(pv, loaLevel, sponsor);

            ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
            verify(rpInfPvAttemptConfigService).getByProofingLevelSorted(captor.capture(), anyLong(), anyLong());
            assertFalse(captor.getValue().contains(RefOtpSupplier.LEXISNEXIS_RDP_OTP_SUPPLIER_ID),
                    "LexisNexis must be excluded from INF query when individualNotFound=true");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // CNF path — Experian CustomerNotOnFile
    // ──────────────────────────────────────────────────────────────────────────

    @Nested @DisplayName("CNF path — Experian CustomerNotOnFile")
    class ExperianCnfTests {

        @Test
        void determineVerificationMethod_shouldReturnNull_whenCnfSetAndSelectedSupplierIsExperian()
                throws Exception {
            PersonVo pv = TestFixtures.standardPersonVo(SPONSOR_ID, "COA");
            pv.setExperianCustomerNotOnFile(true);

            RpInfPvAttemptConfig expInf = TestFixtures.infConfig(
                    RefOtpSupplier.EXPERIAN_CROSSCORE_SUPPLIER_ID, 0, 100, loaLevel, sponsor);
            expInf.getRefOtpSupplier().setExperianPhone(true);

            when(rpInfPvAttemptConfigService.getByProofingLevel(anyLong(), anyLong()))
                    .thenReturn(List.of(expInf));
            when(rpInfPvAttemptConfigService.getByProofingLevelSorted(anyList(), anyLong(), anyLong()))
                    .thenReturn(List.of(expInf));
            when(refSponsorConfigurationService.getConfigRecord(anyInt(), anyString())).thenReturn(null);

            RefOtpSupplier result = service.determineVerificationMethod(pv, loaLevel, sponsor);

            assertNull(result,
                    "Must return null when CNF is set and selected supplier is Experian (isExperianPhone=true)");
        }

        @Test
        void determineVerificationMethod_shouldExcludeExperian_whenCnfIsSet() throws Exception {
            PersonVo pv = TestFixtures.standardPersonVo(SPONSOR_ID, "COA");
            pv.setExperianCustomerNotOnFile(true);

            RpInfPvAttemptConfig efxInf = TestFixtures.infConfig(
                    RefOtpSupplier.EQUIFAX_IDFS_OTP_SUPPLIER_ID, 0, 60, loaLevel, sponsor);

            when(rpInfPvAttemptConfigService.getByProofingLevel(anyLong(), anyLong()))
                    .thenReturn(List.of(efxInf));
            when(rpInfPvAttemptConfigService.getByProofingLevelSorted(anyList(), anyLong(), anyLong()))
                    .thenReturn(List.of(efxInf));
            when(refSponsorConfigurationService.getConfigRecord(anyInt(), anyString())).thenReturn(null);

            service.determineVerificationMethod(pv, loaLevel, sponsor);

            ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
            verify(rpInfPvAttemptConfigService).getByProofingLevelSorted(captor.capture(), anyLong(), anyLong());
            assertFalse(captor.getValue().contains(RefOtpSupplier.EXPERIAN_CROSSCORE_SUPPLIER_ID),
                    "Experian must be excluded from query when customerNotOnFile=true");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Experian error response (non-INF path)
    // ──────────────────────────────────────────────────────────────────────────

    @Nested @DisplayName("Experian error response")
    class ExperianErrorTests {

        @Test
        void determineVerificationMethod_shouldReturnNull_whenExperianErrorAndSingleSupplierConfigured()
                throws Exception {
            PersonVo pv = TestFixtures.standardPersonVo(SPONSOR_ID, "COA");
            pv.setExperianErrorResponse(true);

            // 100% Experian → isSwitchingConfigured=false → return null immediately
            RpOtpAttemptConfig expFull = TestFixtures.otpConfig(
                    RefOtpSupplier.EXPERIAN_CROSSCORE_SUPPLIER_ID, 0, 100, loaLevel, sponsor);
            when(otpAttemptConfigService.getByProofingLevel(anyLong(), anyLong())).thenReturn(List.of(expFull));

            RefOtpSupplier result = service.determineVerificationMethod(pv, loaLevel, sponsor);

            assertNull(result);
            verify(otpAttemptConfigService, never()).getByProofingLevelSorted(any(), anyLong(), anyLong());
        }

        @Test
        void determineVerificationMethod_shouldExcludeExperian_whenExperianErrorAndSwitchingConfigured()
                throws Exception {
            PersonVo pv = TestFixtures.standardPersonVo(SPONSOR_ID, "COA");
            pv.setExperianErrorResponse(true);

            RpOtpAttemptConfig ln  = TestFixtures.otpConfig(RefOtpSupplier.LEXISNEXIS_RDP_OTP_SUPPLIER_ID, 0, 60, loaLevel, sponsor);
            RpOtpAttemptConfig exp = TestFixtures.otpConfig(RefOtpSupplier.EXPERIAN_CROSSCORE_SUPPLIER_ID,  0, 40, loaLevel, sponsor);

            when(otpAttemptConfigService.getByProofingLevel(anyLong(), anyLong())).thenReturn(List.of(ln, exp));
            when(otpAttemptConfigService.getByProofingLevelSorted(anyList(), anyLong(), anyLong()))
                    .thenReturn(List.of(ln)); // Experian already removed before query

            RefOtpSupplier result = service.determineVerificationMethod(pv, loaLevel, sponsor);

            assertNotNull(result);
            assertEquals(RefOtpSupplier.LEXISNEXIS_RDP_OTP_SUPPLIER_ID, result.getOtpSupplierId());

            ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
            verify(otpAttemptConfigService).getByProofingLevelSorted(captor.capture(), anyLong(), anyLong());
            assertFalse(captor.getValue().contains(RefOtpSupplier.EXPERIAN_CROSSCORE_SUPPLIER_ID));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Proof-of-fix: no DB write for 100% config across N transactions
    // ──────────────────────────────────────────────────────────────────────────

    @Nested @DisplayName("Proof-of-fix — DB lock eliminated")
    class ProofOfFixTests {

        @Test
        void determineVerificationMethod_shouldNeverCallCallingOtp_across10Calls_when100PercentConfig()
                throws Exception {
            RpOtpAttemptConfig lnFull = TestFixtures.otpConfig(
                    RefOtpSupplier.LEXISNEXIS_RDP_OTP_SUPPLIER_ID, 0, 100, loaLevel, sponsor);
            when(otpAttemptConfigService.getByProofingLevel(anyLong(), anyLong())).thenReturn(List.of(lnFull));
            when(otpAttemptConfigService.getByProofingLevelSorted(anyList(), anyLong(), anyLong()))
                    .thenReturn(List.of(lnFull));

            for (int i = 0; i < 10; i++) {
                service.determineVerificationMethod(
                        TestFixtures.standardPersonVo(SPONSOR_ID, "COA"), loaLevel, sponsor);
            }

            // Zero DB write interactions across all 10 calls
            verify(otpAttemptConfigService, never()).callingOTP(any());
            verify(otpAttemptConfigService, never()).reset(anyLong(), anyLong());
        }
    }
}
JAVAEOF
echo "done"
Output

done
