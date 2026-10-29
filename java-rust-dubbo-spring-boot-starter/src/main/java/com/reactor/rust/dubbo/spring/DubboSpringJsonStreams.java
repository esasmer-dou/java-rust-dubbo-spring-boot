package com.reactor.rust.dubbo.spring;

import com.reactor.rust.dubbo.runtime.DubboDirectJsonStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;

/** Framework-neutral HTTP output adapter; does not load Jackson or start worker threads. */
public final class DubboSpringJsonStreams {
    private static final byte[] NULL = {'n', 'u', 'l', 'l'};
    private DubboSpringJsonStreams() {}

    public static DubboDirectJsonStream directJson(List<?> result) {
        if (!(result instanceof DubboDirectJsonStream stream)) {
            throw new IllegalArgumentException("Result requires @DubboStreamed(directJson=true); no DTO fallback is available");
        }
        return stream;
    }

    /** Writes and closes the native result, never the HTTP stream; null RPC values produce JSON null. */
    public static long write(List<?> result, OutputStream output) throws IOException {
        if (result == null) { Objects.requireNonNull(output, "output").write(NULL); return NULL.length; }
        try (DubboDirectJsonStream stream = directJson(result)) {
            return stream.writeTo(Objects.requireNonNull(output, "output"));
        }
    }
}
