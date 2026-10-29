package com.reactor.rust.dubbo.runtime;

/** Additional ownership capability, deliberately separate from the ABI-3 function signatures. */
final class NativeResponsePolicy {
    private NativeResponsePolicy() {}

    static void requireCurrent() {
        if (!Current.SUPPORTED) {
            throw new IllegalStateException("Direct JSON requires the current bounded-response native library "
                    + "(policy 1, at most 256 handles and 32 MiB); no RPC was dispatched");
        }
    }

    static boolean supports(String metrics) {
        if (metrics == null || integer(metrics, "nativeResponsePolicyVersion") != 1) return false;
        int handles = integer(metrics, "nativeResponseHandleLimit");
        int bytes = integer(metrics, "nativeResponseByteLimit");
        return handles > 0 && handles <= 256 && bytes > 0 && bytes <= 32 * 1024 * 1024;
    }

    private static int integer(String json, String key) {
        int at = json.indexOf('"' + key + '"');
        if (at < 0) return -1;
        at += key.length() + 2;
        while (at < json.length() && space(json.charAt(at))) at++;
        if (at == json.length() || json.charAt(at++) != ':') return -1;
        while (at < json.length() && space(json.charAt(at))) at++;
        int digits = 0;
        long value = 0;
        while (at < json.length() && json.charAt(at) >= '0' && json.charAt(at) <= '9') {
            value = value * 10 + json.charAt(at++) - '0';
            if (value > Integer.MAX_VALUE) return -1;
            digits++;
        }
        while (at < json.length() && space(json.charAt(at))) at++;
        return digits == 0 || at == json.length() || json.charAt(at) != ',' && json.charAt(at) != '}' ? -1 : (int) value;
    }

    private static boolean space(char value) { return value == ' ' || value == '\n' || value == '\r' || value == '\t'; }

    // Loaded only for this explicit native feature, never by ordinary DTO/legacy calls.
    private static final class Current {
        private static final boolean SUPPORTED = supports(NativeDubboBridge.metricsJson());
    }
}
