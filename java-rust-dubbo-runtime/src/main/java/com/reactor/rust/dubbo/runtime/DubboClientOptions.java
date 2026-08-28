package com.reactor.rust.dubbo.runtime;

public record DubboClientOptions(
        String providers,
        int connectionsPerEndpoint,
        int commandQueueCapacity,
        int maxInFlight,
        int heartbeatIntervalMs,
        int timeoutMs,
        int maxPayloadBytes,
        int maxCollectionItems,
        int initialBufferBytes,
        int retainedBuffers,
        int maxRetainedBufferBytes) {

    public DubboClientOptions {
        if (providers == null || providers.isBlank()) {
            throw new IllegalArgumentException("providers must not be blank");
        }
        requirePositive(connectionsPerEndpoint, "connectionsPerEndpoint");
        requirePositive(commandQueueCapacity, "commandQueueCapacity");
        requirePositive(maxInFlight, "maxInFlight");
        if (heartbeatIntervalMs < 0) {
            throw new IllegalArgumentException("heartbeatIntervalMs must not be negative");
        }
        requirePositive(timeoutMs, "timeoutMs");
        requirePositive(maxPayloadBytes, "maxPayloadBytes");
        requirePositive(maxCollectionItems, "maxCollectionItems");
        requirePositive(initialBufferBytes, "initialBufferBytes");
        if (retainedBuffers < 0) {
            throw new IllegalArgumentException("retainedBuffers must not be negative");
        }
        if (maxRetainedBufferBytes < initialBufferBytes) {
            throw new IllegalArgumentException(
                    "maxRetainedBufferBytes must be at least initialBufferBytes");
        }
    }

    public static DubboClientOptions microDefaults() {
        return new DubboClientOptions("127.0.0.1:20880", 2, 32, 64, 30_000, 3_000,
                8 * 1024 * 1024, 100_000, 1024, 16, 64 * 1024);
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
