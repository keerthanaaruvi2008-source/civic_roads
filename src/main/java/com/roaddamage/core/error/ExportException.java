package com.roaddamage.core.error;

import java.util.Collections;
import java.util.Map;

/**
 * Exception thrown when inspection report export or serialization fails.
 */
@SuppressWarnings("serial")
public final class ExportException extends RoadDamageException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs an ExportException with component and message.
     *
     * @param component the subsystem or component identifier
     * @param message the detail error message
     */
    public ExportException(String component, String message) {
        super(component, message, null, Collections.emptyMap());
    }

    /**
     * Constructs an ExportException with component, message, and cause.
     *
     * @param component the subsystem or component identifier
     * @param message the detail error message
     * @param cause the underlying cause
     */
    public ExportException(String component, String message, Throwable cause) {
        super(component, message, cause, Collections.emptyMap());
    }

    /**
     * Constructs an ExportException with component, message, cause, and contextual attributes.
     *
     * @param component the subsystem or component identifier
     * @param message the detail error message
     * @param cause the underlying cause
     * @param context contextual diagnostics map, made unmodifiable
     */
    public ExportException(String component, String message, Throwable cause, Map<String, Object> context) {
        super(component, message, cause, context);
    }
}
