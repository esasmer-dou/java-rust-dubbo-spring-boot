package com.reactor.rust.dubbo.runtime;

/** Generated once per application compilation unit. */
public interface GeneratedDubboModule {
    void registerClients(DubboClientRegistry clients, NativeDubboRuntime runtime);

    void registerProviders(DubboProviderRegistry providers, NativeDubboRuntime runtime,
                           DubboBeanResolver beans);
}
