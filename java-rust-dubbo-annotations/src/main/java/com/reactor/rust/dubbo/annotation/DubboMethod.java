package com.reactor.rust.dubbo.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Positive timeout in milliseconds. On an interface method this bounds the consumer wait;
 * on an implementation method it bounds the provider response, including queue time.
 * Neither timeout interrupts business code already running at the provider.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface DubboMethod {
    int timeoutMs();
}
