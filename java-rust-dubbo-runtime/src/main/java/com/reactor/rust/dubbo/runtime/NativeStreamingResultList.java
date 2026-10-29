package com.reactor.rust.dubbo.runtime;

import java.lang.ref.Cleaner;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

final class NativeStreamingResultList<E> extends AbstractList<E>
        implements DubboStreamingResult<E> {
    private static final String ONE_PASS_ITERATOR_MESSAGE =
            "Dubbo streaming iteration is one-pass; streamingIterator() may be requested only once";

    private final Hessian2Input input;
    private final NativeResponseDecoder<E> decoder;
    private final int declaredSize;
    private final LeakGuard leakGuard;
    private final Cleaner.Cleanable cleanable;
    private ArrayList<E> materialized;
    private int decodedItems;
    private boolean released;
    private Mode mode = Mode.READY;

    NativeStreamingResultList(
            NativeResponse response,
            Hessian2Input input,
            int declaredSize,
            NativeResponseDecoder<E> decoder) {
        this(input, declaredSize, decoder, new NativeResponseLease(response));
    }

    NativeStreamingResultList(
            Hessian2Input input,
            int declaredSize,
            NativeResponseDecoder<E> decoder,
            Runnable releaseAction) {
        this.input = Objects.requireNonNull(input, "input");
        this.decoder = Objects.requireNonNull(decoder, "decoder");
        this.declaredSize = declaredSize;
        // The production action owns only the native response, never this list or its decoder.
        this.leakGuard = new LeakGuard(Objects.requireNonNull(releaseAction, "releaseAction"));
        this.cleanable = Cleanup.CLEANER.register(this, leakGuard);
        try {
            input.completeReference(input.referenceCount() - 1, this);
        } catch (RuntimeException | Error failure) {
            try {
                release();
            } catch (RuntimeException | Error cleanupFailure) {
                if (cleanupFailure != failure) failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
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
            throw new IllegalStateException(ONE_PASS_ITERATOR_MESSAGE);
        }
        mode = Mode.STREAMING;
        input.beginStreamingReferenceWindow();
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
        } catch (RuntimeException | Error failure) {
            fail(failure);
            throw failure;
        }
    }

    private E decodeNext() {
        try {
            if (mode == Mode.STREAMING) input.discardConsumedStreamingReferences();
            E value = decoder.decode(input);
            decodedItems = Math.incrementExact(decodedItems);
            return value;
        } catch (RuntimeException | Error failure) {
            fail(failure);
            throw failure;
        }
    }

    private void finish(Mode finishedMode) {
        try {
            input.readListEnd(declaredSize);
            mode = finishedMode;
        } catch (RuntimeException | Error failure) {
            fail(failure);
            throw failure;
        } finally {
            release();
        }
    }

    private void fail(Throwable failure) {
        mode = Mode.CLOSED;
        try {
            release();
        } catch (RuntimeException | Error cleanupFailure) {
            if (cleanupFailure != failure) failure.addSuppressed(cleanupFailure);
        }
    }

    private void release() {
        if (!released) {
            released = true;
            try {
                input.detach();
            } finally {
                try {
                    leakGuard.release(false);
                } finally {
                    cleanable.clean();
                }
            }
        }
    }

    static long leakedResultCount() {
        return LeakCounters.LEAKED_RESULTS.sum();
    }

    static void resetLeakedResultCountForTesting() {
        LeakCounters.LEAKED_RESULTS.reset();
    }

    private static final class Cleanup {
        // Initialized only on the first actual streaming result, never on disabled/non-streamed paths.
        private static final Cleaner CLEANER = Cleaner.create(task -> {
            Thread thread = new Thread(null, task, "reactor-dubbo-stream-cleaner", 256 * 1024L, false);
            thread.setDaemon(true);
            thread.setContextClassLoader(null);
            return thread;
        });
    }

    private static final class LeakCounters {
        private static final LongAdder LEAKED_RESULTS = new LongAdder();
    }

    private static final class LeakGuard implements Runnable {
        private final AtomicBoolean released = new AtomicBoolean();
        private Runnable action;

        private LeakGuard(Runnable action) {
            this.action = action;
        }

        private void release(boolean leaked) {
            if (released.compareAndSet(false, true)) {
                Runnable current = action;
                action = null;
                if (leaked) LeakCounters.LEAKED_RESULTS.increment();
                current.run();
            }
        }

        @Override
        public void run() {
            release(true);
        }
    }

    private void requireListMode() {
        if (mode == Mode.STREAMING || mode == Mode.CONSUMED) {
            throw new IllegalStateException(
                    "Dubbo streaming iteration is one-pass; regular List operations are unavailable "
                            + "after streamingIterator() starts");
        }
        if (mode == Mode.CLOSED) {
            throw new IllegalStateException("Dubbo streaming result is closed");
        }
    }

    private void requireStreamingMode() {
        if (mode != Mode.STREAMING) {
            throw new IllegalStateException(
                    "Dubbo streaming iteration is one-pass; the iterator cannot be used after "
                            + "the result is consumed or closed");
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

        private NativeResponseLease(NativeResponse response) {
            this.response = Objects.requireNonNull(response, "response");
        }

        @Override
        public void run() {
            if (response == null) {
                return;
            }
            response.close();
            response = null;
        }
    }
}
