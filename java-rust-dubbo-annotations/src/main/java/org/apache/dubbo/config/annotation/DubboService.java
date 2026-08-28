package org.apache.dubbo.config.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD})
@Inherited
public @interface DubboService {
    Class<?> interfaceClass() default void.class;
    String interfaceName() default "";
    String version() default "";
    String group() default "";
    String path() default "";
    boolean export() default true;
    boolean async() default false;
    int executes() default -1;
    int connections() default -1;
    int timeout() default -1;
    int retries() default -1;
    int actives() default -1;
    String cluster() default "";
    String loadbalance() default "";
    String executor() default "";
    String payload() default "";
    String serialization() default "";
    String[] protocol() default {};
    String[] registry() default {};
}
