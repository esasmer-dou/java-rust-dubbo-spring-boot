package com.reactor.rust.dubbo.spring;

import com.reactor.rust.dubbo.runtime.DubboClientRegistry;
import com.reactor.rust.dubbo.runtime.DubboBeanResolver;
import com.reactor.rust.dubbo.runtime.DubboProviderRegistry;
import com.reactor.rust.dubbo.runtime.GeneratedDubboModule;
import com.reactor.rust.dubbo.runtime.NativeDubboRuntime;
import java.util.List;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
@EnableConfigurationProperties(RustDubboProperties.class)
@ConditionalOnProperty(prefix = "reactor.dubbo", name = "enabled", matchIfMissing = true)
public class RustDubboAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    NativeDubboRuntime nativeDubboRuntime(RustDubboProperties properties, Environment environment) {
        properties.resolveProfile(environment);
        return new NativeDubboRuntime(properties.clientRoutingOptions(), properties.runtimeOptions(),
                properties.providerOptions());
    }

    @Bean
    @ConditionalOnMissingBean
    DubboClientRegistry dubboClientRegistry() {
        return new DubboClientRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    DubboProviderRegistry dubboProviderRegistry() {
        return new DubboProviderRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    DubboBeanResolver dubboBeanResolver(ListableBeanFactory beans) {
        return new DubboBeanResolver() {
            @Override
            public <T> T require(Class<T> type) {
                return beans.getBean(type);
            }
        };
    }

    @Bean
    GeneratedModuleCoordinator generatedModuleCoordinator(
            List<GeneratedDubboModule> modules,
            DubboClientRegistry clients,
            DubboProviderRegistry providers,
            NativeDubboRuntime runtime,
            DubboBeanResolver beans) {
        return new GeneratedModuleCoordinator(modules, clients, providers, runtime, beans);
    }

    @Bean
    static DubboInjectionBeanPostProcessor dubboInjectionBeanPostProcessor(
            GeneratedModuleCoordinator coordinator, DubboClientRegistry clients) {
        return new DubboInjectionBeanPostProcessor(coordinator, clients);
    }

    @Bean
    RustDubboLifecycle rustDubboLifecycle(NativeDubboRuntime runtime,
                                          GeneratedModuleCoordinator coordinator,
                                          DubboProviderRegistry providers,
                                          RustDubboProperties properties,
                                          Environment environment) {
        properties.resolveProfile(environment);
        return new RustDubboLifecycle(runtime, coordinator, providers, properties);
    }
}
