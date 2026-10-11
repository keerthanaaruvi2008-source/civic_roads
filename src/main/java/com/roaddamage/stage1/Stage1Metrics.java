package com.roaddamage.stage1;

/**
 * Thread-safe telemetry accumulator for Stage 1 screening latency and damage classification statistics.
 */
public final class Stage1Metrics {

    private long count;
    private long damagedCount;
    private long totalNanos;
    private long minNanos;
    private long maxNanos;

    /**
     * Immutable snapshot of accumulated Stage 1 screening metrics.
     *
     * @param count total number of frames screened
     * @param damagedCount number of frames classified as damaged
     * @param totalNanos cumulative inference latency in nanoseconds
     * @param minNanos minimum observed inference latency in nanoseconds (or 0 if count is 0)
     * @param maxNanos maximum observed inference latency in nanoseconds
     */
    public record Snapshot(long count, long damagedCount, long totalNanos, long minNanos, long maxNanos) {

        /**
         * Computes average screening latency in nanoseconds.
         *
         * @return average nanoseconds per frame, or 0.0 if no frames have been processed
         */
        public double averageNanos() {
            return count == 0 ? 0.0 : (double) totalNanos / count;
        }

        /**
         * Computes the proportion of screened frames classified as damaged.
         *
         * @return damage classification ratio between 0.0 and 1.0
         */
        public double damageRate() {
            return count == 0 ? 0.0 : (double) damagedCount / count;
        }
    }

    /**
     * Constructs a new empty Stage1Metrics accumulator.
     */
    public Stage1Metrics() {
        reset();
    }

    /**
     * Records an inference result into the accumulator.
     *
     * @param damaged whether damage was detected
     * @param inferenceNanos inference latency in nanoseconds
     */
    public synchronized void record(boolean damaged, long inferenceNanos) {
        count++;
        if (damaged) {
            damagedCount++;
        }
        totalNanos += inferenceNanos;
        if (inferenceNanos < minNanos) {
            minNanos = inferenceNanos;
        }
        if (inferenceNanos > maxNanos) {
            maxNanos = inferenceNanos;
        }
    }

    /**
     * Records a {@link MobileNetV3Screener.Stage1Result} into the accumulator.
     *
     * @param result the screening result
     */
    public synchronized void record(MobileNetV3Screener.Stage1Result result) {
        record(result.damaged(), result.inferenceNanos());
    }

    /**
     * Captures an immutable snapshot of all metrics recorded up to this point.
     *
     * @return metrics snapshot
     */
    public synchronized Snapshot snapshot() {
        long effectiveMin = (count == 0) ? 0L : minNanos;
        return new Snapshot(count, damagedCount, totalNanos, effectiveMin, maxNanos);
    }

    /**
     * Resets all metric counters to zero.
     */
    public synchronized void reset() {
        this.count = 0L;
        this.damagedCount = 0L;
        this.totalNanos = 0L;
        this.minNanos = Long.MAX_VALUE;
        this.maxNanos = 0L;
    }
}
