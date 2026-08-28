package com.reactor.rust.dubbo.runtime;

import java.nio.ByteBuffer;

public final class NativeDubboBridge {
    public static final int EXPECTED_ABI = 3;

    private NativeDubboBridge() {
    }

    public static void ensureLoaded() {
        Holder.ensureCompatible();
    }

    public static void configure(DubboRuntimeOptions options) {
        ensureLoaded();
        nativeConfigure(options.ioWorkers(), options.callbackWorkers(),
                options.callbackQueueCapacity(), options.threadStackBytes());
    }

    public static int createClient(String service, String group, String version, String providers,
                                   int connectionsPerEndpoint, int commandQueueCapacity,
                                   int maxInFlight, int heartbeatIntervalMs,
                                   int maxPayloadBytes) {
        ensureLoaded();
        return nativeCreateClient(service, group, version, providers, connectionsPerEndpoint,
                commandQueueCapacity, maxInFlight, heartbeatIntervalMs, maxPayloadBytes);
    }

    public static void registerMethod(int clientId, int methodId, String method, String descriptor) {
        ensureLoaded();
        nativeRegisterMethod(clientId, methodId, method, descriptor);
    }

    public static NativeResponse invoke(int clientId, int methodId, ByteBuffer request,
                                        int requestLength, int timeoutMs) {
        ensureLoaded();
        if (!request.isDirect()) {
            throw new IllegalArgumentException("request must be a direct ByteBuffer");
        }
        long handle = nativeInvoke(clientId, methodId, request, requestLength, timeoutMs);
        if (handle <= 0) {
            throw new DubboNativeException("Native Dubbo invocation failed without a response handle");
        }
        return takeResponse(handle);
    }

    public static void invokeAsync(int clientId, int methodId, ByteBuffer request,
                                   int requestLength, int timeoutMs,
                                   NativeDubboAsyncCallback callback) {
        ensureLoaded();
        if (!request.isDirect()) {
            throw new IllegalArgumentException("request must be a direct ByteBuffer");
        }
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }
        nativeInvokeAsync(clientId, methodId, request, requestLength, timeoutMs, callback);
    }

    public static void closeClient(int clientId) {
        if (clientId > 0 && Holder.loaded) {
            nativeCloseClient(clientId);
        }
    }

    public static boolean clientReady(int clientId) {
        ensureLoaded();
        return nativeClientReady(clientId);
    }

    static void releaseResponse(long handle) {
        if (handle > 0 && Holder.loaded) {
            nativeReleaseResponse(handle);
        }
    }

    public static long startProvider(int port, int ioWorkers, int businessWorkers,
                                     int queueCapacity, int maxPayloadBytes, int requestTimeoutMs,
                                     NativeDubboDispatcher dispatcher) {
        ensureLoaded();
        return nativeStartProvider(port, ioWorkers, businessWorkers, queueCapacity,
                maxPayloadBytes, requestTimeoutMs, dispatcher);
    }

    public static void registerProviderMethod(int serviceId, int methodId, String service,
                                              String group, String version, String method,
                                              String descriptor, String executor,
                                              int maxConcurrent) {
        ensureLoaded();
        nativeRegisterProviderMethod(serviceId, methodId, service, group, version, method,
                descriptor, executor == null ? "" : executor, maxConcurrent);
    }

    public static ByteBuffer growProviderResponse(long responseHandle, int requiredBytes) {
        ByteBuffer buffer = nativeGrowProviderResponse(responseHandle, requiredBytes);
        if (buffer == null) {
            throw new DubboNativeException("Native provider response buffer growth failed");
        }
        return buffer;
    }

    public static boolean completeProviderResponse(long responseHandle, int responseLength) {
        return nativeCompleteProviderResponse(responseHandle, responseLength);
    }

    public static boolean failProviderResponse(long responseHandle, String message) {
        return nativeFailProviderResponse(responseHandle,
                message == null || message.isBlank() ? "asynchronous provider call failed" : message);
    }

    public static void stopProvider(long providerHandle, int drainTimeoutMs) {
        if (providerHandle > 0 && Holder.loaded) {
            nativeStopProvider(providerHandle, drainTimeoutMs);
        }
    }

    public static String metricsJson() {
        ensureLoaded();
        return nativeMetricsJson();
    }

    static NativeResponse takeResponse(long handle) {
        if (handle <= 0) {
            throw new DubboNativeException("Native Dubbo response handle is invalid");
        }
        int status = nativeResponseStatus(handle);
        ByteBuffer body = nativeResponseBuffer(handle);
        if (body == null) {
            nativeReleaseResponse(handle);
            throw new DubboNativeException("Native Dubbo response body is unavailable");
        }
        return new NativeResponse(handle, status, body);
    }

    private static final class Holder {
        private static volatile boolean loaded;

        private static void ensureCompatible() {
            if (loaded) {
                return;
            }
            synchronized (Holder.class) {
                if (!loaded) {
                    NativeLibraryLoader.load();
                    int actual = nativeAbiVersion();
                    if (actual != EXPECTED_ABI) {
                        throw new DubboNativeException(
                                "Native Dubbo ABI mismatch: Java=" + EXPECTED_ABI + ", native=" + actual);
                    }
                    loaded = true;
                }
            }
        }
    }

    private static native int nativeAbiVersion();
    private static native void nativeConfigure(int ioWorkers, int callbackWorkers,
                                               int callbackQueueCapacity, int threadStackBytes);
    private static native int nativeCreateClient(String service, String group, String version,
                                                  String providers, int connectionsPerEndpoint,
                                                  int commandQueueCapacity,
                                                  int maxInFlight, int heartbeatIntervalMs,
                                                  int maxPayloadBytes);
    private static native void nativeRegisterMethod(int clientId, int methodId,
                                                    String method, String descriptor);
    private static native long nativeInvoke(int clientId, int methodId, ByteBuffer request,
                                            int requestLength, int timeoutMs);
    private static native void nativeInvokeAsync(int clientId, int methodId, ByteBuffer request,
                                                 int requestLength, int timeoutMs,
                                                 NativeDubboAsyncCallback callback);
    private static native int nativeResponseStatus(long responseHandle);
    private static native ByteBuffer nativeResponseBuffer(long responseHandle);
    private static native void nativeReleaseResponse(long responseHandle);
    private static native void nativeCloseClient(int clientId);
    private static native boolean nativeClientReady(int clientId);
    private static native long nativeStartProvider(int port, int ioWorkers, int businessWorkers,
                                                   int queueCapacity, int maxPayloadBytes,
                                                   int requestTimeoutMs,
                                                   NativeDubboDispatcher dispatcher);
    private static native void nativeRegisterProviderMethod(int serviceId, int methodId,
                                                            String service, String group,
                                                            String version, String method,
                                                            String descriptor, String executor,
                                                            int maxConcurrent);
    private static native ByteBuffer nativeGrowProviderResponse(long responseHandle,
                                                                int requiredBytes);
    private static native boolean nativeCompleteProviderResponse(long responseHandle,
                                                                 int responseLength);
    private static native boolean nativeFailProviderResponse(long responseHandle, String message);
    private static native void nativeStopProvider(long providerHandle, int drainTimeoutMs);
    private static native String nativeMetricsJson();
}
