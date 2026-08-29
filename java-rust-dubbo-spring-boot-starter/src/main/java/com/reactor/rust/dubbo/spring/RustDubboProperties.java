package com.reactor.rust.dubbo.spring;

import com.reactor.rust.dubbo.runtime.DubboClientOptions;
import com.reactor.rust.dubbo.runtime.DubboClientKey;
import com.reactor.rust.dubbo.runtime.DubboClientRoutingOptions;
import com.reactor.rust.dubbo.runtime.DubboRuntimeOptions;
import com.reactor.rust.dubbo.runtime.DubboProviderOptions;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;

@ConfigurationProperties("reactor.dubbo")
public final class RustDubboProperties {
    private boolean enabled = true;
    private String profile = "micro";
    private final Consumer consumer = new Consumer();
    private final Provider provider = new Provider();
    private final Runtime runtime = new Runtime();
    private boolean profileResolved;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    public Provider getProvider() {
        return provider;
    }

    public Runtime getRuntime() {
        return runtime;
    }

    DubboClientOptions clientOptions() {
        return new DubboClientOptions(consumer.providers, consumer.connectionsPerEndpoint,
                consumer.commandQueueCapacity, consumer.maxInFlight,
                consumer.heartbeatIntervalMs, consumer.timeoutMs,
                consumer.maxPayloadBytes,
                consumer.maxCollectionItems, consumer.initialBufferBytes,
                consumer.retainedBuffers, consumer.maxRetainedBufferBytes);
    }

