package com.reactor.rust.dubbo.runtime;

import java.util.HashMap;
import java.util.Map;

public final class DubboProviderRegistry implements NativeDubboDispatcher {
    private final Map<Integer, NativeDubboDispatcher> dispatchers = new HashMap<>();

    public void register(int serviceId, NativeDubboDispatcher dispatcher) {
        if (serviceId <= 0) {
            throw new IllegalArgumentException("serviceId must be positive");
        }
        if (dispatchers.putIfAbsent(serviceId, dispatcher) != null) {
            throw new IllegalStateException("Duplicate Dubbo service id " + serviceId);
        }
    }

    public NativeDubboDispatcher dispatcher(int serviceId) {
        NativeDubboDispatcher dispatcher = dispatchers.get(serviceId);
        if (dispatcher == null) {
            throw new IllegalArgumentException("Unknown Dubbo service id " + serviceId);
        }
        return dispatcher;
    }

    public int size() {
        return dispatchers.size();
    }

    @Override
    public int dispatch(int serviceId, int methodId, java.nio.ByteBuffer request,
                        int requestLength, long responseHandle, java.nio.ByteBuffer response) {
        return dispatcher(serviceId).dispatch(serviceId, methodId, request, requestLength,
                responseHandle, response);
    }
}
