package com.reactor.rust.dubbo.runtime;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public abstract class GeneratedDubboClientSupport {
    public static final int RESPONSE_WITH_EXCEPTION = 0;
    public static final int RESPONSE_VALUE = 1;
    public static final int RESPONSE_NULL_VALUE = 2;
    public static final int RESPONSE_WITH_EXCEPTION_WITH_ATTACHMENTS = 3;
    public static final int RESPONSE_VALUE_WITH_ATTACHMENTS = 4;
    public static final int RESPONSE_NULL_VALUE_WITH_ATTACHMENTS = 5;

    private final int clientId;
    private final int timeoutMs;
    private final int maxCollectionItems;
    private final DubboCallBufferPool buffers;

    protected GeneratedDubboClientSupport(int clientId, int timeoutMs, int maxCollectionItems,
                                          int initialBufferBytes, int retainedBuffers,
                                          int maxRetainedBufferBytes) {
        this.clientId = clientId;
        this.timeoutMs = timeoutMs;
        this.maxCollectionItems = maxCollectionItems;
        buffers = new DubboCallBufferPool(initialBufferBytes, retainedBuffers,
                maxRetainedBufferBytes);
    }

    protected final DubboCallBuffer acquireCallBuffer() {
        return buffers.acquire();
    }

    protected final NativeResponse invoke(int methodId, DubboCallBuffer call) {
        return invoke(methodId, call, 0);
    }

    /** Uses the configured client timeout when {@code methodTimeoutMs} is zero. */
    protected final NativeResponse invoke(int methodId, DubboCallBuffer call, int methodTimeoutMs) {
        return NativeDubboBridge.invoke(clientId, methodId, call.requestBuffer(),
                call.requestLength(), effectiveTimeout(methodTimeoutMs, timeoutMs));
    }

    static int effectiveTimeout(int methodTimeoutMs, int configuredTimeoutMs) {
        if (methodTimeoutMs < 0) {
            throw new IllegalArgumentException("methodTimeoutMs must not be negative");
        }
        return methodTimeoutMs == 0 ? configuredTimeoutMs : methodTimeoutMs;
    }

    protected final Hessian2Input responseInput(NativeResponse response, DubboCallBuffer call) {
        if (response.status() != 20) {
            Hessian2Input error = call.responseInput(response, maxCollectionItems);
            String message;
            try {
                message = error.readString();
            } catch (RuntimeException ignored) {
                message = "Dubbo provider returned status " + response.status();
            }
            throw new DubboNativeException(message);
        }
        return call.responseInput(response, maxCollectionItems);
    }

    protected final <E> List<E> invokeStreamedList(
            int methodId, DubboCallBuffer call, NativeResponseDecoder<E> itemDecoder) {
        return invokeStreamedList(methodId, call, 0, itemDecoder);
    }

    /**
     * Limits the wait for the complete native response frame, not subsequent list consumption.
     * A zero method timeout inherits the configured client timeout. This does not impose a
     * provider execution deadline or cancel business work already running at the provider.
     */
    protected final <E> List<E> invokeStreamedList(
            int methodId, DubboCallBuffer call, int methodTimeoutMs,
            NativeResponseDecoder<E> itemDecoder) {
        NativeResponse response = invoke(methodId, call, methodTimeoutMs);
        Hessian2Input input = new Hessian2Input();
        boolean ownershipTransferred = false;
        try {
            Hessian2Input current = responseInput(response, input);
            int responseFlag = requireValueResponse(current);
            if (isNullResponse(responseFlag) || current.readNull()) {
                return null;
            }
            int size = current.readListStart();
            List<E> result = new NativeStreamingResultList<>(
                    response, current, size, itemDecoder);
            ownershipTransferred = true;
            return result;
        } finally {
            if (!ownershipTransferred) {
                input.detach();
                response.close();
            }
        }
    }

    protected final <E> List<E> invokeDirectJsonList(
            int methodId, DubboCallBuffer call, NativeResponseDecoder<E> itemDecoder, Hessian2JsonWriter itemWriter) {
        return invokeDirectJsonList(methodId, call, 0, itemDecoder, itemWriter);
    }

    /**
     * Limits the wait for the complete native response frame, not subsequent JSON projection,
     * result consumption or HTTP output. A zero method timeout inherits the client timeout;
     * it is not a provider execution deadline or an output deadline.
     */
    protected final <E> List<E> invokeDirectJsonList(
            int methodId, DubboCallBuffer call, int methodTimeoutMs,
            NativeResponseDecoder<E> itemDecoder, Hessian2JsonWriter itemWriter) {
        NativeResponsePolicy.requireCurrent();
        NativeResponse response = invoke(methodId, call, methodTimeoutMs);
        Hessian2Input input = new Hessian2Input();
        boolean transferred = false;
        try {
            responseInput(response, input);
            int flag = requireValueResponse(input);
            if (isNullResponse(flag) || input.readNull()) return null;
            List<E> result = DirectJsonResults.open(input, itemDecoder, itemWriter, response::close,
                    flag == RESPONSE_VALUE_WITH_ATTACHMENTS);
            transferred = true;
            return result;
        } finally {
            if (!transferred) {
                try { input.detach(); } finally { response.close(); }
            }
        }
    }

    protected final <T> CompletableFuture<T> invokeAsync(
            int methodId, DubboCallBuffer call, NativeResponseDecoder<T> decoder) {
        return invokeAsync(methodId, call, 0, decoder);
    }

    /** Uses the configured client timeout when {@code methodTimeoutMs} is zero. */
    protected final <T> CompletableFuture<T> invokeAsync(
            int methodId, DubboCallBuffer call, int methodTimeoutMs,
            NativeResponseDecoder<T> decoder) {
        CompletableFuture<T> result = new CompletableFuture<>();
        NativeDubboAsyncCallback callback = new NativeDubboAsyncCallback() {
            private final Hessian2Input input = new Hessian2Input();

            @Override
            public void complete(long responseHandle) {
                try (NativeResponse response = NativeDubboBridge.takeResponse(responseHandle)) {
                    Hessian2Input current = responseInput(response, input);
                    result.complete(decoder.decode(current));
                } catch (Throwable error) {
                    result.completeExceptionally(error);
                } finally {
                    input.detach();
                }
            }

            @Override
            public void fail(String message) {
                result.completeExceptionally(new DubboNativeException(message));
            }
        };
        try {
            NativeDubboBridge.invokeAsync(clientId, methodId, call.requestBuffer(),
                    call.requestLength(), effectiveTimeout(methodTimeoutMs, timeoutMs), callback);
        } catch (Throwable error) {
            result.completeExceptionally(error);
        }
        return result;
    }

    private Hessian2Input responseInput(NativeResponse response, Hessian2Input input) {
        if (response.status() != 20) {
            input.attach(response.body(), response.body().capacity(), maxCollectionItems);
            String message;
            try {
                message = input.readString();
            } catch (RuntimeException ignored) {
                message = "Dubbo provider returned status " + response.status();
            }
            throw new DubboNativeException(message);
        }
        input.attach(response.body(), response.body().capacity(), maxCollectionItems);
        return input;
    }

    protected static int requireValueResponse(Hessian2Input input) {
        int flag = input.readInt();
        if (flag == RESPONSE_WITH_EXCEPTION || flag == RESPONSE_WITH_EXCEPTION_WITH_ATTACHMENTS) {
            Object payload;
            try {
                payload = input.readDynamic();
            } catch (RuntimeException decodingFailure) {
                throw new DubboNativeException(
                        "Dubbo provider returned an unreadable business exception", decodingFailure);
            }
            throw DubboRemoteBusinessException.fromPayload(payload);
        }
        if (flag != RESPONSE_VALUE && flag != RESPONSE_VALUE_WITH_ATTACHMENTS
                && flag != RESPONSE_NULL_VALUE && flag != RESPONSE_NULL_VALUE_WITH_ATTACHMENTS) {
            throw new DubboCodecException("Unsupported Dubbo response flag " + flag);
        }
        return flag;
    }

    protected static boolean isNullResponse(int flag) {
        return flag == RESPONSE_NULL_VALUE || flag == RESPONSE_NULL_VALUE_WITH_ATTACHMENTS;
    }
}
