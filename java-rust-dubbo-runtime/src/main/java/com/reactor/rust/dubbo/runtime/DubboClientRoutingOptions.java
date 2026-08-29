package com.reactor.rust.dubbo.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable startup-only routing table for generated Dubbo clients. */
public final class DubboClientRoutingOptions {
    private final DubboClientOptions defaultOptions;
    private final Map<DubboClientKey, DubboClientOptions> routes;
    private final boolean requireExplicitRoutes;

    public DubboClientRoutingOptions(DubboClientOptions defaultOptions,
                                     Map<DubboClientKey, DubboClientOptions> routes,
                                     boolean requireExplicitRoutes) {
        if (defaultOptions == null) {
            throw new IllegalArgumentException("defaultOptions must not be null");
        }
        this.defaultOptions = defaultOptions;
        this.routes = Map.copyOf(new LinkedHashMap<>(routes));
        this.requireExplicitRoutes = requireExplicitRoutes;
    }

    public static DubboClientRoutingOptions defaults(DubboClientOptions options) {
        return new DubboClientRoutingOptions(options, Map.of(), false);
    }

    public DubboClientOptions defaultOptions() {
        return defaultOptions;
    }

    public DubboClientOptions resolve(String interfaceName, String group, String version) {
        DubboClientKey key = new DubboClientKey(interfaceName, group, version);
        DubboClientOptions options = routes.get(key);
        if (options != null) {
            return options;
        }
        if (requireExplicitRoutes) {
            throw new IllegalStateException("Explicit Dubbo consumer route is required for " + key);
        }
        return defaultOptions;
    }

    public int routeCount() {
        return routes.size();
    }
}
