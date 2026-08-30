package org.apache.dubbo.config.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Optional migration surface. New code should use
 * {@code com.reactor.rust.dubbo.annotation.DubboReference}.
 */
@Deprecated(since = "0.3.0", forRemoval = false)
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
public @interface DubboReference {
    Class<?> interfaceClass() default void.class;
    String interfaceName() default "";
    String version() default "";
    String group() default "";
    String url() default "";
    boolean check() default true;
    boolean init() default false;
    boolean lazy() default false;
    boolean async() default false;
    boolean sticky() default false;
    int connections() default -1;
    int timeout() default -1;
    int retries() default -1;
    int actives() default -1;
    String cluster() default "";
    String loadbalance() default "";
    String protocol() default "";
    String client() default "";
    String[] registry() default {};
    String serialization() default "";
    String executor() default "";
}
