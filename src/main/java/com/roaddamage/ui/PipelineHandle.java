package com.roaddamage.ui;

/**
 * Abstraction interface representing an asynchronous inspection pipeline session.
 * <p>
 * Decouples the UI controller from the Phase 6 pipeline implementation.
 */
public interface PipelineHandle extends AutoCloseable {

    /**
     * Halts pipeline processing and terminates worker threads.
     */
    void stop();

    /**
     * Indicates whether the pipeline is currently active.
     *
     * @return true if active, false otherwise
     */
    boolean isRunning();

    /**
     * Closes the pipeline session. Delegates to {@link #stop()}.
     */
    @Override
    default void close() {
        stop();
    }
}
