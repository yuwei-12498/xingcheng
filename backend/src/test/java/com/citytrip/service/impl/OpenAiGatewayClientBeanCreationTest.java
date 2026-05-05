package com.citytrip.service.impl;

import com.citytrip.config.LlmProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class OpenAiGatewayClientBeanCreationTest {

    @Test
    void springShouldCreateOpenAiGatewayClientFromInjectedDependencies() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(LlmProperties.class, () -> new LlmProperties());
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.registerBean(OpenAiGatewayClient.class);

            assertThatCode(context::refresh)
                    .doesNotThrowAnyException();

            assertThat(context.getBean(OpenAiGatewayClient.class)).isNotNull();
        }
    }
}
