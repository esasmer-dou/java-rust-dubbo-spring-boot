package com.reactor.rust.dubbo.runtime;

import java.util.HashMap;
import java.util.Map;

/** Startup-only registry. Generated injection code performs direct casts after lookup. */
public final class DubboClientRegistry {
    private final Map<DubboClientKey, Object> clients = new HashMap<>();
    private boolean sealed;

    public void register(DubboClientKey key, Object client) {
        if (sealed) {
            throw new IllegalStateException("Dubbo client registry is sealed");
        }
        if (clients.putIfAbsent(key, client) != null) {
            throw new IllegalStateException("Duplicate Dubbo client " + key);
        }
    }

    public Object client(String interfaceName, String group, String version) {
        Object client = clients.get(new DubboClientKey(interfaceName, group, version));
        if (client == null) {
            throw new IllegalStateException("Generated Dubbo client is not registered for " + interfaceName);
        }
        return client;
    }

    public void seal() {
        sealed = true;
    }

    public int size() {
        return clients.size();
    }
}
