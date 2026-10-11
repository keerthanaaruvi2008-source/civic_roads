package com.roaddamage.governance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Staff Authentication & Role Assignment Tests")
class StaffAuthTest {

    @ParameterizedTest
    @CsvSource({
            "admin@pwd.gov, pwd123, NODAL_OFFICER, false",
            "ADMIN@PWD.GOV, admin, NODAL_OFFICER, false",
            "site@contractor.in, site123, SITE_ENGINEER, false",
            "SITE@CONTRACTOR.IN, 123456, SITE_ENGINEER, false",
            "audit@nhai.gov, audit123, QUALITY_AUDITOR, false",
            "AUDIT@NHAI.GOV, password, QUALITY_AUDITOR, false",
            "ai@inspector.gov, ai123, CITIZEN, true",
            " AI@INSPECTOR.GOV , admin, CITIZEN, true"
    })
    @DisplayName("Should authenticate predefined credentials with designated roles and permissions")
    void testPredefinedAuthenticationSuccess(String email, String password, UserRole expectedRole, boolean canInspect) {
        Optional<StaffAuthService.StaffPrincipal> principalOpt = StaffAuthService.authenticate(email, password);

        assertTrue(principalOpt.isPresent(), "Expected successful authentication for: " + email);
        StaffAuthService.StaffPrincipal principal = principalOpt.get();

        assertEquals(email.toLowerCase().trim(), principal.email());
        assertEquals(expectedRole, principal.role());
        assertEquals(canInspect, principal.canAccessInspection());
        assertNotNull(principal.fullName());
        assertNotNull(principal.agency());
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrongpass", "1111", "invalid", " "})
    @DisplayName("Should reject valid accounts with invalid passwords")
    void testInvalidPasswordsRejected(String wrongPassword) {
        Optional<StaffAuthService.StaffPrincipal> opt = StaffAuthService.authenticate("admin@pwd.gov", wrongPassword);
        assertTrue(opt.isEmpty(), "Should fail authentication with password: " + wrongPassword);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown@gov.in", "fake@contractor.com", "hacker@domain.com"})
    @DisplayName("Should reject unknown email addresses")
    void testUnknownAccountsRejected(String unknownEmail) {
        Optional<StaffAuthService.StaffPrincipal> opt = StaffAuthService.authenticate(unknownEmail, "admin");
        assertTrue(opt.isEmpty(), "Should not authenticate unknown account: " + unknownEmail);
    }

    @Test
    @DisplayName("Should handle null and empty inputs safely")
    void testNullAndEmptyInputs() {
        assertTrue(StaffAuthService.authenticate(null, "pwd123").isEmpty());
        assertTrue(StaffAuthService.authenticate("", "pwd123").isEmpty());
        assertTrue(StaffAuthService.authenticate("   ", "pwd123").isEmpty());
        assertTrue(StaffAuthService.authenticate("admin@pwd.gov", null).isEmpty());
        assertTrue(StaffAuthService.authenticate("admin@pwd.gov", "").isEmpty());
        assertTrue(StaffAuthService.authenticate("admin@pwd.gov", "   ").isEmpty());
    }

    @Test
    @DisplayName("Should list all 5 predefined accounts")
    void testPredefinedAccountsList() {
        List<StaffAuthService.StaffPrincipal> accounts = StaffAuthService.getPredefinedAccounts();
        assertEquals(5, accounts.size());
        assertTrue(accounts.stream().anyMatch(a -> a.email().equals("admin@pwd.gov")));
        assertTrue(accounts.stream().anyMatch(a -> a.email().equals("site@contractor.in")));
        assertTrue(accounts.stream().anyMatch(a -> a.email().equals("surveyor@pwd.gov")));
        assertTrue(accounts.stream().anyMatch(a -> a.email().equals("audit@nhai.gov")));
        assertTrue(accounts.stream().anyMatch(a -> a.email().equals("ai@inspector.gov")));
    }

    @Test
    @DisplayName("Should find accounts by email ignoring case and trimming")
    void testFindByEmail() {
        assertTrue(StaffAuthService.findByEmail("ADMIN@PWD.GOV ").isPresent());
        assertTrue(StaffAuthService.findByEmail("site@contractor.in").isPresent());
        assertFalse(StaffAuthService.findByEmail("nonexistent@domain.com").isPresent());
        assertFalse(StaffAuthService.findByEmail(null).isPresent());
    }

    @Test
    @DisplayName("StaffPrincipal record should reject null constructor arguments")
    void testPrincipalRecordValidation() {
        assertThrows(NullPointerException.class, () ->
                new StaffAuthService.StaffPrincipal(null, "Name", UserRole.CITIZEN, false, "Agency")
        );
        assertThrows(NullPointerException.class, () ->
                new StaffAuthService.StaffPrincipal("email", null, UserRole.CITIZEN, false, "Agency")
        );
        assertThrows(NullPointerException.class, () ->
                new StaffAuthService.StaffPrincipal("email", "Name", null, false, "Agency")
        );
        assertThrows(NullPointerException.class, () ->
                new StaffAuthService.StaffPrincipal("email", "Name", UserRole.CITIZEN, false, null)
        );
    }
}
