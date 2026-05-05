package com.citytrip.service.impl;

import com.citytrip.service.impl.vivo.VivoFunctionCallingService;
import com.citytrip.service.impl.vivo.VivoToolRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class VivoFunctionCallingServiceBeanCreationTest {

    @Test
    void springShouldCreateVivoFunctionCallingServiceFromInjectedDependencies() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(VivoToolRegistry.class, VivoToolRegistry::new);
            context.registerBean(VivoFunctionCallingService.class);

            assertThatCode(context::refresh)
                    .doesNotThrowAnyException();

            assertThat(context.getBean(VivoFunctionCallingService.class)).isNotNull();
        }
    }
}
