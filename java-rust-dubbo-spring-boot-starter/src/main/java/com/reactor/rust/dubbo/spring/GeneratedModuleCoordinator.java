package com.reactor.rust.dubbo.spring;

import com.reactor.rust.dubbo.runtime.DubboClientRegistry;
import com.reactor.rust.dubbo.runtime.DubboBeanResolver;
import com.reactor.rust.dubbo.runtime.DubboProviderRegistry;
import com.reactor.rust.dubbo.runtime.GeneratedDubboModule;
import com.reactor.rust.dubbo.runtime.NativeDubboRuntime;
import java.util.List;

final class GeneratedModuleCoordinator {
    private final List<GeneratedDubboModule> modules;
    private final DubboClientRegistry clients;
    private final DubboProviderRegistry providers;
    private final NativeDubboRuntime runtime;
    private final DubboBeanResolver beans;
    private boolean clientsReady;
    private boolean providersReady;

    GeneratedModuleCoordinator(List<GeneratedDubboModule> modules, DubboClientRegistry clients,
                               DubboProviderRegistry providers, NativeDubboRuntime runtime,
                               DubboBeanResolver beans) {
        this.modules = List.copyOf(modules);
        this.clients = clients;
        this.providers = providers;
        this.runtime = runtime;
        this.beans = beans;
    }

    synchronized void ensureClients() {
        if (clientsReady) {
            return;
        }
        for (GeneratedDubboModule module : modules) {
            module.registerClients(clients, runtime);
        }
        clients.seal();
        clientsReady = true;
    }

    synchronized void ensureProviders() {
        if (providersReady) {
            return;
        }
        for (GeneratedDubboModule module : modules) {
            module.registerProviders(providers, runtime, beans);
        }
        providersReady = true;
    }
}
