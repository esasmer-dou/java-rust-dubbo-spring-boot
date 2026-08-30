package org.apache.dubbo.config.spring.context.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Optional migration surface. New code should use
 * {@code com.reactor.rust.dubbo.annotation.EnableDubbo}.
 */
@Deprecated(since = "0.3.0", forRemoval = false)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
@Inherited
@Documented
public @interface EnableDubbo {
    String[] scanBasePackages() default {};
    Class<?>[] scanBasePackageClasses() default {};
    boolean multipleConfig() default true;
}
