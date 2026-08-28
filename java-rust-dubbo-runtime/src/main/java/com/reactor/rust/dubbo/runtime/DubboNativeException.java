package com.reactor.rust.dubbo.runtime;

public final class DubboNativeException extends RuntimeException {
    public DubboNativeException(String message) {
        super(message);
    }

    public DubboNativeException(String message, Throwable cause) {
        super(message, cause);
    }
}
