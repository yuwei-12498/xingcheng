package com.citytrip.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Loads the repository-level .env before Spring config data is resolved.
 * <p>
 * This makes local IDE launches stable whether the working directory is the
 * repository root or the backend module directory, while still allowing real
 * process environment variables to take precedence.
 */
public class DotEnvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "citytripDotEnv";
    private static final int MAX_PARENT_DEPTH = 4;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Optional<Path> dotEnv = findDotEnv(Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize());
        if (dotEnv.isEmpty()) {
            return;
        }

        Map<String, Object> values = readDotEnv(dotEnv.get(), environment);
        if (values.isEmpty()) {
            return;
        }

        MapPropertySource propertySource = new MapPropertySource(PROPERTY_SOURCE_NAME, values);
        if (environment.getPropertySources().contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
            environment.getPropertySources().addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, propertySource);
        } else if (environment.getPropertySources().contains(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME)) {
            environment.getPropertySources().addAfter(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, propertySource);
        } else {
            environment.getPropertySources().addFirst(propertySource);
        }
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER - 1;
    }

    private Optional<Path> findDotEnv(Path start) {
        Path cursor = Files.isDirectory(start) ? start : start.getParent();
        for (int depth = 0; cursor != null && depth <= MAX_PARENT_DEPTH; depth++) {
            Path candidate = cursor.resolve(".env");
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
            cursor = cursor.getParent();
        }
        return Optional.empty();
    }

    private Map<String, Object> readDotEnv(Path dotEnv, ConfigurableEnvironment environment) {
        Map<String, Object> values = new LinkedHashMap<>();
        try {
            for (String rawLine : Files.readAllLines(dotEnv, StandardCharsets.UTF_8)) {
                DotEnvEntry entry = parseLine(rawLine);
                if (entry == null || StringUtils.hasText(environment.getProperty(entry.name()))) {
                    continue;
                }
                values.put(entry.name(), entry.value());
            }
        } catch (IOException ignored) {
            return Map.of();
        }
        return values;
    }

    private DotEnvEntry parseLine(String rawLine) {
        if (rawLine == null) {
            return null;
        }
        String line = rawLine.strip();
        if (line.isEmpty() || line.startsWith("#")) {
            return null;
        }
        if (line.startsWith("export ")) {
            line = line.substring("export ".length()).strip();
        }

        int separator = line.indexOf('=');
        if (separator <= 0) {
            return null;
        }

        String name = line.substring(0, separator).strip();
        if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return null;
        }

        String value = line.substring(separator + 1).strip();
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1);
        }
        return new DotEnvEntry(name, value);
    }

    private record DotEnvEntry(String name, String value) {
    }
}
