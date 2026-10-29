package com.reactor.rust.dubbo.runtime;

import java.util.ServiceLoader;

/** Startup-only assembly of plain-Java APT clients; no Spring, scanning or runtime proxy. */
public final class StandaloneDubboClients implements AutoCloseable {
    private final NativeDubboRuntime runtime;
    private final DubboClientRegistry clients = new DubboClientRegistry();

    public StandaloneDubboClients(DubboClientOptions options, DubboRuntimeOptions runtimeOptions) {
        java.util.Objects.requireNonNull(options, "options");
        java.util.Objects.requireNonNull(runtimeOptions, "runtimeOptions");
        NativeResponsePolicy.requireCurrent();
        runtime = new NativeDubboRuntime(options, runtimeOptions);
        try {
            int count = 0;
            for (GeneratedDubboModule module : ServiceLoader.load(GeneratedDubboModule.class)) {
                module.registerClients(clients, runtime);
                count++;
            }
            if (count == 0) throw new IllegalStateException(
                    "No plain-Java Dubbo module; compile with -Areactor.dubbo.framework=plain");
            clients.seal();
        } catch (RuntimeException | Error failure) {
            runtime.close();
            throw failure;
        }
    }

    /** Resolve once during application startup and retain the typed client. */
    public <T> T require(Class<T> service, String group, String version) {
        return service.cast(clients.client(service.getName(), group, version));
    }

    @Override public void close() { runtime.close(); }
}
