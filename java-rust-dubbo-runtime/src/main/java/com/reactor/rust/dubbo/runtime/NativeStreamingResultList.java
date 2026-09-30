package com.reactor.rust.dubbo.runtime;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

final class NativeStreamingResultList<E> extends AbstractList<E>
        implements DubboStreamingResult<E> {
    private final Hessian2Input input;
    private final NativeResponseDecoder<E> decoder;
    private final int declaredSize;
    private final Runnable releaseAction;
    private ArrayList<E> materialized;
    private int decodedItems;
    private boolean released;
    private Mode mode = Mode.READY;

    NativeStreamingResultList(
            NativeResponse response,
            Hessian2Input input,
            int declaredSize,
            NativeResponseDecoder<E> decoder) {
        this(input, declaredSize, decoder, new NativeResponseLease(response, input));
    }

    NativeStreamingResultList(
            Hessian2Input input,
            int declaredSize,
            NativeResponseDecoder<E> decoder,
            Runnable releaseAction) {
        this.input = Objects.requireNonNull(input, "input");
        this.decoder = Objects.requireNonNull(decoder, "decoder");
        this.declaredSize = declaredSize;
        this.releaseAction = Objects.requireNonNull(releaseAction, "releaseAction");
    }

    @Override
    public synchronized int size() {
        requireListMode();
        materializeAll();
        return materialized.size();
    }

    @Override
    public synchronized E get(int index) {
        requireListMode();
        materializeAll();
        if (index < 0 || index >= materialized.size()) {
            throw new IndexOutOfBoundsException("index=" + index + ", size=" + materialized.size());
        }
        return materialized.get(index);
    }

    @Override
    public synchronized Iterator<E> streamingIterator() {
        if (mode == Mode.MATERIALIZING) {
            materializeAll();
        }
        if (mode == Mode.MATERIALIZED) {
            return materialized.iterator();
        }
        if (mode != Mode.READY) {
            throw new IllegalStateException("Dubbo streaming result is already consumed or closed");
        }
        mode = Mode.STREAMING;
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                synchronized (NativeStreamingResultList.this) {
                    if (mode == Mode.CONSUMED) {
                        return false;
                    }
                    requireStreamingMode();
                    boolean more = hasMore();
                    if (!more) {
                        finish(Mode.CONSUMED);
                    }
                    return more;
                }
            }

            @Override
            public E next() {
                synchronized (NativeStreamingResultList.this) {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    E value = decodeNext();
                    if (declaredSize >= 0 && decodedItems == declaredSize) {
                        finish(Mode.CONSUMED);
                    }
                    return value;
                }
            }
        };
    }

    @Override
    public synchronized void close() {
        if (mode == Mode.MATERIALIZED || mode == Mode.CONSUMED || mode == Mode.CLOSED) {
            release();
            return;
        }
        if (materialized != null) {
            materialized.clear();
        }
        mode = Mode.CLOSED;
        release();
    }

    private void beginMaterializing() {
        if (mode == Mode.READY) {
            materialized = new ArrayList<>(Math.max(declaredSize, 0));
            mode = Mode.MATERIALIZING;
        }
    }

    private void materializeAll() {
        beginMaterializing();
        while (mode == Mode.MATERIALIZING && hasMore()) {
            materialized.add(decodeNext());
        }
        if (mode == Mode.MATERIALIZING) {
            finish(Mode.MATERIALIZED);
        }
    }

    private boolean hasMore() {
        try {
            return input.hasMoreListEntries(declaredSize, decodedItems);
        } catch (RuntimeException failure) {
            fail();
            return false;
        }
    }

    private E decodeNext() {
        try {
            E value = decoder.decode(input);
            decodedItems = Math.incrementExact(decodedItems);
            return value;
        } catch (RuntimeException failure) {
            fail();
            throw failure;
        }
    }

    private void finish(Mode finishedMode) {
        try {
            input.readListEnd(declaredSize);
            mode = finishedMode;
        } catch (RuntimeException failure) {
            fail();
            throw failure;
        } finally {
            release();
        }
    }

    private void fail() {
        mode = Mode.CLOSED;
        release();
    }

    private void release() {
        if (!released) {
            released = true;
            releaseAction.run();
        }
    }

    private void requireListMode() {
        if (mode == Mode.STREAMING || mode == Mode.CONSUMED) {
            throw new IllegalStateException(
                    "Regular List operations are unavailable after streaming iteration starts");
        }
        if (mode == Mode.CLOSED) {
            throw new IllegalStateException("Dubbo streaming result is closed");
        }
    }

    private void requireStreamingMode() {
        if (mode != Mode.STREAMING) {
            throw new IllegalStateException("Dubbo streaming result is not open for streaming");
        }
    }

    private enum Mode {
        READY,
        MATERIALIZING,
        MATERIALIZED,
        STREAMING,
        CONSUMED,
        CLOSED
    }

    private static final class NativeResponseLease implements Runnable {
        private NativeResponse response;
        private Hessian2Input input;

        private NativeResponseLease(NativeResponse response, Hessian2Input input) {
            this.response = Objects.requireNonNull(response, "response");
            this.input = Objects.requireNonNull(input, "input");
        }

        @Override
        public synchronized void run() {
            if (response == null) {
                return;
            }
            input.detach();
            response.close();
            input = null;
            response = null;
        }
    }
}
