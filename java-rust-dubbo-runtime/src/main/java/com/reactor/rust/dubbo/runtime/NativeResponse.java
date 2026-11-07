package com.reactor.rust.dubbo.runtime;

import java.nio.ByteBuffer;

public final class NativeResponse implements AutoCloseable {
    private long handle;
    private final int status;
    private final ByteBuffer body;

    NativeResponse(long handle, int status, ByteBuffer body) {
        this.handle = handle;
        this.status = status;
        // JNI transfers this private buffer descriptor; only borrowed read-only views escape.
        this.body = body;
    }

    public int status() {
        return status;
    }

    public ByteBuffer body() {
        ensureOpen();
        return body.asReadOnlyBuffer();
    }

    void attachTo(Hessian2Input input, int maxCollectionItems) {
        ensureOpen();
        // Hessian2Input creates its own cursor; intermediate public views are unnecessary.
        input.attachReadOnly(body, body.capacity(), maxCollectionItems);
    }

    @Override
    public void close() {
        long value = handle;
        if (value != 0) {
            handle = 0;
            NativeDubboBridge.releaseResponse(value);
        }
    }

    private void ensureOpen() {
        if (handle == 0) {
            throw new IllegalStateException("Native response is closed");
        }
    }
}
