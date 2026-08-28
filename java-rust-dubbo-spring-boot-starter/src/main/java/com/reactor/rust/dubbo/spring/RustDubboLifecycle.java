package com.reactor.rust.dubbo.spring;

import com.reactor.rust.dubbo.runtime.DubboProviderRegistry;
import com.reactor.rust.dubbo.runtime.NativeDubboRuntime;
import org.springframework.context.SmartLifecycle;

final class RustDubboLifecycle implements SmartLifecycle {
    private final NativeDubboRuntime runtime;
    private final GeneratedModuleCoordinator coordinator;
    private final DubboProviderRegistry providers;
    private final RustDubboProperties properties;
    private volatile boolean running;

    RustDubboLifecycle(NativeDubboRuntime runtime, GeneratedModuleCoordinator coordinator,
                       DubboProviderRegistry providers, RustDubboProperties properties) {
        this.runtime = runtime;
        this.coordinator = coordinator;
        this.providers = providers;
        this.properties = properties;
    }

    @Override
    public void start() {
        coordinator.ensureClients();
        coordinator.ensureProviders();
        RustDubboProperties.Provider provider = properties.getProvider();
        if (provider.isEnabled()) {
            runtime.startProvider(provider.getPort(), provider.getIoWorkers(),
                    provider.getBusinessWorkers(), provider.getQueueCapacity(),
                    provider.getMaxPayloadBytes(), provider.getRequestTimeoutMs(),
                    provider.getDrainTimeoutMs(), providers);
        }
        RustDubboProperties.Consumer consumer = properties.getConsumer();
        if (consumer.isStartupCheck()) {
            runtime.awaitClientsReady(consumer.getStartupTimeoutMs());
        }
        running = true;
    }

    @Override
    public void stop() {
        runtime.close();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }
}
