package com.reactor.rust.dubbo.runtime;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/** Adds mutually exclusive direct JSON consumption to the existing native-backed List. */
public final class DirectJsonResults {
    private DirectJsonResults() {}

    /** The release action must own only the native lease, never input, decoder or returned result. */
    public static <E> List<E> open(Hessian2Input input, NativeResponseDecoder<E> decoder,
            Hessian2JsonWriter writer, Runnable releaseAction) {
        return open(input, decoder, writer, releaseAction, false);
    }

    static <E> List<E> open(Hessian2Input input, NativeResponseDecoder<E> decoder,
            Hessian2JsonWriter writer, Runnable releaseAction, boolean attachments) {
        return open(input, decoder, writer, releaseAction, attachments, null);
    }

    static <E> List<E> open(Hessian2Input input, NativeResponseDecoder<E> decoder,
            Hessian2JsonWriter writer, Runnable releaseAction, boolean attachments, DirectJsonWriterPool pool) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(releaseAction, "releaseAction");
        NativeStreamingResultList.LeakGuard lease = new NativeStreamingResultList.LeakGuard(releaseAction);
        boolean transferred = false;
        Throwable failure = null;
        try {
            Objects.requireNonNull(writer, "writer");
            Objects.requireNonNull(decoder, "decoder");
            Hessian2JsonInput.Root root = (pool == null ? DirectJsonWriterPool.global() : pool).readRoot(input);
            input.jsonListRoot(root.typeName());
            Result<E> result = new Result<>(input, root, decoder, writer, lease, attachments, pool);
            transferred = true;
            return result;
        } catch (RuntimeException | Error error) {
            failure = error;
            throw error;
        } finally {
            if (!transferred) {
                try { input.detach(); }
                catch (RuntimeException | Error cleanupFailure) {
                    if (failure == null) { failure = cleanupFailure; throw cleanupFailure; }
                    cleanup(failure, cleanupFailure);
                } finally {
                    try { lease.release(false); }
                    catch (RuntimeException | Error cleanupFailure) {
                        if (failure != null) {
                            if (cleanupFailure != failure) failure.addSuppressed(cleanupFailure);
                        }
                        else throw cleanupFailure;
                    }
                }
            }
        }
    }

    private static Hessian2JsonTranscoder acquire(DirectJsonWriterPool pool, Hessian2Input input, OutputStream output) {
        return pool == null ? Hessian2JsonTranscoder.acquire(input, output) : pool.acquire(input, output);
    }

    private static void cleanup(Throwable failure, Throwable cleanupFailure) {
        if (failure != null) {
            if (failure != cleanupFailure) failure.addSuppressed(cleanupFailure);
        } else if (cleanupFailure instanceof RuntimeException error) throw error;
        else if (cleanupFailure instanceof Error error) throw error;
    }

    private static final class Result<E> extends NativeStreamingResultList<E>
            implements DubboDirectJsonStream {
        private static final int READY = 0, DTO = 1, DIRECT = 2, CLOSED = 3, BUFFER_RETRY = 4;
        private final Hessian2Input input;
        private final Hessian2JsonWriter writer;
        private final Hessian2JsonInput.Root root;
        private final DirectJsonWriterPool pool;
        private final boolean attachments;
        private final AtomicInteger state = new AtomicInteger();
        private BufferOutput bufferOutput;
        private int requiredLength;

        private Result(Hessian2Input input, Hessian2JsonInput.Root root, NativeResponseDecoder<E> decoder,
                Hessian2JsonWriter writer, NativeStreamingResultList.LeakGuard release,
                boolean attachments, DirectJsonWriterPool pool) {
            // This result is the sole lease/Cleaner owner in BOTH consumption modes.
            super(input, root.size(), decoder, release);
            this.input = input;
            this.root = root;
            this.pool = pool;
            this.writer = writer;
            this.attachments = attachments;
        }

        @Override public int size() { selectDtoMode(); return super.size(); }
        @Override public E get(int index) { selectDtoMode(); return super.get(index); }
        @Override public Iterator<E> streamingIterator() { selectDtoMode(); return super.streamingIterator(); }

        private void selectDtoMode() {
            if (state.compareAndSet(READY, DTO) || state.get() == DTO) return;
            throw new IllegalStateException("Direct JSON consumption and ordinary List/DTO consumption are mutually exclusive");
        }

        @Override
        public long writeTo(OutputStream output) throws IOException {
            if (!state.compareAndSet(READY, DIRECT)) throw new IllegalStateException("Direct JSON is one-pass and unavailable after List/DTO consumption or close");
            Hessian2JsonTranscoder out = null;
            Throwable failure = null;
            try {
                Objects.requireNonNull(output, "output");
                out = acquire(pool, input, output);
                writeJson(out);
                return out.bytesWritten();
            } catch (IOException | RuntimeException | Error error) {
                failure = error;
                throw error;
            } finally {
                finish(out, failure, false);
            }
        }

        @Override
        public int writeTo(ByteBuffer output, int offset) {
            int previous = state.get();
            if ((previous != READY && previous != BUFFER_RETRY) || !state.compareAndSet(previous, DIRECT)) {
                throw new IllegalStateException("Direct JSON buffer consumption is unavailable or active");
            }
            Hessian2JsonTranscoder out = null;
            Throwable failure = null;
            boolean retain = false;
            try {
                Objects.requireNonNull(output, "output");
                if (output.isReadOnly() || offset < 0 || offset > output.limit()) {
                    throw new IllegalArgumentException("Direct JSON requires a writable buffer and offset within its limit");
                }
                int capacity = output.limit() - offset;
                if (previous == BUFFER_RETRY && capacity < requiredLength) {
                    throw new IllegalArgumentException("Direct JSON retry requires at least " + requiredLength + " writable body bytes");
                }
                if (bufferOutput == null) bufferOutput = new BufferOutput();
                bufferOutput.attach(output, offset);
                out = acquire(pool, input, bufferOutput);
                writeJson(out);
                int length = bufferOutput.count;
                if (previous == BUFFER_RETRY && length != requiredLength) {
                    throw new DubboCodecException("Direct JSON writer changed response length during capacity retry");
                }
                if (length > capacity) {
                    requiredLength = length;
                    retain = true;
                }
                return retain ? -length : length;
            } catch (IOException error) {
                IllegalStateException wrapped = new IllegalStateException("Direct JSON buffer writing failed", error);
                failure = wrapped;
                throw wrapped;
            } catch (RuntimeException | Error error) {
                failure = error;
                throw error;
            } finally {
                finish(out, failure, retain);
            }
        }

        private void writeJson(Hessian2JsonTranscoder out) throws IOException {
            Hessian2JsonInput json = out.input(input);
            json.restoreRoot(root);
            out.writeByte('[');
            int count = 0;
            while (json.hasMore(root.size(), count)) {
                json.beginRow();
                if (count > 0) out.writeByte(',');
                writer.write(input, out);
                count++;
            }
            json.listEnd(root.size());
            if (attachments) json.skipValue();
            if (input.remaining() != 0) throw new DubboCodecException("Unexpected bytes after direct JSON response");
            out.writeByte(']');
            out.flush();
        }

        private void finish(Hessian2JsonTranscoder out, Throwable failure, boolean retain) {
            Throwable detachFailure = null;
            try {
                if (out != null) out.detach();
            } catch (RuntimeException | Error error) {
                detachFailure = error;
            }
            // Keep one cleanup tail: finally duplicates this graph in the hot caller's JIT input.
            if (bufferOutput != null) bufferOutput.detach();
            if (retain && failure == null && detachFailure == null) state.set(BUFFER_RETRY);
            else {
                state.set(CLOSED);
                try { super.close(); }
                catch (RuntimeException | Error error) {
                    if (detachFailure == null) detachFailure = error;
                    else cleanup(detachFailure, error);
                }
            }
            if (detachFailure != null) cleanup(failure, detachFailure);
        }

        @Override
        public void close() {
            for (;;) {
                int current = state.get();
                if (current == DIRECT) throw new IllegalStateException("Cannot close direct JSON result while writeTo is active");
                if (current == CLOSED) return;
                if (current == DTO) { super.close(); return; }
                if (state.compareAndSet(current, CLOSED)) { super.close(); return; }
            }
        }
    }

    /** Counts the complete bounded JSON body while copying only the caller's available prefix. */
    private static final class BufferOutput extends OutputStream {
        private ByteBuffer target;
        private int offset, capacity, count;

        void attach(ByteBuffer target, int offset) {
            this.target = target;
            this.offset = offset;
            capacity = target.limit() - offset;
            count = 0;
        }

        void detach() { target = null; offset = capacity = count = 0; }

        @Override public void write(int value) {
            if (count < capacity) target.put(offset + count, (byte) value);
            count = Math.incrementExact(count);
        }

        @Override public void write(byte[] bytes, int start, int length) {
            Objects.checkFromIndexSize(start, length, bytes.length);
            int copy = Math.min(length, Math.max(0, capacity - count));
            if (copy != 0) target.put(offset + count, bytes, start, copy);
            count = Math.addExact(count, length);
        }
    }
}
