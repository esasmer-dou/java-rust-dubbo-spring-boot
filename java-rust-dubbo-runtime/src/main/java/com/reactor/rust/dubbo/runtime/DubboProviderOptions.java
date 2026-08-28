package com.reactor.rust.dubbo.runtime;

import java.util.Map;

public record DubboProviderOptions(
        int defaultMaxConcurrent,
        Map<String, Integer> executorMaxConcurrent) {

    public DubboProviderOptions {
        requirePositive(defaultMaxConcurrent, "defaultMaxConcurrent");
        executorMaxConcurrent = executorMaxConcurrent == null
                ? Map.of()
                : Map.copyOf(executorMaxConcurrent);
        for (Map.Entry<String, Integer> entry : executorMaxConcurrent.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new IllegalArgumentException("executor lane name must not be blank");
            }
            requirePositive(entry.getValue(), "executorMaxConcurrent[" + entry.getKey() + "]");
        }
    }

    public int maxConcurrent(String executor) {
        if (executor == null || executor.isBlank()) {
            return defaultMaxConcurrent;
        }
        return executorMaxConcurrent.getOrDefault(executor, defaultMaxConcurrent);
    }

    public static DubboProviderOptions microDefaults() {
        return new DubboProviderOptions(16, Map.of());
    }

    private static void requirePositive(Integer value, String name) {
        if (value == null || value <= 0 || value > 1_000_000) {
            throw new IllegalArgumentException(name + " must be between 1 and 1000000");
        }
    }
}
