package com.reactor.rust.dubbo.runtime;

import java.nio.ByteBuffer;

public abstract class GeneratedDubboProviderSupport implements NativeDubboDispatcher {
    private static final int MAX_PROVIDER_FAILURE_MESSAGE_CHARS = 4096;

    protected static final int ASYNC_PENDING = -1;
    private final ThreadLocal<CodecContext> codecs = ThreadLocal.withInitial(CodecContext::new);
    private final int maxCollectionItems;

    protected GeneratedDubboProviderSupport(int maxCollectionItems) {
        this.maxCollectionItems = maxCollectionItems;
    }

    protected final Hessian2Input requestInput(ByteBuffer request, int requestLength) {
        CodecContext context = codecs.get();
        context.input.attach(request, requestLength, maxCollectionItems);
        return context.input;
    }

    protected final Hessian2Output responseOutput(long responseHandle, ByteBuffer response) {
        CodecContext context = codecs.get();
        context.output.attach(response, responseHandle);
        return context.output;
    }

    protected static void writeValueResponsePrefix(Hessian2Output output, Object value) {
        output.writeInt(value == null
                ? GeneratedDubboClientSupport.RESPONSE_NULL_VALUE
                : GeneratedDubboClientSupport.RESPONSE_VALUE);
    }

    protected static int writeBusinessExceptionResponse(Hessian2Output output, Throwable failure) {
        Throwable businessFailure = unwrapCompletionFailure(failure);
        if (businessFailure instanceof Error fatalFailure) {
            throw fatalFailure;
        }
        output.writeInt(GeneratedDubboClientSupport.RESPONSE_WITH_EXCEPTION);
        output.writeRemoteThrowable(businessFailure);
        return output.position();
    }

    protected static void completeBusinessExceptionResponse(long responseHandle, Throwable failure) {
        Throwable businessFailure = unwrapCompletionFailure(failure);
        if (businessFailure instanceof Error) {
            NativeDubboBridge.failProviderResponse(responseHandle,
                    providerFailureMessage(businessFailure));
            return;
        }
        try {
            Hessian2Output output = new Hessian2Output();
            output.attach(NativeDubboBridge.growProviderResponse(responseHandle, 1), responseHandle);
            int length = writeBusinessExceptionResponse(output, businessFailure);
            NativeDubboBridge.completeProviderResponse(responseHandle, length);
        } catch (RuntimeException encodingFailure) {
            NativeDubboBridge.failProviderResponse(responseHandle,
                    "Failed to encode provider business exception: "
                            + providerFailureMessage(encodingFailure));
        }
    }

    protected static String providerFailureMessage(Throwable failure) {
        Throwable current = unwrapCompletionFailure(failure);
        String message = current.getMessage();
        String rendered = message == null || message.isBlank()
                ? current.getClass().getName()
                : current.getClass().getName() + ": " + message;
        if (rendered.length() <= MAX_PROVIDER_FAILURE_MESSAGE_CHARS) {
            return rendered;
        }
        int end = MAX_PROVIDER_FAILURE_MESSAGE_CHARS;
        if (Character.isHighSurrogate(rendered.charAt(end - 1))) {
            end--;
        }
        return rendered.substring(0, end);
    }

    private static Throwable unwrapCompletionFailure(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; depth < 8; depth++) {
            if (!(current instanceof java.util.concurrent.CompletionException)
                    && !(current instanceof java.util.concurrent.ExecutionException)) {
                break;
            }
            Throwable cause = current.getCause();
            if (cause == null || cause == current) {
                break;
            }
            current = cause;
        }
        return current;
    }

    private static final class CodecContext {
        private final Hessian2Input input = new Hessian2Input();
        private final Hessian2Output output = new Hessian2Output();
    }
}
