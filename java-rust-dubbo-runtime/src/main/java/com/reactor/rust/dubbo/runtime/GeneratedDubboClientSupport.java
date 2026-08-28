package com.reactor.rust.dubbo.runtime;

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
        return NativeDubboBridge.invoke(clientId, methodId, call.requestBuffer(),
                call.requestLength(), timeoutMs);
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

    protected final <T> CompletableFuture<T> invokeAsync(
            int methodId, DubboCallBuffer call, NativeResponseDecoder<T> decoder) {
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
                }
            }

            @Override
            public void fail(String message) {
                result.completeExceptionally(new DubboNativeException(message));
            }
        };
        try {
            NativeDubboBridge.invokeAsync(clientId, methodId, call.requestBuffer(),
                    call.requestLength(), timeoutMs, callback);
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
            throw new DubboNativeException("Dubbo provider returned a business exception");
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
