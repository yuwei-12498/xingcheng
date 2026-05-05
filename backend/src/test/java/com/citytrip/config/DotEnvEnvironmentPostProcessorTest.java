package com.citytrip.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DotEnvEnvironmentPostProcessorTest {

    @Test
    void loadsRootDotEnvWhenStartedFromBackendDirectory() throws Exception {
        Path root = Files.createTempDirectory("citytrip-dotenv-root");
        Path backend = Files.createDirectories(root.resolve("backend"));
        Files.writeString(root.resolve(".env"), """
                OPENAI_API_KEY=sk-from-dotenv
                OPENAI_BASE_URL=https://gateway.example/v1
                APP_DB_NAME=city_trip_test
                """);

        String previousUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", backend.toString());
            ConfigurableEnvironment environment = new StandardEnvironment();

            new DotEnvEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());

            assertThat(environment.getProperty("OPENAI_API_KEY")).isEqualTo("sk-from-dotenv");
            assertThat(environment.getProperty("OPENAI_BASE_URL")).isEqualTo("https://gateway.example/v1");
            assertThat(environment.getProperty("APP_DB_NAME")).isEqualTo("city_trip_test");
        } finally {
            System.setProperty("user.dir", previousUserDir);
        }
    }

    @Test
    void keepsExplicitEnvironmentValuesAheadOfDotEnvFile() throws Exception {
        Path root = Files.createTempDirectory("citytrip-dotenv-override");
        Files.writeString(root.resolve(".env"), "OPENAI_API_KEY=sk-from-dotenv\n");

        String previousUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", root.toString());
            ConfigurableEnvironment environment = new StandardEnvironment();
            environment.getPropertySources().addFirst(new MapPropertySource(
                    "testOverrides",
                    Map.of("OPENAI_API_KEY", "sk-explicit")
            ));

            new DotEnvEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());

            assertThat(environment.getProperty("OPENAI_API_KEY")).isEqualTo("sk-explicit");
        } finally {
            System.setProperty("user.dir", previousUserDir);
        }
    }
}
