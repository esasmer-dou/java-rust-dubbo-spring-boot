package com.reactor.rust.dubbo.runtime;

/** Invoked only by the bounded native callback pool, never by a Tokio I/O worker. */
public interface NativeDubboAsyncCallback {
    void complete(long responseHandle);

    void fail(String message);
}