    DubboClientRoutingOptions clientRoutingOptions() {
        DubboClientOptions defaults = clientOptions();
        Map<DubboClientKey, DubboClientOptions> routes = new LinkedHashMap<>();
        consumer.routes.forEach((name, route) -> {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Dubbo consumer route name must not be blank");
            }
            DubboClientKey key = route.key(name);
            DubboClientOptions previous = routes.putIfAbsent(key, route.options(name, defaults));
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Duplicate Dubbo consumer route for " + key + " (route " + name + ")");
            }
        });
        return new DubboClientRoutingOptions(defaults, routes, consumer.requireExplicitRoutes);
    }

    DubboRuntimeOptions runtimeOptions() {
        return new DubboRuntimeOptions(runtime.ioWorkers, runtime.callbackWorkers,
                runtime.callbackQueueCapacity, runtime.threadStackBytes);
    }

    DubboProviderOptions providerOptions() {
        Map<String, Integer> limits = new LinkedHashMap<>();
        provider.executors.forEach((name, lane) -> limits.put(name, lane.maxConcurrent));
        return new DubboProviderOptions(provider.defaultMaxConcurrent, limits);
    }

    synchronized void resolveProfile(Environment environment) {
        if (profileResolved) {
            return;
        }
        String selected = profile == null ? "micro" : profile.trim().toLowerCase(java.util.Locale.ROOT);
        switch (selected) {
            case "micro" -> { }
            case "balanced" -> applyBalanced(environment);
            case "throughput" -> applyThroughput(environment);
            default -> throw new IllegalArgumentException(
                    "reactor.dubbo.profile must be micro, balanced or throughput: " + profile);
        }
        profile = selected;
        profileResolved = true;
    }

    private void applyBalanced(Environment environment) {
        setIfMissing(environment, "reactor.dubbo.runtime.io-workers", () -> runtime.ioWorkers = 2);
        setIfMissing(environment, "reactor.dubbo.runtime.callback-workers", () -> runtime.callbackWorkers = 2);
        setIfMissing(environment, "reactor.dubbo.runtime.callback-queue-capacity",
                () -> runtime.callbackQueueCapacity = 512);
        setIfMissing(environment, "reactor.dubbo.consumer.command-queue-capacity",
                () -> consumer.commandQueueCapacity = 128);
        setIfMissing(environment, "reactor.dubbo.consumer.max-in-flight", () -> consumer.maxInFlight = 256);
        setIfMissing(environment, "reactor.dubbo.consumer.retained-buffers", () -> consumer.retainedBuffers = 32);
        setIfMissing(environment, "reactor.dubbo.consumer.max-retained-buffer-bytes",
                () -> consumer.maxRetainedBufferBytes = 128 * 1024);
        setIfMissing(environment, "reactor.dubbo.provider.io-workers", () -> provider.ioWorkers = 2);
        setIfMissing(environment, "reactor.dubbo.provider.business-workers", () -> provider.businessWorkers = 8);
        setIfMissing(environment, "reactor.dubbo.provider.queue-capacity", () -> provider.queueCapacity = 256);
        setIfMissing(environment, "reactor.dubbo.provider.default-max-concurrent",
                () -> provider.defaultMaxConcurrent = 128);
    }

    private void applyThroughput(Environment environment) {
        setIfMissing(environment, "reactor.dubbo.runtime.io-workers", () -> runtime.ioWorkers = 4);
        setIfMissing(environment, "reactor.dubbo.runtime.callback-workers", () -> runtime.callbackWorkers = 4);
        setIfMissing(environment, "reactor.dubbo.runtime.callback-queue-capacity",
                () -> runtime.callbackQueueCapacity = 2_048);
        setIfMissing(environment, "reactor.dubbo.runtime.thread-stack-bytes",
                () -> runtime.threadStackBytes = 512 * 1024);
        setIfMissing(environment, "reactor.dubbo.consumer.connections-per-endpoint",
                () -> consumer.connectionsPerEndpoint = 4);
        setIfMissing(environment, "reactor.dubbo.consumer.command-queue-capacity",
                () -> consumer.commandQueueCapacity = 256);
        setIfMissing(environment, "reactor.dubbo.consumer.max-in-flight", () -> consumer.maxInFlight = 1_024);
        setIfMissing(environment, "reactor.dubbo.consumer.retained-buffers", () -> consumer.retainedBuffers = 64);
        setIfMissing(environment, "reactor.dubbo.consumer.max-retained-buffer-bytes",
                () -> consumer.maxRetainedBufferBytes = 256 * 1024);
        setIfMissing(environment, "reactor.dubbo.provider.io-workers", () -> provider.ioWorkers = 4);
        setIfMissing(environment, "reactor.dubbo.provider.business-workers", () -> provider.businessWorkers = 16);
        setIfMissing(environment, "reactor.dubbo.provider.queue-capacity", () -> provider.queueCapacity = 1_024);
        setIfMissing(environment, "reactor.dubbo.provider.default-max-concurrent",
                () -> provider.defaultMaxConcurrent = 512);
    }

    private static void setIfMissing(Environment environment, String key, Runnable setter) {
        if (!environment.containsProperty(key)) {
            setter.run();
        }
    }

    public static final class Runtime {
        private int ioWorkers = 1;
        private int callbackWorkers = 1;
        private int callbackQueueCapacity = 256;
        private int threadStackBytes = 256 * 1024;

        public int getIoWorkers() { return ioWorkers; }
        public void setIoWorkers(int value) { ioWorkers = value; }
        public int getCallbackWorkers() { return callbackWorkers; }
        public void setCallbackWorkers(int value) { callbackWorkers = value; }
        public int getCallbackQueueCapacity() { return callbackQueueCapacity; }
        public void setCallbackQueueCapacity(int value) { callbackQueueCapacity = value; }
        public int getThreadStackBytes() { return threadStackBytes; }
        public void setThreadStackBytes(int value) { threadStackBytes = value; }
    }

    public static final class Consumer {
        private String providers = "127.0.0.1:20880";
        private int connectionsPerEndpoint = 2;
        private int commandQueueCapacity = 32;
        private int maxInFlight = 64;
        private int heartbeatIntervalMs = 30_000;
        private int timeoutMs = 3_000;
        private int maxPayloadBytes = 8 * 1024 * 1024;
        private int maxCollectionItems = 100_000;
        private int initialBufferBytes = 1024;
        private int retainedBuffers = 16;
        private int maxRetainedBufferBytes = 64 * 1024;
        private boolean startupCheck = true;
        private int startupTimeoutMs = 3_000;
        private boolean requireExplicitRoutes;
        private final Map<String, ConsumerRoute> routes = new LinkedHashMap<>();

        public String getProviders() { return providers; }
        public void setProviders(String providers) { this.providers = providers; }
        public int getConnectionsPerEndpoint() { return connectionsPerEndpoint; }
        public void setConnectionsPerEndpoint(int value) { connectionsPerEndpoint = value; }
        public int getCommandQueueCapacity() { return commandQueueCapacity; }
        public void setCommandQueueCapacity(int value) { commandQueueCapacity = value; }
        public int getMaxInFlight() { return maxInFlight; }
        public void setMaxInFlight(int value) { maxInFlight = value; }
        public int getHeartbeatIntervalMs() { return heartbeatIntervalMs; }
        public void setHeartbeatIntervalMs(int value) { heartbeatIntervalMs = value; }
        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int value) { timeoutMs = value; }
        public int getMaxPayloadBytes() { return maxPayloadBytes; }
        public void setMaxPayloadBytes(int value) { maxPayloadBytes = value; }
        public int getMaxCollectionItems() { return maxCollectionItems; }
        public void setMaxCollectionItems(int value) { maxCollectionItems = value; }
        public int getInitialBufferBytes() { return initialBufferBytes; }
        public void setInitialBufferBytes(int value) { initialBufferBytes = value; }
        public int getRetainedBuffers() { return retainedBuffers; }
        public void setRetainedBuffers(int value) { retainedBuffers = value; }
        public int getMaxRetainedBufferBytes() { return maxRetainedBufferBytes; }
        public void setMaxRetainedBufferBytes(int value) { maxRetainedBufferBytes = value; }
        public boolean isStartupCheck() { return startupCheck; }
        public void setStartupCheck(boolean value) { startupCheck = value; }
        public int getStartupTimeoutMs() { return startupTimeoutMs; }
        public void setStartupTimeoutMs(int value) { startupTimeoutMs = value; }
        public boolean isRequireExplicitRoutes() { return requireExplicitRoutes; }
        public void setRequireExplicitRoutes(boolean value) { requireExplicitRoutes = value; }
        public Map<String, ConsumerRoute> getRoutes() { return routes; }
    }

    public static final class ConsumerRoute {
        private String interfaceName;
        private String group = "";
        private String version = "";
        private String providers;
        private Integer connectionsPerEndpoint;
        private Integer commandQueueCapacity;
        private Integer maxInFlight;
        private Integer heartbeatIntervalMs;
        private Integer timeoutMs;
        private Integer maxPayloadBytes;
        private Integer maxCollectionItems;
        private Integer initialBufferBytes;
        private Integer retainedBuffers;
        private Integer maxRetainedBufferBytes;

        DubboClientKey key(String routeName) {
            if (interfaceName == null || interfaceName.isBlank()) {
                throw new IllegalArgumentException(
                        "reactor.dubbo.consumer.routes." + routeName + ".interface-name must not be blank");
            }
            return new DubboClientKey(interfaceName, group, version);
        }

        DubboClientOptions options(String routeName, DubboClientOptions defaults) {
            if (providers == null || providers.isBlank()) {
                throw new IllegalArgumentException(
                        "reactor.dubbo.consumer.routes." + routeName + ".providers must not be blank");
            }
            return new DubboClientOptions(providers,
                    inherit(connectionsPerEndpoint, defaults.connectionsPerEndpoint()),
                    inherit(commandQueueCapacity, defaults.commandQueueCapacity()),
                    inherit(maxInFlight, defaults.maxInFlight()),
                    inherit(heartbeatIntervalMs, defaults.heartbeatIntervalMs()),
                    inherit(timeoutMs, defaults.timeoutMs()),
                    inherit(maxPayloadBytes, defaults.maxPayloadBytes()),
                    inherit(maxCollectionItems, defaults.maxCollectionItems()),
                    inherit(initialBufferBytes, defaults.initialBufferBytes()),
                    inherit(retainedBuffers, defaults.retainedBuffers()),
                    inherit(maxRetainedBufferBytes, defaults.maxRetainedBufferBytes()));
        }

        private static int inherit(Integer value, int defaultValue) {
            return value == null ? defaultValue : value;
        }

        public String getInterfaceName() { return interfaceName; }
        public void setInterfaceName(String value) { interfaceName = value; }
        public String getGroup() { return group; }
        public void setGroup(String value) { group = value; }
        public String getVersion() { return version; }
        public void setVersion(String value) { version = value; }
        public String getProviders() { return providers; }
        public void setProviders(String value) { providers = value; }
        public Integer getConnectionsPerEndpoint() { return connectionsPerEndpoint; }
        public void setConnectionsPerEndpoint(Integer value) { connectionsPerEndpoint = value; }
        public Integer getCommandQueueCapacity() { return commandQueueCapacity; }
        public void setCommandQueueCapacity(Integer value) { commandQueueCapacity = value; }
        public Integer getMaxInFlight() { return maxInFlight; }
        public void setMaxInFlight(Integer value) { maxInFlight = value; }
        public Integer getHeartbeatIntervalMs() { return heartbeatIntervalMs; }
        public void setHeartbeatIntervalMs(Integer value) { heartbeatIntervalMs = value; }
        public Integer getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(Integer value) { timeoutMs = value; }
        public Integer getMaxPayloadBytes() { return maxPayloadBytes; }
        public void setMaxPayloadBytes(Integer value) { maxPayloadBytes = value; }
        public Integer getMaxCollectionItems() { return maxCollectionItems; }
        public void setMaxCollectionItems(Integer value) { maxCollectionItems = value; }
        public Integer getInitialBufferBytes() { return initialBufferBytes; }
        public void setInitialBufferBytes(Integer value) { initialBufferBytes = value; }
        public Integer getRetainedBuffers() { return retainedBuffers; }
        public void setRetainedBuffers(Integer value) { retainedBuffers = value; }
        public Integer getMaxRetainedBufferBytes() { return maxRetainedBufferBytes; }
        public void setMaxRetainedBufferBytes(Integer value) { maxRetainedBufferBytes = value; }
    }

    public static final class Provider {
        private boolean enabled;
        private int port = 20_880;
        private int ioWorkers = 1;
        private int businessWorkers = 4;
        private int queueCapacity = 64;
        private int maxPayloadBytes = 8 * 1024 * 1024;
        private int requestTimeoutMs = 30_000;
        private int drainTimeoutMs = 10_000;
        private int defaultMaxConcurrent = 16;
        private final Map<String, ExecutorLane> executors = new LinkedHashMap<>();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { enabled = value; }
        public int getPort() { return port; }
        public void setPort(int value) { port = value; }
        public int getIoWorkers() { return ioWorkers; }
        public void setIoWorkers(int value) { ioWorkers = value; }
        public int getBusinessWorkers() { return businessWorkers; }
        public void setBusinessWorkers(int value) { businessWorkers = value; }
        public int getQueueCapacity() { return queueCapacity; }
        public void setQueueCapacity(int value) { queueCapacity = value; }
        public int getMaxPayloadBytes() { return maxPayloadBytes; }
        public void setMaxPayloadBytes(int value) { maxPayloadBytes = value; }
        public int getRequestTimeoutMs() { return requestTimeoutMs; }
        public void setRequestTimeoutMs(int value) { requestTimeoutMs = value; }
        public int getDrainTimeoutMs() { return drainTimeoutMs; }
        public void setDrainTimeoutMs(int value) { drainTimeoutMs = value; }
        public int getDefaultMaxConcurrent() { return defaultMaxConcurrent; }
        public void setDefaultMaxConcurrent(int value) { defaultMaxConcurrent = value; }
        public Map<String, ExecutorLane> getExecutors() { return executors; }
    }

    public static final class ExecutorLane {
        private int maxConcurrent = 16;

        public int getMaxConcurrent() { return maxConcurrent; }
        public void setMaxConcurrent(int value) { maxConcurrent = value; }
    }
}
