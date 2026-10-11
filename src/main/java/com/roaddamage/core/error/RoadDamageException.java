package com.roaddamage.core.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Base sealed runtime exception for all failures occurring within the road damage detection framework.
 */
@SuppressWarnings("serial")
public sealed class RoadDamageException extends RuntimeException
        permits IngestionException, InferenceException, SeverityException, ExportException {

    private static final long serialVersionUID = 1L;

    private final String component;
    private final Map<String, Object> context;

    /**
     * Constructs a new RoadDamageException with component and message.
     *
     * @param component the subsystem or component identifier
     * @param message the detail error message
     */
    public RoadDamageException(String component, String message) {
        this(component, message, null, Collections.emptyMap());
    }

    /**
     * Constructs a new RoadDamageException with component, message, and cause.
     *
     * @param component the subsystem or component identifier
     * @param message the detail error message
     * @param cause the underlying cause
     */
    public RoadDamageException(String component, String message, Throwable cause) {
        this(component, message, cause, Collections.emptyMap());
    }

    /**
     * Constructs a new RoadDamageException with component, message, cause, and contextual attributes.
     *
     * @param component the subsystem or component identifier
     * @param message the detail error message
     * @param cause the underlying cause
     * @param context contextual diagnostics map, made unmodifiable
     */
    public RoadDamageException(String component, String message, Throwable cause, Map<String, Object> context) {
        super(message, cause);
        this.component = Objects.requireNonNull(component, "component must not be null");
        this.context = context == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(context));
    }

    /**
     * Returns the component where the error originated.
     *
     * @return the component identifier
     */
    public String component() {
        return component;
    }

    /**
     * Returns the component where the error originated.
     *
     * @return the component identifier
     */
    public String getComponent() {
        return component;
    }

    /**
     * Returns the immutable diagnostic context map.
     *
     * @return unmodifiable map of context key-value pairs
     */
    public Map<String, Object> context() {
        return context;
    }

    /**
     * Returns the immutable diagnostic context map.
     *
     * @return unmodifiable map of context key-value pairs
     */
    public Map<String, Object> getContext() {
        return context;
    }
}
