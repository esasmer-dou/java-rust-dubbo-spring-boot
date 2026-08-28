package com.reactor.rust.dubbo.spring;

import com.reactor.rust.dubbo.runtime.NativeDubboRuntime;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureAfter(RustDubboAutoConfiguration.class)
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnProperty(prefix = "reactor.dubbo", name = "enabled", matchIfMissing = true)
public class RustDubboActuatorAutoConfiguration {
    @Bean("rustDubboHealthIndicator")
    @ConditionalOnMissingBean(name = "rustDubboHealthIndicator")
    HealthIndicator rustDubboHealthIndicator(NativeDubboRuntime runtime,
                                              RustDubboProperties properties) {
        return () -> {
            try {
                boolean clientsReady = runtime.clientsReady();
                boolean providerReady = !properties.getProvider().isEnabled()
                        || runtime.providerRunning();
                Health.Builder health = clientsReady && providerReady ? Health.up() : Health.down();
                return health
                        .withDetail("profile", properties.getProfile())
                        .withDetail("clientsReady", clientsReady)
                        .withDetail("providerReady", providerReady)
                        .withDetail("nativeMetrics", runtime.metricsJson())
                        .build();
            } catch (RuntimeException error) {
                return Health.down(error).build();
            }
        };
    }
}
