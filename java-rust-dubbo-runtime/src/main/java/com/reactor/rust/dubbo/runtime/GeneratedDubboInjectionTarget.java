package com.reactor.rust.dubbo.runtime;

/** Implemented by build-time enhanced Spring beans that contain Dubbo references. */
public interface GeneratedDubboInjectionTarget {
    void __rustDubboInject(DubboClientRegistry registry);
}
