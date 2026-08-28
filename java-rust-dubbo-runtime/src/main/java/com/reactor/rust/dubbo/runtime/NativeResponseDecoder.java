package com.reactor.rust.dubbo.runtime;

@FunctionalInterface
public interface NativeResponseDecoder<T> {
    T decode(Hessian2Input input);
}
