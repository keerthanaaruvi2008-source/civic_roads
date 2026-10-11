package com.roaddamage.pipeline;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe telemetry tracking throughput, frame dropping, stage bypasses, and inference latency.
 */
public final class PipelineMetrics {

    private final long startNanos;
    private final LongAdder framesIn = new LongAdder();
    private final LongAdder framesOut = new LongAdder();
    private final LongAdder framesSkippedStage2 = new LongAdder();
    private final LongAdder errors = new LongAdder();
    private final LongAdder droppedFrames = new LongAdder();

    private final AtomicLong maxStage1Nanos = new AtomicLong(0L);
    private final AtomicLong maxStage2Nanos = new AtomicLong(0L);
    private final AtomicLong maxStage3Nanos = new AtomicLong(0L);
    private final AtomicLong maxTotalNanos = new AtomicLong(0L);

    /**
     * Constructs a PipelineMetrics instance starting the elapsed time stopwatch.
     */
    public PipelineMetrics() {
        this.startNanos = System.nanoTime();
    }

    /**
     * Records ingestion of a frame into the pipeline queue.
     */
    public void recordFrameIn() {
        framesIn.increment();
    }

    /**
     * Records successful emission of a frame evaluation result.
     */
    public void recordFrameOut() {
        framesOut.increment();
    }

    /**
     * Records a frame that bypassed Stage 2 and Stage 3 because Stage 1 found no distress.
     */
    public void recordSkippedStage2() {
        framesSkippedStage2.increment();
    }

    /**
     * Records an internal non-fatal stage processing error.
     */
    public void recordError() {
        errors.increment();
    }

    /**
     * Records a frame dropped due to buffer back-pressure queue overflow.
     */
    public void recordDroppedFrame() {
        droppedFrames.increment();
    }

    /**
     * Records execution durations of individual pipeline stages.
     *
     * @param s1 Stage 1 screening duration in nanoseconds
     * @param s2 Stage 2 detection duration in nanoseconds
     * @param s3 Stage 3 severity assessment duration in nanoseconds
     */
    public void recordStageDurations(long s1, long s2, long s3) {
        maxStage1Nanos.accumulateAndGet(s1, Math::max);
        maxStage2Nanos.accumulateAndGet(s2, Math::max);
        maxStage3Nanos.accumulateAndGet(s3, Math::max);
        maxTotalNanos.accumulateAndGet(s1 + s2 + s3, Math::max);
    }

    public long maxStage1Nanos() { return maxStage1Nanos.get(); }
    public long maxStage2Nanos() { return maxStage2Nanos.get(); }
    public long maxStage3Nanos() { return maxStage3Nanos.get(); }
    public long maxTotalNanos() { return maxTotalNanos.get(); }

    /**
     * Captures an immutable telemetry snapshot.
     *
     * @return current metrics snapshot
     */
    public Snapshot snapshot() {
        long elapsed = System.nanoTime() - startNanos;
        return new Snapshot(
                framesIn.sum(),
                framesOut.sum(),
                framesSkippedStage2.sum(),
                errors.sum(),
                droppedFrames.sum(),
                maxTotalNanos.get(),
                elapsed
        );
    }

    /**
     * Immutable performance measurement snapshot.
     *
     * @param framesIn total frames ingested
     * @param framesOut total frames processed and emitted
     * @param framesSkippedStage2 frames that bypassed Stage 2 detection
     * @param errors non-fatal processing errors encountered
     * @param droppedFrames frames dropped due to back-pressure
     * @param maxStageNanos peak latency across any frame in nanoseconds
     * @param elapsedNanos elapsed pipeline execution duration in nanoseconds
     */
    public record Snapshot(
            long framesIn,
            long framesOut,
            long framesSkippedStage2,
            long errors,
            long droppedFrames,
            long maxStageNanos,
            long elapsedNanos) {

        /**
         * Computes derived frames-per-second processing throughput.
         *
         * @return throughput in FPS
         */
        public double fps() {
            if (elapsedNanos <= 0L) {
                return 0.0;
            }
            return (double) framesOut / ((double) elapsedNanos / 1_000_000_000.0);
        }
    }
}
