package com.citytrip.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceConfigFallbackTest {

    @Test
    void fallsBackToAppDbVariablesWhenDbVariablesAreMissing() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DB_USERNAME", "")
                .withProperty("DB_PASSWORD", "")
                .withProperty("APP_DB_NAME", "test_city_trip_db")
                .withProperty("APP_DB_USERNAME", "test_user")
                .withProperty("APP_DB_PASSWORD", "test_password");

        new DbCompatibilityEnvironmentPostProcessor().postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("DB_URL")).contains("/test_city_trip_db");
        assertThat(environment.getProperty("DB_USERNAME")).isEqualTo("test_user");
        assertThat(environment.getProperty("DB_PASSWORD")).isEqualTo("test_password");
    }

    @Test
    void dbVariablesStillOverrideAppDbVariables() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DB_URL", "jdbc:mysql://127.0.0.1:3306/explicit_db")
                .withProperty("DB_USERNAME", "explicit_user")
                .withProperty("DB_PASSWORD", "explicit_password")
                .withProperty("APP_DB_NAME", "app_db_name")
                .withProperty("APP_DB_USERNAME", "app_user")
                .withProperty("APP_DB_PASSWORD", "app_password");

        new DbCompatibilityEnvironmentPostProcessor().postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("DB_URL")).isEqualTo("jdbc:mysql://127.0.0.1:3306/explicit_db");
        assertThat(environment.getProperty("DB_USERNAME")).isEqualTo("explicit_user");
        assertThat(environment.getProperty("DB_PASSWORD")).isEqualTo("explicit_password");
    }
}
