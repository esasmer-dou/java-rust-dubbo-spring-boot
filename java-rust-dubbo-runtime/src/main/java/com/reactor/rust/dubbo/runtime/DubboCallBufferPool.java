package com.reactor.rust.dubbo.runtime;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public final class DubboCallBufferPool {
    private final ConcurrentLinkedQueue<DubboCallBuffer> available = new ConcurrentLinkedQueue<>();
    private final AtomicInteger retained = new AtomicInteger();
    private final int initialBytes;
    private final int maxRetainedBuffers;
    private final int maxRetainedBytes;

    public DubboCallBufferPool(int initialBytes, int maxRetainedBuffers, int maxRetainedBytes) {
        if (initialBytes <= 0 || maxRetainedBuffers < 0 || maxRetainedBytes < initialBytes) {
            throw new IllegalArgumentException("Invalid Dubbo call buffer pool limits");
        }
        this.initialBytes = initialBytes;
        this.maxRetainedBuffers = maxRetainedBuffers;
        this.maxRetainedBytes = maxRetainedBytes;
    }

    public DubboCallBuffer acquire() {
        DubboCallBuffer buffer = available.poll();
        if (buffer != null) {
            retained.decrementAndGet();
        } else {
            buffer = new DubboCallBuffer(this, initialBytes);
        }
        buffer.lease();
        return buffer;
    }

    void release(DubboCallBuffer buffer) {
        if (buffer.capacity() > maxRetainedBytes || maxRetainedBuffers == 0) {
            return;
        }
        int current = retained.incrementAndGet();
        if (current <= maxRetainedBuffers) {
            available.offer(buffer);
        } else {
            retained.decrementAndGet();
        }
    }

    public int retainedBuffers() {
        return retained.get();
    }
}
