package com.reactor.rust.dubbo.runtime;

@FunctionalInterface
public interface DubboBeanResolver {
    <T> T require(Class<T> type);
}
