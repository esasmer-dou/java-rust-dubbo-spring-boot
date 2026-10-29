package com.reactor.rust.dubbo.runtime;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/** Mutually exclusive stream or capacity-retry buffer consumption of one native response. */
public interface DubboDirectJsonStream extends AutoCloseable {
    /** One-pass write, closing this result on success or failure, never the output. */
    long writeTo(OutputStream output) throws IOException;

    /**
     * Writes at offset without changing the buffer position/limit. Returns body bytes,
     * or negative required body bytes when limit - offset is insufficient. Only that
     * negative result permits one retry with at least the reported writable space.
     * The retry rereads the same native response; it never repeats the RPC. Partial
     * bytes from a negative result must be discarded. Close on an abandoned retry.
     * First use permanently excludes List/DTO and OutputStream consumption. Success,
     * invalid arguments and serialization failures close this result. Callers must
     * not write concurrently or mutate the retained wire response between attempts.
     */
    default int writeTo(ByteBuffer output, int offset) {
        throw new UnsupportedOperationException("Direct JSON buffer writing is not supported by this result");
    }
    @Override void close();
}
