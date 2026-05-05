package com.citytrip.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseStartupDiagnosticsTest {

    @Test
    void throwsActionableMessageWhenConnectionCannotBeOpened() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("Access denied"));

        DataSourceProperties properties = new DataSourceProperties();
        properties.setUrl("jdbc:mysql://127.0.0.1:3306/city_trip_db");
        properties.setUsername("root");

        DatabaseStartupDiagnostics diagnostics = new DatabaseStartupDiagnostics(dataSource, properties);

        assertThatThrownBy(diagnostics::verifyConnection)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_URL/DB_USERNAME/DB_PASSWORD")
                .hasMessageContaining("APP_DB_NAME/APP_DB_USERNAME/APP_DB_PASSWORD");
    }

    @Test
    void succeedsWhenConnectionIsValid() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        Statement statement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(3)).thenReturn(true);
        when(connection.getMetaData()).thenReturn(metaData);
        when(connection.createStatement()).thenReturn(statement);
        when(metaData.getTables(any(), any(), anyString(), any()))
                .thenAnswer(invocation -> tableLookup(true));
        when(metaData.getColumns(any(), any(), anyString(), anyString()))
                .thenAnswer(invocation -> columnLookup(true));

        DatabaseStartupDiagnostics diagnostics = new DatabaseStartupDiagnostics(dataSource, new DataSourceProperties());

        assertThatCode(diagnostics::verifyConnection).doesNotThrowAnyException();
        verify(connection).close();
    }

    @Test
    void autoUpgradesSavedItinerarySchemaWhenCompatibilityColumnsAreMissing() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        Statement statement = mock(Statement.class);
        AtomicBoolean upgraded = new AtomicBoolean(false);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(3)).thenReturn(true);
        when(connection.getMetaData()).thenReturn(metaData);
        when(connection.createStatement()).thenReturn(statement);
        when(metaData.getTables(any(), any(), anyString(), any()))
                .thenAnswer(invocation -> tableLookup(true));
        when(metaData.getColumns(any(), any(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String tableName = invocation.getArgument(2, String.class);
                    String columnName = invocation.getArgument(3, String.class);
                    if (!"saved_itinerary".equals(tableName)) {
                        return columnLookup(true);
                    }
                    boolean exists = upgraded.get() || !"is_deleted".equals(columnName);
                    return columnLookup(exists);
                });
        when(statement.execute(anyString()))
                .thenAnswer(invocation -> {
                    upgraded.set(true);
                    return true;
                });

        DatabaseStartupDiagnostics diagnostics = new DatabaseStartupDiagnostics(dataSource, defaultProperties());

        assertThatCode(diagnostics::verifyConnection).doesNotThrowAnyException();
        verify(statement, atLeastOnce()).execute(contains("ADD COLUMN `is_deleted`"));
        verify(connection).close();
    }

    @Test
    void autoUpgradesRoutePlanFactSchemaWhenFeatureColumnsAreMissing() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        Statement statement = mock(Statement.class);
        AtomicBoolean upgraded = new AtomicBoolean(false);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(3)).thenReturn(true);
        when(connection.getMetaData()).thenReturn(metaData);
        when(connection.createStatement()).thenReturn(statement);
        when(metaData.getTables(any(), any(), anyString(), any()))
                .thenAnswer(invocation -> tableLookup(true));
        when(metaData.getColumns(any(), any(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String tableName = invocation.getArgument(2, String.class);
                    String columnName = invocation.getArgument(3, String.class);
                    if (!"route_plan_fact".equals(tableName)) {
                        return columnLookup(true);
                    }
                    boolean exists = upgraded.get() || !"options_feature_json".equals(columnName);
                    return columnLookup(exists);
                });
        when(statement.execute(anyString()))
                .thenAnswer(invocation -> {
                    upgraded.set(true);
                    return true;
                });

        DatabaseStartupDiagnostics diagnostics = new DatabaseStartupDiagnostics(dataSource, defaultProperties());

        assertThatCode(diagnostics::verifyConnection).doesNotThrowAnyException();
        verify(statement, atLeastOnce()).execute(contains("ADD COLUMN `options_feature_json`"));
        verify(connection).close();
    }

    @Test
    void throwsActionableMessageWhenCompatibilityUpgradeCannotBeApplied() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        Statement statement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(3)).thenReturn(true);
        when(connection.getMetaData()).thenReturn(metaData);
        when(connection.createStatement()).thenReturn(statement);
        when(metaData.getTables(any(), any(), anyString(), any()))
                .thenAnswer(invocation -> tableLookup(true));
        when(metaData.getColumns(any(), any(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String tableName = invocation.getArgument(2, String.class);
                    String columnName = invocation.getArgument(3, String.class);
                    if (!"saved_itinerary".equals(tableName)) {
                        return columnLookup(true);
                    }
                    return columnLookup(!"is_deleted".equals(columnName));
                });
        when(statement.execute(anyString())).thenThrow(new SQLException("ALTER denied"));

        DatabaseStartupDiagnostics diagnostics = new DatabaseStartupDiagnostics(dataSource, defaultProperties());

        assertThatThrownBy(diagnostics::verifyConnection)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Database schema is outdated. Missing columns")
                .hasMessageContaining("apply_upgrades.bat");
    }

    @Test
    void autoBootstrapsAnalyticsFactTablesWhenTheyAreMissing() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        Statement statement = mock(Statement.class);
        AtomicInteger bootstrapExecutions = new AtomicInteger(0);
        Map<String, Boolean> stableTables = Map.of(
                "user_custom_poi", true,
                "saved_itinerary_edit_version", true
        );

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(3)).thenReturn(true);
        when(connection.getMetaData()).thenReturn(metaData);
        when(connection.createStatement()).thenReturn(statement);
        when(metaData.getTables(any(), any(), anyString(), any()))
                .thenAnswer(invocation -> {
                    String tableName = invocation.getArgument(2, String.class);
                    boolean exists = stableTables.getOrDefault(tableName, bootstrapExecutions.get() > 0);
                    return tableLookup(exists);
                });
        when(metaData.getColumns(any(), any(), anyString(), anyString()))
                .thenAnswer(invocation -> columnLookup(true));
        when(statement.execute(anyString()))
                .thenAnswer(invocation -> {
                    bootstrapExecutions.incrementAndGet();
                    return true;
                });

        DatabaseStartupDiagnostics diagnostics = new DatabaseStartupDiagnostics(dataSource, defaultProperties());

        assertThatCode(diagnostics::verifyConnection).doesNotThrowAnyException();
        verify(statement, atLeastOnce()).execute(contains("CREATE TABLE IF NOT EXISTS `user_behavior_event`"));
        verify(statement, atLeastOnce()).execute(contains("CREATE TABLE IF NOT EXISTS `route_plan_fact`"));
        verify(statement, atLeastOnce()).execute(contains("CREATE TABLE IF NOT EXISTS `route_node_fact`"));
    }

    private ResultSet columnLookup(boolean exists) throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(exists, false);
        return resultSet;
    }

    private ResultSet tableLookup(boolean exists) throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(exists, false);
        return resultSet;
    }

    private DataSourceProperties defaultProperties() {
        DataSourceProperties properties = new DataSourceProperties();
        properties.setUrl("jdbc:mysql://127.0.0.1:3306/city_trip_db");
        properties.setUsername("root");
        return properties;
    }
}
