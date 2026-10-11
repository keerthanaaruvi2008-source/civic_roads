package com.roaddamage.governance;

/**
 * Domain exception thrown for invalid state machine transitions, role violations, or policy failures.
 */
public class GovernanceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a governance exception with an informative message.
     *
     * @param message failure explanation
     */
    public GovernanceException(String message) {
        super(message);
    }

    /**
     * Constructs a governance exception with an informative message and underlying cause.
     *
     * @param message failure explanation
     * @param cause underlying root cause
     */
    public GovernanceException(String message, Throwable cause) {
        super(message, cause);
    }
}
