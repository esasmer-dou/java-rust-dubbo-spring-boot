package com.reactor.rust.dubbo.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks an application that uses generated Rust Dubbo clients or providers. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
@Inherited
@Documented
public @interface EnableDubbo {
    String[] scanBasePackages() default {};

    Class<?>[] scanBasePackageClasses() default {};

    boolean multipleConfig() default true;
}
