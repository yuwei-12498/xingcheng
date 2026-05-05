package com.citytrip.db;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseMigrationCoverageTest {

    @Test
    void routeFeatureColumnsHaveIncrementalFlywayMigrationForExistingDatabases() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V2__route_recommendation_feature_columns.sql");

        assertThat(migration).exists();
        String ddl = Files.readString(migration);
        assertThat(ddl).contains("selected_route_feature_json");
        assertThat(ddl).contains("options_feature_json");
        assertThat(ddl).contains("route_plan_fact");
    }
}
