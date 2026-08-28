package com.reactor.rust.dubbo.runtime;

public record DubboRuntimeOptions(
        int ioWorkers,
        int callbackWorkers,
        int callbackQueueCapacity,
        int threadStackBytes) {

    public DubboRuntimeOptions {
        requireRange(ioWorkers, 1, 64, "ioWorkers");
        requireRange(callbackWorkers, 1, 64, "callbackWorkers");
        requireRange(callbackQueueCapacity, 1, 1_000_000, "callbackQueueCapacity");
        requireRange(threadStackBytes, 128 * 1024, 8 * 1024 * 1024, "threadStackBytes");
    }

    public static DubboRuntimeOptions microDefaults() {
        return new DubboRuntimeOptions(1, 1, 256, 256 * 1024);
    }

    private static void requireRange(int value, int min, int max, String name) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max);
        }
    }
}
