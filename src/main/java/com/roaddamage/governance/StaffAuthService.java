package com.roaddamage.governance;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Access control and credential authentication service for municipal and highway staff portals.
 */
public final class StaffAuthService {

    /**
     * Authenticated staff principal holding role and capability permissions.
     */
    public record StaffPrincipal(
            String email,
            String fullName,
            UserRole role,
            boolean canAccessInspection,
            String agency) {

        public StaffPrincipal {
            Objects.requireNonNull(email, "email must not be null");
            Objects.requireNonNull(fullName, "fullName must not be null");
            Objects.requireNonNull(role, "role must not be null");
            Objects.requireNonNull(agency, "agency must not be null");
        }
    }

    private static final Map<String, StaffPrincipal> PREDEFINED_ACCOUNTS = new LinkedHashMap<>();
    private static final Map<String, String> PASSWORDS = new LinkedHashMap<>();

    static {
        registerAccount(
                "admin@pwd.gov",
                "PWD Administrator",
                UserRole.NODAL_OFFICER,
                false,
                "State Highway PWD",
                "pwd123"
        );
        registerAccount(
                "site@contractor.in",
                "Apex Chief Site Engineer",
                UserRole.SITE_ENGINEER,
                false,
                "Apex Infra Roadways Ltd",
                "site123"
        );
        registerAccount(
                "audit@nhai.gov",
                "National Quality Auditor",
                UserRole.QUALITY_AUDITOR,
                false,
                "National Highways Authority of India",
                "audit123"
        );
        registerAccount(
                "surveyor@pwd.gov",
                "Divisional Field Surveyor",
                UserRole.SURVEYOR,
                false,
                "State PWD Survey Wing",
                "survey123"
        );
        registerAccount(
                "ai@inspector.gov",
                "AI Computer Vision Specialist",
                UserRole.CITIZEN, // Uses Inspection Engine
                true,
                "National Highway Inspection Wing",
                "ai123"
        );
    }

    private static void registerAccount(
            String email,
            String fullName,
            UserRole role,
            boolean canAccessInspection,
            String agency,
            String defaultPassword) {

        String key = email.toLowerCase().trim();
        PREDEFINED_ACCOUNTS.put(key, new StaffPrincipal(key, fullName, role, canAccessInspection, agency));
        PASSWORDS.put(key, defaultPassword);
    }

    /**
     * Authenticates a staff user by email address and password.
     * For developer demonstration, matches predefined passwords or standard fallbacks ("admin", "password", "123456").
     *
     * @param email login email address
     * @param password input secret
     * @return populated principal if valid, empty otherwise
     */
    public static Optional<StaffPrincipal> authenticate(String email, String password) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }

        String key = email.toLowerCase().trim();
        StaffPrincipal principal = PREDEFINED_ACCOUNTS.get(key);
        if (principal == null) {
            return Optional.empty();
        }

        // Allow configured password or standard demo passwords
        String expectedPassword = PASSWORDS.get(key);
        if (password == null || password.isBlank()) {
            return Optional.empty();
        }

        String cleanPass = password.trim();
        if (cleanPass.equals(expectedPassword) || cleanPass.equals("admin") || cleanPass.equals("password") || cleanPass.equals("123456")) {
            return Optional.of(principal);
        }

        return Optional.empty();
    }

    /**
     * Returns an unmodifiable list of all registered staff accounts for quick UI login selectors.
     *
     * @return list of principals
     */
    public static List<StaffPrincipal> getPredefinedAccounts() {
        return Collections.unmodifiableList(List.copyOf(PREDEFINED_ACCOUNTS.values()));
    }

    /**
     * Retrieves account principal by email if present.
     *
     * @param email user email
     * @return principal optional
     */
    public static Optional<StaffPrincipal> findByEmail(String email) {
        if (email == null) return Optional.empty();
        return Optional.ofNullable(PREDEFINED_ACCOUNTS.get(email.toLowerCase().trim()));
    }
}
