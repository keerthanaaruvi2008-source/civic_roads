package com.roaddamage.governance;

/**
 * Quality audit and payment authorization milestones.
 */
public enum AuditApprovalStatus {

    /** Standard ticket where random sample was not picked. */
    NOT_REQUIRED("Not Required"),

    /** Contractor submitted requisition awaiting physical site progress & material endorsement by Field Surveyor. */
    PENDING_SURVEYOR_ENDORSEMENT("Pending Surveyor Endorsement"),

    /** Field Surveyor verified site execution and recommended funding to Supervisor/Auditor. */
    SURVEYOR_ENDORSED("Surveyor Endorsed - Awaiting Audit"),

    /** Flagged by statistical sampling awaiting independent quality verification. */
    PENDING("Audit Pending"),

    /** Quality Auditor certified site workmanship and approved payment release. */
    APPROVED("Audit Approved"),

    /** Quality Auditor rejected site workmanship requiring rework. */
    REJECTED("Audit Rejected (Rework Required)");

    private final String label;

    AuditApprovalStatus(String label) {
        this.label = label;
    }

    /**
     * Display label for UI representation.
     *
     * @return readable status string
     */
    public String label() {
        return label;
    }
}
