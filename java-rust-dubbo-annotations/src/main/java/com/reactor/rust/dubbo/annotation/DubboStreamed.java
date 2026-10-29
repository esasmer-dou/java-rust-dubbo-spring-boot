package com.reactor.rust.dubbo.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opts a synchronous {@code List<T>} result into native-backed incremental decoding.
 *
 * <p>The generated client still returns the declared {@code List<T>} type. Regular list
 * operations retain their normal behavior and materialize values when needed. A response writer
 * can instead consume the result through the runtime {@code DubboStreamingResult} contract so
 * only one decoded item is reachable at a time.</p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface DubboStreamed {
    /**
     * Adds optional one-pass direct JSON consumption, selected through DubboSpringJsonStreams.
     * The declared List type and ordinary DTO/list operations remain available until direct
     * consumption starts. Choosing either mode permanently excludes the other for that response.
     */
    boolean directJson() default false;
}
