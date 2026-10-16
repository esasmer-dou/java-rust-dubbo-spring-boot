package com.reactor.rust.dubbo.runtime;

/** Optional startup-resolved telemetry. No dependency on a particular agent or Java metric engine. */
@FunctionalInterface
public interface DubboProviderTelemetry {
    DubboProviderTelemetry NONE = (service, method) -> Method.NONE;

    Method method(String service, String method);

    interface Method {
        Method NONE = new Method() {
            public long start() { return 0; }
            public void complete(long startedAt, Throwable error) {}
        };
        long start();
        void complete(long startedAt, Throwable error);
    }
}
