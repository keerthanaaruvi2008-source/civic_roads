package com.roaddamage.infer;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import java.nio.FloatBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TensorLeaseTest {

    @Test
    void wrapsTensorAndTracksGlobalLeaseCount() throws OrtException {
        long baseline = TensorLease.OPEN_LEASES.get();
        FloatBuffer data = FloatBuffer.wrap(new float[]{1.0f, 2.0f});
        OnnxTensor tensor = OnnxTensor.createTensor(OrtRuntime.environment(), data, new long[]{1, 2});

        TensorLease lease = TensorLease.wrap(tensor);
        assertEquals(baseline + 1, TensorLease.OPEN_LEASES.get(), "Lease count must increment by 1");
        assertFalse(lease.isClosed());
        assertSame(tensor, lease.tensor());

        lease.close();
        assertTrue(lease.isClosed());
        assertEquals(baseline, TensorLease.OPEN_LEASES.get(), "Lease count must decrement by 1 upon close");
        assertThrows(IllegalStateException.class, lease::tensor);
    }

    @Test
    void closeIsIdempotent() throws OrtException {
        long baseline = TensorLease.OPEN_LEASES.get();
        FloatBuffer data = FloatBuffer.wrap(new float[]{42.0f});
        OnnxTensor tensor = OnnxTensor.createTensor(OrtRuntime.environment(), data, new long[]{1, 1});

        TensorLease lease = TensorLease.wrap(tensor);
        assertEquals(baseline + 1, TensorLease.OPEN_LEASES.get());

        lease.close();
        lease.close();
        lease.close();

        assertTrue(lease.isClosed());
        assertEquals(baseline, TensorLease.OPEN_LEASES.get(), "Multiple close calls must not decrement multiple times");
    }

    @Test
    void rejectsNullTensor() {
        assertThrows(NullPointerException.class, () -> TensorLease.wrap(null));
    }
}
