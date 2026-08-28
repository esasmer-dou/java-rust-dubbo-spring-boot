package com.reactor.rust.dubbo.runtime;

import java.nio.ByteBuffer;

/** Called only by bounded native business workers, never by Tokio I/O workers. */
public interface NativeDubboDispatcher {
    int dispatch(
            int serviceId,
            int methodId,
            ByteBuffer request,
            int requestLength,
            long responseHandle,
            ByteBuffer response);
}
