package com.roaddamage.pipeline;

import com.roaddamage.core.model.ManagedMat;
import com.roaddamage.infer.DirectFloatBufferPool;
import com.roaddamage.infer.TensorLease;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;

/**
 * Diagnostic utility capturing JVM direct memory metrics and framework native resource allocations.
 * <p>
 * Uses only public JMX platform APIs without internal JDK or sun.* packages.
 */
public final class NativeLeakAudit {

    private NativeLeakAudit() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Immutable snapshot of native resource allocations.
     *
     * @param openTensorLeases number of currently open, unclosed TensorLease instances
     * @param activeManagedMats number of currently active, unreleased ManagedMat instances
     * @param directBufferPoolAllocations total buffers allocated by the inference pool
     * @param directMemoryUsed JVM direct native memory currently allocated in bytes
     * @param directBufferCount total number of direct buffers allocated in the JVM
     */
    public record Snapshot(
            long openTensorLeases,
            long activeManagedMats,
            long directBufferPoolAllocations,
            long directMemoryUsed,
            long directBufferCount) {

        /**
         * Alias for activeManagedMats matching specification naming.
         *
         * @return number of currently active, unreleased ManagedMat instances
         */
        public long liveManagedMats() {
            return activeManagedMats;
        }

        /**
         * Checks whether any framework native resources are currently leaked.
         *
         * @return true if open leases and active mats are zero
         */
        public boolean isClean() {
            return openTensorLeases == 0L && activeManagedMats == 0L;
        }
    }

    /**
     * Captures a snapshot of native allocations without an explicit buffer pool reference.
     *
     * @return current leak audit snapshot
     */
    public static Snapshot snapshot() {
        return snapshot(null);
    }

    /**
     * Captures a snapshot of native allocations including a reference to an active or closed buffer pool.
     *
     * @param pool optional DirectFloatBufferPool reference
     * @return current leak audit snapshot
     */
    public static Snapshot snapshot(DirectFloatBufferPool pool) {
        long openLeases = TensorLease.OPEN_LEASES.get();
        long activeMats = ManagedMat.activeCount();
        long poolAllocations = pool != null ? pool.allocationCount() : 0L;

        long directMemory = 0L;
        long directCount = 0L;

        for (BufferPoolMXBean mxBean : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
            if ("direct".equalsIgnoreCase(mxBean.getName())) {
                directMemory = mxBean.getMemoryUsed();
                directCount = mxBean.getCount();
                break;
            }
        }

        return new Snapshot(openLeases, activeMats, poolAllocations, directMemory, directCount);
    }
}
