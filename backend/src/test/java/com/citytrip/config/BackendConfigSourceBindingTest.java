package com.citytrip.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

class BackendConfigSourceBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestApp.class);

    @Test
    void backendShouldBindPortAndLlmSceneModelsFromExplicitConfiguration() {
        contextRunner
                .withPropertyValues(
                        "server.port=18082",
                        "llm.openai.api-key=test-api-key",
                        "llm.openai.model=gpt-test-default",
                        "llm.openai.chat.model=Doubao-Seed-2.0-mini",
                        "llm.openai.text.model=Doubao-Seed-2.0-lite"
                )
                .run(context -> {
                    Environment environment = context.getEnvironment();
                    LlmProperties llmProperties = context.getBean(LlmProperties.class);

                    assertThat(environment.getProperty("server.port")).isEqualTo("18082");
                    assertThat(llmProperties.getOpenai().resolveChatOptions().getModel())
                            .isEqualTo("Doubao-Seed-2.0-mini");
                    assertThat(llmProperties.getOpenai().resolveTextOptions().getModel())
                            .isEqualTo("Doubao-Seed-2.0-lite");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(LlmProperties.class)
    static class TestApp {
    }
}
