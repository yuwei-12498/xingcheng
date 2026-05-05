package com.citytrip.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

public class DbCompatibilityEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "citytripDbCompatibility";
    private static final String DEFAULT_DB_NAME = "city_trip_db";
    private static final String DEFAULT_DB_URL_TEMPLATE =
            "jdbc:mysql://127.0.0.1:3306/%s?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&createDatabaseIfNotExist=true";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> aliases = new LinkedHashMap<>();

        if (!hasText(environment, "DB_URL")) {
            String dbName = environment.getProperty("APP_DB_NAME", DEFAULT_DB_NAME);
            aliases.put("DB_URL", DEFAULT_DB_URL_TEMPLATE.formatted(dbName));
        }
        if (!hasText(environment, "DB_USERNAME") && hasText(environment, "APP_DB_USERNAME")) {
            aliases.put("DB_USERNAME", environment.getProperty("APP_DB_USERNAME"));
        }
        if (!hasText(environment, "DB_PASSWORD")) {
            String password = firstNonBlank(
                    environment.getProperty("MYSQL_PWD"),
                    environment.getProperty("APP_DB_PASSWORD")
            );
            if (StringUtils.hasText(password)) {
                aliases.put("DB_PASSWORD", password);
            }
        }

        if (!aliases.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, aliases));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private boolean hasText(ConfigurableEnvironment environment, String key) {
        return StringUtils.hasText(environment.getProperty(key));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
