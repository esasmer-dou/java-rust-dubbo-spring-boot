package com.reactor.rust.dubbo.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Startup-only registry. Generated injection code performs direct casts after one-time lookup. */
public final class DubboClientRegistry {
    private final Map<DubboClientKey, ClientEntry> clients = new HashMap<>();
    private boolean sealed;

    public synchronized void register(DubboClientKey key, Object client) {
        registerEntry(key, ClientEntry.materialized(client));
    }

    /** Registers generated construction without opening native connections for inactive Spring beans. */
    public synchronized void registerFactory(DubboClientKey key, Supplier<?> factory) {
        registerEntry(key, ClientEntry.lazy(factory));
    }

    public Object client(String interfaceName, String group, String version) {
        DubboClientKey key = new DubboClientKey(interfaceName, group, version);
        ClientEntry entry;
        synchronized (this) {
            entry = clients.get(key);
        }
        if (entry == null) {
            throw new IllegalStateException("Generated Dubbo client is not registered for " + interfaceName);
        }
        return entry.client(key);
    }

    public synchronized void seal() {
        sealed = true;
    }

    public synchronized int size() {
        return clients.size();
    }

    private void registerEntry(DubboClientKey key, ClientEntry entry) {
        if (sealed) {
            throw new IllegalStateException("Dubbo client registry is sealed");
        }
        if (clients.putIfAbsent(Objects.requireNonNull(key, "key"), entry) != null) {
            throw new IllegalStateException("Duplicate Dubbo client " + key);
        }
    }

    private static final class ClientEntry {
        private Object client;
        private Supplier<?> factory;

        private ClientEntry(Object client, Supplier<?> factory) {
            this.client = client;
            this.factory = factory;
        }

        static ClientEntry materialized(Object client) {
            return new ClientEntry(Objects.requireNonNull(client, "client"), null);
        }

        static ClientEntry lazy(Supplier<?> factory) {
            return new ClientEntry(null, Objects.requireNonNull(factory, "factory"));
        }

        synchronized Object client(DubboClientKey key) {
            if (client == null) {
                Object created = factory.get();
                if (created == null) {
                    throw new IllegalStateException("Generated Dubbo client factory returned null for " + key);
                }
                client = created;
                factory = null;
            }
            return client;
        }
    }
}
