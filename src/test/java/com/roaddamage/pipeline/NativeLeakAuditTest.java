package com.roaddamage.pipeline;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NativeLeakAuditTest {

    @Test
    @DisplayName("NativeLeakAudit takes valid snapshot using public JMX APIs")
    void snapshotReturnsValidMetrics() {
        NativeLeakAudit.Snapshot snap = NativeLeakAudit.snapshot();
        assertNotNull(snap);
        assertTrue(snap.openTensorLeases() >= 0L);
        assertTrue(snap.activeManagedMats() >= 0L);
        assertTrue(snap.directBufferPoolAllocations() >= 0L);
        assertTrue(snap.directBufferCount() >= 0L);
        assertTrue(snap.directMemoryUsed() >= 0L);

        String summary = snap.toString();
        assertNotNull(summary);
        assertTrue(summary.contains("openTensorLeases"));
    }
}
