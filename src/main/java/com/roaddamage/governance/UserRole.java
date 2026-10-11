package com.roaddamage.governance;

/**
 * Stakeholder roles participating in the Road Damage Governance workflow.
 */
public enum UserRole {

    /** Public road users submitting distress reports and tracking remediation progress. */
    CITIZEN("Citizen", "Report damage & track public grievances"),

    /** Departmental triage administrators assigning jurisdictions and enforcing defect liability warranties. */
    NODAL_OFFICER("Nodal Officer", "Triage tickets, assign jurisdictions & verify DLP warranties"),

    /** Field contracting engineers executing physical repairs, ordering materials, and submitting photo proofs. */
    SITE_ENGINEER("Site Engineer", "Execute repairs, request materials & upload before/after proofs"),

    /** PWD field surveyors inspecting physical site progress and endorsing material & cost funding. */
    SURVEYOR("Field Surveyor", "Inspect physical milestones, verify materials & endorse funding"),

    /** Independent audit and finance officers reviewing random samples and approving digital payment sign-offs. */
    QUALITY_AUDITOR("Quality Auditor", "Audit random sample proofs, monitor SLAs & sign off payments");

    private final String displayName;
    private final String description;

    UserRole(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * User-facing role name for UI selectors.
     *
     * @return display label
     */
    public String displayName() {
        return displayName;
    }

    /**
     * Operational mandate description for the role.
     *
     * @return summary description
     */
    public String description() {
        return description;
    }
}
