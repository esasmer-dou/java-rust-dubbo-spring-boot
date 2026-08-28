package com.reactor.rust.dubbo.spring;

import com.reactor.rust.dubbo.runtime.DubboClientRegistry;
import com.reactor.rust.dubbo.runtime.GeneratedDubboInjectionTarget;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

final class DubboInjectionBeanPostProcessor implements BeanPostProcessor {
    private final GeneratedModuleCoordinator coordinator;
    private final DubboClientRegistry clients;

    DubboInjectionBeanPostProcessor(GeneratedModuleCoordinator coordinator, DubboClientRegistry clients) {
        this.coordinator = coordinator;
        this.clients = clients;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof GeneratedDubboInjectionTarget target) {
            coordinator.ensureClients();
            target.__rustDubboInject(clients);
        }
        return bean;
    }
}
