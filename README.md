# Road Damage Inspection Framework

High-performance, multi-stage road distress detection and severity assessment pipeline built in pure Java 21, leveraging ONNX Runtime, OpenCV, and JavaFX.

## Architecture

The framework coordinates three sequential deep-learning stages:
1. **Stage 1 (Screening)**: MobileNetV3-Small binary screening (`models/stage1_mobilenetv3_small.onnx`) filtering undamaged road frames with zero unnecessary computation.
2. **Stage 2 (Detection)**: YOLOv8 Object Detector (`models/stage2_yolov8.onnx`) locating distress bounding boxes across 4 classes: Longitudinal crack (D00), Transverse crack (D10), Alligator crack (D20), and Pothole (D40).
3. **Stage 3 (Severity & Priority)**: EfficientNet Classifier (`models/stage3_efficientnet.onnx`) combined with deterministic structural scoring evaluating area ratio, aspect ratio, and class risk to produce 0–100 priority scores and severity levels (LOW, MEDIUM, HIGH, CRITICAL).
4. **Phase 6 (Integration)**: `InspectionPipeline` orchestrating asynchronous ingest and inference with bounded in-memory queuing, back-pressure drop counters, single-threaded engine isolation, and JavaFX desktop UI visualization.

---

## Recommended JVM Flags

For optimal real-time throughput, deterministic low-latency GC, and native memory management, launch the JVM with the following arguments:

```bash
-Xms1g -Xmx2g -XX:MaxDirectMemorySize=1g -XX:+UseG1GC -XX:MaxGCPauseMillis=50
```

### Explanation of Flags:
- `-Xms1g -Xmx2g`: Constrains Java heap to 1GB initial / 2GB maximum, preventing unbounded heap expansion while guaranteeing sufficient headroom for image caches.
- `-XX:MaxDirectMemorySize=1g`: Caps off-heap DirectByteBuffer allocations to 1GB. Direct buffers are pooled and reused via `DirectFloatBufferPool` to prevent native fragmentation.
- `-XX:+UseG1GC`: Enables Garbage-First garbage collector designed for multi-threaded applications with large memory spaces.
- `-XX:MaxGCPauseMillis=50`: Sets G1GC pause time target to 50ms, ensuring smooth 30+ FPS video playback and UI rendering without stutter.

---

## ONNX Runtime Session Tuning

Each ONNX Runtime session (`OrtSession`) is configured with the following session options:

- **intraOpNumThreads**: `min(4, Runtime.getRuntime().availableProcessors())`  
  Limits per-operator parallel execution threads to avoid CPU cache thrashing on consumer laptop and workstation CPUs.
- **interOpNumThreads**: `1`  
  Enforces single-threaded operator graph execution because frames are processed sequentially through stages on a dedicated worker thread.
- **graphOptimizationLevel**: `ALL_OPT` (ORT_ENABLE_ALL)  
  Enables all compiler-level graph optimizations, constant folding, and node fusions prior to model execution.

---

## Configuration

Default runtime settings can be customized in `src/main/resources/config/application.properties`:

- `ORT_INTRA_OP_THREADS`: Parallel thread count for ONNX operators.
- `FRAME_STRIDE`: Frame subsampling stride (1 processes every frame, 2 every other frame, etc.).
- `QUEUE_CAPACITY`: Maximum in-flight frame envelopes in `BoundedFrameQueue` (1 to 64).
- `STAGE1_DAMAGE_THRESHOLD`: Minimum damage probability (0.0 to 1.0) to qualify as damaged.
- `SKIP_STAGE2_WHEN_CLEAN`: Bypasses detection and severity stages when Stage 1 finds no distress.

---

## Native Memory Safety & Leak Audit

The framework enforces deterministic reference-counted lifecycle tracking:
- `TensorLease`: Guarantees ONNX tensors are wrapped and closed immediately upon consumption.
- `ManagedMat`: Reference-counted OpenCV `Mat` wrapper ensuring native C++ matrices are released upon refCount reaching zero.
- `DirectFloatBufferPool`: Fixed-capacity direct buffer cache keyed by capacity, eliminating repetitive native `malloc`/`free` calls.
- `NativeLeakAudit`: JMX-based audit verifying zero unreleased leases, active Mat instances, and steady direct buffer pools after pipeline shutdown.
