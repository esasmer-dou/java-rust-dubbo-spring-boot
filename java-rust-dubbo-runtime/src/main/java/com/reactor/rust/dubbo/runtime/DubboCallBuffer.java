package com.reactor.rust.dubbo.runtime;

import java.nio.ByteBuffer;

public final class DubboCallBuffer implements AutoCloseable {
    private final DubboCallBufferPool owner;
    private final Hessian2Output output = new Hessian2Output();
    private final Hessian2Input input = new Hessian2Input();
    private ByteBuffer request;
    private boolean leased;

    DubboCallBuffer(DubboCallBufferPool owner, int initialBytes) {
        this.owner = owner;
        request = ByteBuffer.allocateDirect(initialBytes);
    }

    void lease() {
        if (leased) {
            throw new IllegalStateException("Dubbo call buffer is already leased");
        }
        leased = true;
        output.attach(request, 0);
    }

    public Hessian2Output output() {
        ensureLeased();
        return output;
    }

    public Hessian2Input responseInput(NativeResponse response, int maxCollectionItems) {
        ensureLeased();
        input.attach(response.body(), response.body().capacity(), maxCollectionItems);
        return input;
    }

    ByteBuffer requestBuffer() {
        ensureLeased();
        request = output.buffer();
        return request;
    }

    int requestLength() {
        ensureLeased();
        return output.position();
    }

    int capacity() {
        return request.capacity();
    }

    @Override
    public void close() {
        if (leased) {
            leased = false;
            owner.release(this);
        }
    }

    private void ensureLeased() {
        if (!leased) {
            throw new IllegalStateException("Dubbo call buffer is not leased");
        }
    }
}
