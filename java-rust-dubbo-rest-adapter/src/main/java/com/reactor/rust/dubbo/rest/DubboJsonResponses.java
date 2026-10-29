package com.reactor.rust.dubbo.rest;

import com.reactor.rust.dubbo.runtime.DubboDirectJsonStream;
import com.reactor.rust.json.ManagedJsonBodyProducer;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/** Explicit, resource-owning bridge to REST's existing bounded response-buffer protocol. */
public final class DubboJsonResponses {
    private static final byte[] NULL = {'n', 'u', 'l', 'l'};

    private DubboJsonResponses() {}

    /**
     * Transfers result ownership to a one-shot producer; close it if it is never
     * submitted or if response writing is cancelled. Null results produce JSON null.
     * Other lists must support direct JSON; no iteration/materialization is attempted.
     */
    public static ManagedJsonBodyProducer body(List<?> result) {
        if (result == null) return new Body(null);
        if (!(result instanceof DubboDirectJsonStream stream)) {
            throw new IllegalArgumentException("Result requires @DubboStreamed(directJson=true); no DTO fallback is available");
        }
        return new Body(stream);
    }

    // Embed the CAS state in the wrapper; no second per-response atomic allocation.
    private static final class Body extends AtomicInteger implements ManagedJsonBodyProducer {
        private static final long serialVersionUID = 1L;
        private static final int READY = 0, WRITING = 1, RETRY = 2, CLOSED = 3;
        private DubboDirectJsonStream stream;
        private int requiredLength;

        private Body(DubboDirectJsonStream stream) { this.stream = stream; }

        @Override
        public int write(ByteBuffer output, int offset) {
            int previous = get();
            if ((previous != READY && previous != RETRY) || !compareAndSet(previous, WRITING)) {
                throw new IllegalStateException("Dubbo JSON body is closed, consumed or already being written");
            }
            boolean retain = false;
            try {
                Objects.requireNonNull(output, "output");
                if (output.isReadOnly() || offset < 0 || offset > output.limit()) {
                    throw new IllegalArgumentException("Dubbo JSON body requires a writable buffer and valid offset");
                }
                int capacity = output.limit() - offset;
                if (requiredLength != 0 && capacity < requiredLength) {
                    throw new IllegalArgumentException("Dubbo JSON body retry requires at least " + requiredLength + " writable bytes");
                }
                int written;
                if (stream != null) written = stream.writeTo(output, offset);
                else if (capacity < NULL.length) written = -NULL.length;
                else { output.put(offset, NULL); written = NULL.length; }
                if (written < 0) {
                    if (requiredLength != 0 || written == Integer.MIN_VALUE) {
                        throw new IllegalStateException("Dubbo JSON body exceeded its single capacity retry");
                    }
                    requiredLength = -written;
                    retain = true;
                }
                return written;
            } finally {
                // close() may request cancellation during a write, but cannot free its borrowed wire.
                if (!retain || !compareAndSet(WRITING, RETRY)) {
                    set(CLOSED);
                    release();
                }
            }
        }

        @Override
        public void close() {
            int previous = getAndSet(CLOSED);
            if (previous != CLOSED && previous != WRITING) release();
        }

        private void release() {
            DubboDirectJsonStream current = stream;
            stream = null;
            if (current == null) return;
            try { current.close(); }
            catch (RuntimeException | Error failure) {
                // Abort cleanup must not replace an HTTP/serialization failure.
                try {
                    System.getLogger(DubboJsonResponses.class.getName()).log(System.Logger.Level.ERROR,
                            "Failed to close a Dubbo direct JSON response", failure);
                } catch (RuntimeException | Error loggingFailure) {
                    // ManagedJsonBodyProducer.close is explicitly non-throwing.
                }
            }
        }
    }
}
