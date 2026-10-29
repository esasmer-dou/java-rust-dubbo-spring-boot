package com.reactor.rust.dubbo.runtime;

import java.io.OutputStream;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

/** One lazy, immutable-capacity pool. Admission never waits for another writer. */
final class DirectJsonWriterPool {
    static final String PROPERTY = "reactor.dubbo.direct-json.pool-slots";
    static final String ENVIRONMENT = "REACTOR_DUBBO_DIRECT_JSON_POOL_SLOTS";
    static final String SCAN_PROPERTY = "reactor.dubbo.direct-json.max-wire-scan-bytes";
    static final String SCAN_ENVIRONMENT = "REACTOR_DUBBO_DIRECT_JSON_MAX_WIRE_SCAN_BYTES";
    static final String OUTPUT_PROPERTY = "reactor.dubbo.direct-json.max-output-bytes";
    static final String OUTPUT_ENVIRONMENT = "REACTOR_DUBBO_DIRECT_JSON_MAX_OUTPUT_BYTES";
    static final long DEFAULT_BYTES = 32L * 1024 * 1024, MAX_BYTES = 64L * 1024 * 1024;
    private static volatile DirectJsonWriterPool global;
    private final Hessian2JsonTranscoder[] writers;
    private final AtomicReferenceArray<Thread> owners;
    private final AtomicInteger available;
    final long scanLimit, outputLimit;
    private final String rejectionMessage;

    DirectJsonWriterPool(int slots, long scanLimit, long outputLimit) {
        if (slots < 1 || slots > 32 || scanLimit < 1 || scanLimit > MAX_BYTES || outputLimit < 1 || outputLimit > MAX_BYTES) {
            throw new IllegalArgumentException("Direct JSON pool requires 1..32 slots and byte budgets in 1..67108864");
        }
        this.scanLimit = scanLimit;
        this.outputLimit = outputLimit;
        rejectionMessage = "Direct JSON writer pool is full (slots=" + slots + ')';
        writers = new Hessian2JsonTranscoder[slots];
        owners = new AtomicReferenceArray<>(slots);
        available = new AtomicInteger(slots == 32 ? -1 : (1 << slots) - 1);
        for (int index = 0; index < slots; index++) writers[index] = new Hessian2JsonTranscoder(this, index);
    }

    static DirectJsonWriterPool global() {
        DirectJsonWriterPool pool = global;
        return pool == null ? initialize() : pool;
    }

    private static synchronized DirectJsonWriterPool initialize() {
        if (global == null) global = new DirectJsonWriterPool(slotCount(System.getProperty(PROPERTY), System.getenv(ENVIRONMENT)),
                byteLimit(System.getProperty(SCAN_PROPERTY), System.getenv(SCAN_ENVIRONMENT)),
                byteLimit(System.getProperty(OUTPUT_PROPERTY), System.getenv(OUTPUT_ENVIRONMENT)));
        return global;
    }

    static long byteLimit(String property, String environment) {
        String value = property != null ? property : environment;
        if (value == null) return DEFAULT_BYTES;
        try {
            long bytes = Long.parseLong(value.trim());
            if (bytes >= 1 && bytes <= MAX_BYTES) return bytes;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("Direct JSON byte budgets must be integers in 1..67108864");
    }

    static int slotCount(String property, String environment) {
        String value = property != null ? property : environment;
        if (value == null) return 16;
        try {
            int slots = Integer.parseInt(value.trim());
            if (slots >= 1 && slots <= 32) return slots;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException(PROPERTY + " / " + ENVIRONMENT + " must be an integer in 1..32");
    }

    Hessian2JsonTranscoder acquire(Hessian2Input input, OutputStream target) {
        java.util.Objects.requireNonNull(input, "input");
        java.util.Objects.requireNonNull(target, "target");
        Thread thread = Thread.currentThread();
        for (int index = 0; index < writers.length; index++) {
            if (owners.get(index) == thread) throw new IllegalStateException("Direct JSON output is not reentrant on the same thread");
        }
        int free, bit;
        do {
            free = available.get();
            if (free == 0) throw new PoolExhausted(rejectionMessage);
            bit = Integer.lowestOneBit(free);
        } while (!available.compareAndSet(free, free & ~bit));
        int index = Integer.numberOfTrailingZeros(bit);
        owners.set(index, thread);
        Hessian2JsonTranscoder writer = writers[index];
        try {
            writer.attach(input, target);
            return writer;
        } catch (RuntimeException | Error failure) {
            try { writer.detach(); }
            catch (RuntimeException | Error cleanupFailure) {
                if (cleanupFailure != failure) failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    boolean ownedByCaller(int slot) { return owners.get(slot) == Thread.currentThread(); }

    void release(int slot) {
        owners.set(slot, null);
        int previous, bit = 1 << slot;
        do { previous = available.get(); } while (!available.compareAndSet(previous, previous | bit));
    }

    int capacity() { return writers.length; }
    int active() { return writers.length - Integer.bitCount(available.get()); }
    long payloadCapacity() { return (long) writers.length * 65536; }
    boolean idleAndDetached() {
        if (active() != 0) return false;
        for (int index = 0; index < writers.length; index++) {
            if (owners.get(index) != null || !writers[index].detached()) return false;
        }
        return true;
    }
    static int initializedSlots() { return global == null ? 0 : global.capacity(); }

    private static final class PoolExhausted extends RejectedExecutionException {
        private PoolExhausted(String message) { super(message); }
        @Override public Throwable fillInStackTrace() { return this; }
    }
}
