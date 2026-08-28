package com.reactor.rust.dubbo.codegen;

final class StableIds {
    private StableIds() {
    }

    static int fnv1a32(String value) {
        int hash = 0x811c9dc5;
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (byte current : bytes) {
            hash ^= current & 0xff;
            hash *= 0x01000193;
        }
        return hash & 0x7fffffff;
    }
}
