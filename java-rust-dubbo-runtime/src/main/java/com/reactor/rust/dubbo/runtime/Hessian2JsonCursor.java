package com.reactor.rust.dubbo.runtime;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/** Bounded pooled read window; the borrowed buffer remains the canonical cursor. */
final class Hessian2JsonCursor {
    static final int CAPACITY = 4096;
    private final byte[] window = new byte[CAPACITY];
    private ByteBuffer source;
    private int start, end, highWater;
    private boolean buffered;

    void attach(ByteBuffer source) {
        if (this.source != null) throw new IllegalStateException("Direct JSON cursor is already attached");
        this.source = Objects.requireNonNull(source, "source");
    }

    void bufferReads() { buffered = true; }
    boolean attached() { return source != null; }
    boolean detached() { return source == null && highWater == 0 && !buffered; }
    int cachedBytes() { return highWater; }

    void detach() {
        Arrays.fill(window, 0, highWater, (byte) 0);
        source = null;
        start = end = highWater = 0;
        buffered = false;
    }

    int position() { return source.position(); }
    void position(int position) { source.position(position); }
    int limit() { return source.limit(); }
    void limit(int limit) { source.limit(limit); }
    int remaining() { return source.remaining(); }
    boolean hasRemaining() { return source.hasRemaining(); }

    int scalarByte() {
        int position = source.position();
        int index = scalarIndex(position, 1);
        int value = window[index] & 255;
        source.position(position + 1);
        return value;
    }

    int scalarShort() {
        int position = source.position();
        int index = scalarIndex(position, 2);
        int value = (window[index] & 255) << 8 | window[index + 1] & 255;
        source.position(position + 2);
        return value;
    }

    int scalarCompactShort() {
        // Compact integer payloads consume an available first byte before failing.
        if (source.remaining() < 2) return (scalarByte() << 8) | scalarByte();
        return scalarShort();
    }

    int scalarInt() {
        int position = source.position();
        int index = scalarIndex(position, 4);
        int value = (window[index] & 255) << 24 | (window[index + 1] & 255) << 16
                | (window[index + 2] & 255) << 8 | window[index + 3] & 255;
        source.position(position + 4);
        return value;
    }

    long scalarLong() {
        int position = source.position();
        int index = scalarIndex(position, 8);
        long value = (window[index] & 255L) << 56 | (window[index + 1] & 255L) << 48
                | (window[index + 2] & 255L) << 40 | (window[index + 3] & 255L) << 32
                | (window[index + 4] & 255L) << 24 | (window[index + 5] & 255L) << 16
                | (window[index + 6] & 255L) << 8 | window[index + 7] & 255L;
        source.position(position + 8);
        return value;
    }

    private int scalarIndex(int position, int width) {
        if (source.limit() - position < width) throw new DubboCodecException("Incomplete Hessian payload");
        if (position < start || position > end - width) refill(position);
        return position - start;
    }

    byte get() {
        int position = source.position();
        if (position >= source.limit()) throw new BufferUnderflowException();
        if (!buffered) return source.get();
        if (position < start || position >= end) refill(position);
        byte value = window[position - start];
        source.position(position + 1);
        return value;
    }

    byte get(int index) {
        if (index < 0 || index >= source.limit()) throw new IndexOutOfBoundsException(index);
        if (!buffered) return source.get(index);
        if (index < start || index >= end) refill(index);
        return window[index - start];
    }

    void get(byte[] target, int offset, int length) {
        Objects.checkFromIndexSize(offset, length, target.length);
        int position = source.position();
        if (length > source.remaining()) throw new BufferUnderflowException();
        if (!buffered) {
            source.get(target, offset, length);
            return;
        }
        int remaining = length;
        while (remaining > 0) {
            if (position < start || position >= end) refill(position);
            int count = Math.min(remaining, end - position);
            System.arraycopy(window, position - start, target, offset, count);
            position += count;
            offset += count;
            remaining -= count;
        }
        source.position(position);
    }

    private void refill(int position) {
        int length = Math.min(CAPACITY, source.limit() - position);
        // Absolute bulk access does not advance the shared legacy scalar cursor.
        source.get(position, window, 0, length);
        buffered = true;
        start = position;
        end = position + length;
        highWater = Math.max(highWater, length);
    }
}
