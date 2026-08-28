package com.reactor.rust.dubbo.runtime;

import java.nio.ByteBuffer;

public abstract class GeneratedDubboProviderSupport implements NativeDubboDispatcher {
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

    private static final class CodecContext {
        private final Hessian2Input input = new Hessian2Input();
        private final Hessian2Output output = new Hessian2Output();
    }
}
