package com.roaddamage.governance;

/**
 * Sequential lifecycle progression states for a governance ticket.
 */
public enum TicketStatus {

    /** Freshly submitted citizen report awaiting nodal triage. */
    REPORTED("Reported", "Awaiting departmental assignment and triage"),

    /** Formally assigned to a specific road agency and contractor. */
    ASSIGNED("Assigned", "Work order issued to contractor"),

    /** Site operations underway with repair crew deployed. */
    IN_PROGRESS("In Progress", "Physical repair and material application underway"),

    /** Field work finished with before/after photo proofs submitted. */
    COMPLETED("Completed", "Work finished; pending quality verification"),

    /** Audited and signed off for payment release and closed in registry. */
    RESOLVED("Resolved", "Audit approved, payment released, and closed");

    private final String displayName;
    private final String description;

    TicketStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * Human-readable state name.
     *
     * @return display label
     */
    public String displayName() {
        return displayName;
    }

    /**
     * Explanation of state meaning.
     *
     * @return description
     */
    public String description() {
        return description;
    }
}
