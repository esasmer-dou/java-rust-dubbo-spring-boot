package com.reactor.rust.dubbo.runtime;

import java.util.ArrayList;
import java.util.List;

public final class NativeDubboRuntime implements AutoCloseable {
    private final DubboClientOptions clientOptions;
    private final DubboProviderOptions providerOptions;
    private final List<ClientRegistration> clients = new ArrayList<>();
    private long providerHandle;
    private int providerDrainTimeoutMs = 10_000;
    private boolean closed;

    public NativeDubboRuntime() {
        this(DubboClientOptions.microDefaults(), DubboRuntimeOptions.microDefaults(),
                DubboProviderOptions.microDefaults());
    }

    public NativeDubboRuntime(DubboClientOptions clientOptions) {
        this(clientOptions, DubboRuntimeOptions.microDefaults(), DubboProviderOptions.microDefaults());
    }

    public NativeDubboRuntime(DubboClientOptions clientOptions, DubboRuntimeOptions runtimeOptions) {
        this(clientOptions, runtimeOptions, DubboProviderOptions.microDefaults());
    }

    public NativeDubboRuntime(DubboClientOptions clientOptions, DubboRuntimeOptions runtimeOptions,
                              DubboProviderOptions providerOptions) {
        this.clientOptions = clientOptions;
        this.providerOptions = providerOptions;
        NativeDubboBridge.configure(runtimeOptions);
    }

    public DubboClientOptions clientOptions() {
        return clientOptions;
    }

    public synchronized int createClient(String service, String group, String version) {
        return createClient(service, group, version, true);
    }

    public synchronized int createClient(String service, String group, String version,
                                         boolean startupCheck) {
        return createClient(service, group, version, clientOptions.providers(),
                clientOptions.connectionsPerEndpoint(), clientOptions.commandQueueCapacity(),
                clientOptions.maxInFlight(), clientOptions.heartbeatIntervalMs(),
                clientOptions.maxPayloadBytes(), startupCheck);
    }

    public synchronized int createClient(String service, String group, String version, String providers,
                                         int connectionsPerEndpoint, int commandQueueCapacity,
                                         int maxInFlight, int heartbeatIntervalMs,
                                         int maxPayloadBytes, boolean startupCheck) {
        ensureOpen();
        int clientId = NativeDubboBridge.createClient(service, group, version, providers,
                connectionsPerEndpoint, commandQueueCapacity, maxInFlight,
                heartbeatIntervalMs, maxPayloadBytes);
        clients.add(new ClientRegistration(clientId, startupCheck));
        return clientId;
    }

    public void registerMethod(int clientId, int methodId, String method, String descriptor) {
        ensureOpen();
        NativeDubboBridge.registerMethod(clientId, methodId, method, descriptor);
    }

    public synchronized boolean clientsReady() {
        ensureOpen();
        for (ClientRegistration client : clients) {
            if (client.startupCheck() && !NativeDubboBridge.clientReady(client.id())) {
                return false;
            }
        }
        return true;
    }

    public void awaitClientsReady(int timeoutMs) {
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("startupTimeoutMs must be positive");
        }
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (!clientsReady()) {
            if (System.nanoTime() >= deadline) {
                throw new DubboNativeException(
                        "Native Dubbo providers were not reachable within " + timeoutMs + " ms");
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new DubboNativeException("Interrupted while waiting for native Dubbo providers",
                        interrupted);
            }
        }
    }

    public synchronized boolean providerRunning() {
        return !closed && providerHandle != 0;
    }

    public String metricsJson() {
        ensureOpen();
        return NativeDubboBridge.metricsJson();
    }

    public void registerProviderMethod(int serviceId, int methodId, String service, String group,
                                       String version, String method, String descriptor,
                                       String executor, int annotatedMaxConcurrent) {
        ensureOpen();
        NativeDubboBridge.registerProviderMethod(serviceId, methodId, service, group, version,
                method, descriptor, executor, annotatedMaxConcurrent > 0
                        ? annotatedMaxConcurrent : providerOptions.maxConcurrent(executor));
    }

    public synchronized void startProvider(int port, int ioWorkers, int businessWorkers,
                                           int queueCapacity, int maxPayloadBytes,
                                           int requestTimeoutMs, int drainTimeoutMs,
                                           NativeDubboDispatcher dispatcher) {
        ensureOpen();
        if (providerHandle != 0) {
            throw new IllegalStateException("Native Dubbo provider is already running");
        }
        if (drainTimeoutMs < 0) {
            throw new IllegalArgumentException("drainTimeoutMs must not be negative");
        }
        providerHandle = NativeDubboBridge.startProvider(port, ioWorkers, businessWorkers,
                queueCapacity, maxPayloadBytes, requestTimeoutMs, dispatcher);
        providerDrainTimeoutMs = drainTimeoutMs;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (providerHandle != 0) {
            NativeDubboBridge.stopProvider(providerHandle, providerDrainTimeoutMs);
            providerHandle = 0;
        }
        for (ClientRegistration client : clients) {
            NativeDubboBridge.closeClient(client.id());
        }
        clients.clear();
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Native Dubbo runtime is closed");
        }
    }

    private record ClientRegistration(int id, boolean startupCheck) {
    }
}
