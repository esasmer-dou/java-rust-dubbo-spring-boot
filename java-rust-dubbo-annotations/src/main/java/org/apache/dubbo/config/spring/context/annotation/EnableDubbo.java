package org.apache.dubbo.config.spring.context.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
@Inherited
@Documented
public @interface EnableDubbo {
    String[] scanBasePackages() default {};
    Class<?>[] scanBasePackageClasses() default {};
    boolean multipleConfig() default true;
}
