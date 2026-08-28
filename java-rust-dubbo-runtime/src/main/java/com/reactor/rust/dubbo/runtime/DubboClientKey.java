package com.reactor.rust.dubbo.runtime;

public record DubboClientKey(String interfaceName, String group, String version) {
    public DubboClientKey {
        interfaceName = requireText(interfaceName, "interfaceName");
        group = normalize(group);
        version = normalize(version);
    }

    public static DubboClientKey of(String interfaceName) {
        return new DubboClientKey(interfaceName, "", "");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }
}
