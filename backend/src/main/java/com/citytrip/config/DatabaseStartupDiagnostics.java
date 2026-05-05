package com.citytrip.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.db.verify-on-startup", havingValue = "true")
public class DatabaseStartupDiagnostics {

    private static final Logger log = LoggerFactory.getLogger(DatabaseStartupDiagnostics.class);
    private static final Map<String, String> REQUIRED_TABLE_DDL = requiredTables();
    private static final Map<String, Map<String, String>> COMPATIBILITY_COLUMNS = compatibilityColumns();

    private final DataSource dataSource;
    private final DataSourceProperties properties;

    public DatabaseStartupDiagnostics(DataSource dataSource, DataSourceProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verifyConnection() {
        String url = maskUrl(properties.getUrl());
        String username = firstNonBlank(properties.getUsername(), "(empty)");

        log.info("Database startup config: url={}, username={}", url, username);

        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(3)) {
                throw new SQLException("Connection validation returned false");
            }
            ensureCompatibility(connection);
            log.info("Database connectivity and schema compatibility check passed.");
        } catch (SQLException ex) {
            throw new IllegalStateException(
                    "Database connection failed at startup. Resolved url=" + url
                            + ", username=" + username
                            + ". Configure DB_URL/DB_USERNAME/DB_PASSWORD, or provide APP_DB_NAME/APP_DB_USERNAME/APP_DB_PASSWORD. "
                            + "For local SQL scripts, MYSQL_PWD can also be reused as the DB password.",
                    ex
            );
        }
    }

    void ensureCompatibility(Connection connection) throws SQLException {
        ensureRequiredTables(connection);
        Map<String, List<String>> missingColumns = detectMissingColumns(connection);
        if (missingColumns.isEmpty()) {
            return;
        }

        String missingSummary = formatMissingColumns(missingColumns);
        log.warn("Database schema compatibility gap detected. Missing columns: {}. Applying startup compatibility upgrade.", missingSummary);

        try (Statement statement = connection.createStatement()) {
            for (Map.Entry<String, List<String>> tableEntry : missingColumns.entrySet()) {
                String tableName = tableEntry.getKey();
                Map<String, String> columnDefinitions = COMPATIBILITY_COLUMNS.getOrDefault(tableName, Collections.emptyMap());
                for (String columnName : tableEntry.getValue()) {
                    String definition = columnDefinitions.get(columnName);
                    if (!StringUtils.hasText(definition)) {
                        throw compatibilityFailure(missingSummary, null);
                    }
                    String ddl = "ALTER TABLE `" + tableName + "` ADD COLUMN `" + columnName + "` " + definition;
                    log.info("Applying compatibility DDL: {}", ddl);
                    statement.execute(ddl);
                }
            }
        } catch (SQLException ex) {
            throw compatibilityFailure(missingSummary, ex);
        }

        Map<String, List<String>> remainingColumns = detectMissingColumns(connection);
        if (!remainingColumns.isEmpty()) {
            throw compatibilityFailure(formatMissingColumns(remainingColumns), null);
        }

        log.info("Startup compatibility upgrade applied successfully for: {}", missingSummary);
    }

    private void ensureRequiredTables(Connection connection) throws SQLException {
        List<String> missingTables = detectMissingTables(connection);
        if (missingTables.isEmpty()) {
            return;
        }

        log.warn("Database schema compatibility gap detected. Missing tables: {}. Applying startup table bootstrap.", String.join(", ", missingTables));
        try (Statement statement = connection.createStatement()) {
            for (String tableName : missingTables) {
                String ddl = REQUIRED_TABLE_DDL.get(tableName);
                if (!StringUtils.hasText(ddl)) {
                    throw tableBootstrapFailure("Missing bootstrap DDL for table: " + tableName, null);
                }
                log.info("Applying bootstrap DDL for table {}.", tableName);
                statement.execute(ddl);
            }
        } catch (SQLException ex) {
            throw tableBootstrapFailure(String.join(", ", missingTables), ex);
        }

        List<String> remainingTables = detectMissingTables(connection);
        if (!remainingTables.isEmpty()) {
            throw tableBootstrapFailure(String.join(", ", remainingTables), null);
        }

        log.info("Startup table bootstrap applied successfully for: {}", String.join(", ", missingTables));
    }

    private Map<String, List<String>> detectMissingColumns(Connection connection) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        String catalog = connection.getCatalog();
        Map<String, List<String>> missing = new LinkedHashMap<>();

        for (Map.Entry<String, Map<String, String>> tableEntry : COMPATIBILITY_COLUMNS.entrySet()) {
            String tableName = tableEntry.getKey();
            List<String> missingForTable = new ArrayList<>();
            for (String columnName : tableEntry.getValue().keySet()) {
                if (!columnExists(metaData, catalog, tableName, columnName)) {
                    missingForTable.add(columnName);
                }
            }
            if (!missingForTable.isEmpty()) {
                missing.put(tableName, missingForTable);
            }
        }

        return missing;
    }

    private List<String> detectMissingTables(Connection connection) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        String catalog = connection.getCatalog();
        List<String> missing = new ArrayList<>();
        for (String tableName : REQUIRED_TABLE_DDL.keySet()) {
            if (!tableExists(metaData, catalog, tableName)) {
                missing.add(tableName);
            }
        }
        return missing;
    }

    private boolean tableExists(DatabaseMetaData metaData, String catalog, String tableName) throws SQLException {
        try (ResultSet resultSet = metaData.getTables(catalog, null, tableName, new String[]{"TABLE"})) {
            return resultSet != null && resultSet.next();
        }
    }

    private boolean columnExists(DatabaseMetaData metaData, String catalog, String tableName, String columnName) throws SQLException {
        try (ResultSet resultSet = metaData.getColumns(catalog, null, tableName, columnName)) {
            return resultSet != null && resultSet.next();
        }
    }

    private IllegalStateException compatibilityFailure(String missingSummary, SQLException cause) {
        return new IllegalStateException(
                "Database schema is outdated. Missing columns: " + missingSummary
                        + ". Startup auto-upgrade could not complete. Run F:/dachuang/backend/sql/apply_upgrades.bat "
                        + "(or source F:/dachuang/backend/sql/full_upgrade_safe.sql) and restart the backend.",
                cause
        );
    }

    private IllegalStateException tableBootstrapFailure(String missingSummary, SQLException cause) {
        return new IllegalStateException(
                "Database schema is outdated. Missing required tables: " + missingSummary
                        + ". Startup auto-bootstrap could not complete. Run F:/dachuang/backend/sql/apply_upgrades.bat "
                        + "(or source F:/dachuang/backend/sql/full_upgrade_safe.sql) and restart the backend.",
                cause
        );
    }

    private String formatMissingColumns(Map<String, List<String>> missingColumns) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : missingColumns.entrySet()) {
            String tableName = entry.getKey();
            for (String columnName : entry.getValue()) {
                parts.add(tableName + "." + columnName);
            }
        }
        return String.join(", ", parts);
    }

    private static Map<String, Map<String, String>> compatibilityColumns() {
        Map<String, String> savedItinerary = new LinkedHashMap<>();
        savedItinerary.put("is_deleted", "TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'community soft delete flag'");
        savedItinerary.put("deleted_at", "DATETIME NULL COMMENT 'community deletion time'");
        savedItinerary.put("deleted_by", "BIGINT NULL COMMENT 'community deletion operator'");
        savedItinerary.put("is_global_pinned", "TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'community global pinned flag'");
        savedItinerary.put("global_pinned_at", "DATETIME NULL COMMENT 'community global pinned time'");
        savedItinerary.put("global_pinned_by", "BIGINT NULL COMMENT 'community global pinned operator'");
        savedItinerary.put("pinned_comment_id", "BIGINT NULL COMMENT 'community author pinned comment id'");

        Map<String, String> poi = new LinkedHashMap<>();
        poi.put("city_code", "VARCHAR(16) NULL COMMENT 'planning city code'");
        poi.put("city_name", "VARCHAR(64) NULL COMMENT 'planning city name'");

        Map<String, String> routePlanFact = new LinkedHashMap<>();
        routePlanFact.put("selected_route_feature_json", "LONGTEXT NULL COMMENT 'selected route LTR feature vector JSON'");
        routePlanFact.put("options_feature_json", "LONGTEXT NULL COMMENT 'candidate options LTR feature vector JSON'");

        Map<String, Map<String, String>> tables = new LinkedHashMap<>();
        tables.put("saved_itinerary", Collections.unmodifiableMap(savedItinerary));
        tables.put("poi", Collections.unmodifiableMap(poi));
        tables.put("route_plan_fact", Collections.unmodifiableMap(routePlanFact));
        return Collections.unmodifiableMap(tables);
    }

    private static Map<String, String> requiredTables() {
        Map<String, String> tables = new LinkedHashMap<>();
        tables.put("user_custom_poi", """
                CREATE TABLE IF NOT EXISTS `user_custom_poi` (
                  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
                  `user_id` BIGINT NOT NULL,
                  `city_name` VARCHAR(64) NULL,
                  `name` VARCHAR(120) NOT NULL,
                  `rough_location` VARCHAR(255) NOT NULL,
                  `category` VARCHAR(64) NULL,
                  `reason` VARCHAR(255) NULL,
                  `address` VARCHAR(255) NULL,
                  `district` VARCHAR(64) NULL,
                  `latitude` DECIMAL(10, 6) NULL,
                  `longitude` DECIMAL(10, 6) NULL,
                  `suggested_stay_duration` INT NULL,
                  `geo_source` VARCHAR(64) NULL,
                  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  KEY `idx_user_custom_poi_user` (`user_id`),
                  KEY `idx_user_custom_poi_city` (`city_name`)
                ) ENGINE=InnoDB COMMENT='User private reusable custom POIs'
                """);
        tables.put("saved_itinerary_edit_version", """
                CREATE TABLE IF NOT EXISTS `saved_itinerary_edit_version` (
                  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
                  `itinerary_id` BIGINT NOT NULL,
                  `user_id` BIGINT NOT NULL,
                  `version_no` INT NOT NULL,
                  `active_flag` TINYINT(1) NOT NULL DEFAULT 0,
                  `source` VARCHAR(32) NOT NULL,
                  `summary` VARCHAR(255) NULL,
                  `request_json` LONGTEXT NOT NULL,
                  `itinerary_json` LONGTEXT NOT NULL,
                  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  UNIQUE KEY `uk_itinerary_version_no` (`itinerary_id`, `version_no`),
                  KEY `idx_saved_itinerary_edit_version_user` (`user_id`),
                  KEY `idx_saved_itinerary_edit_version_active` (`itinerary_id`, `active_flag`)
                ) ENGINE=InnoDB COMMENT='Saved itinerary edit version history'
                """);
        tables.put("user_behavior_event", """
                CREATE TABLE IF NOT EXISTS `user_behavior_event` (
                  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                  `user_id` BIGINT NULL COMMENT '用户ID，匿名行为可为空',
                  `session_id` VARCHAR(64) NULL COMMENT '会话ID，用于关联匿名会话',
                  `request_id` VARCHAR(64) NULL COMMENT '请求追踪ID',
                  `event_type` VARCHAR(64) NOT NULL COMMENT '行为类型，如 plan_submit、option_select、replan_click',
                  `event_source` VARCHAR(32) NOT NULL DEFAULT 'backend' COMMENT '事件来源，如 backend、frontend、miniprogram',
                  `itinerary_id` BIGINT NULL COMMENT '关联行程ID',
                  `poi_id` BIGINT NULL COMMENT '关联 POI ID',
                  `option_key` VARCHAR(64) NULL COMMENT '关联方案 Key',
                  `interaction_weight` DECIMAL(8,2) NOT NULL DEFAULT 1.00 COMMENT '行为权重，用于隐式反馈建模',
                  `success_flag` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否成功采集/执行',
                  `cost_ms` INT NULL COMMENT '请求或行为耗时，单位毫秒',
                  `request_uri` VARCHAR(255) NULL COMMENT '请求 URI',
                  `http_method` VARCHAR(16) NULL COMMENT 'HTTP 方法',
                  `client_ip` VARCHAR(64) NULL COMMENT '客户端 IP',
                  `user_agent` VARCHAR(255) NULL COMMENT '客户端 User-Agent',
                  `referer` VARCHAR(255) NULL COMMENT '来源页面',
                  `extra_json` LONGTEXT NULL COMMENT '扩展上下文 JSON，如请求参数、失败原因、来源页等',
                  `event_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '行为发生时间',
                  PRIMARY KEY (`id`),
                  KEY `idx_user_behavior_user_time` (`user_id`, `event_time`),
                  KEY `idx_user_behavior_event_type_time` (`event_type`, `event_time`),
                  KEY `idx_user_behavior_itinerary_time` (`itinerary_id`, `event_time`),
                  KEY `idx_user_behavior_poi_time` (`poi_id`, `event_time`),
                  KEY `idx_user_behavior_session_time` (`session_id`, `event_time`),
                  KEY `idx_user_behavior_request_id` (`request_id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户行为事件事实表'
                """);
        tables.put("route_plan_fact", """
                CREATE TABLE IF NOT EXISTS `route_plan_fact` (
                  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                  `user_id` BIGINT NULL COMMENT '用户ID，匿名规划可为空',
                  `itinerary_id` BIGINT NULL COMMENT '关联行程ID',
                  `plan_source` VARCHAR(32) NOT NULL COMMENT '规划来源，如 generate、replan、replace',
                  `algorithm_version` VARCHAR(64) NOT NULL COMMENT '算法版本号',
                  `recall_strategy` VARCHAR(64) NULL COMMENT '候选召回/筛选策略标识',
                  `raw_candidate_count` INT NOT NULL DEFAULT 0 COMMENT '原始候选 POI 数量',
                  `filtered_candidate_count` INT NOT NULL DEFAULT 0 COMMENT '过滤后的候选 POI 数量',
                  `final_candidate_count` INT NOT NULL DEFAULT 0 COMMENT '进入最终求解阶段的候选数量',
                  `max_stops` INT NOT NULL DEFAULT 0 COMMENT '本次规划允许的最大站点数',
                  `generated_route_count` INT NOT NULL DEFAULT 0 COMMENT '算法生成的候选路线总数',
                  `displayed_option_count` INT NOT NULL DEFAULT 0 COMMENT '最终展示给用户的方案数',
                  `selected_option_key` VARCHAR(64) NULL COMMENT '最终选中的方案 Key',
                  `selected_route_signature` VARCHAR(255) NULL COMMENT '最终路线签名，用于去重与版本比对',
                  `total_duration` INT NULL COMMENT '总时长，单位分钟',
                  `total_cost` DECIMAL(10,2) NULL COMMENT '总花费',
                  `total_travel_time` INT NULL COMMENT '总通行时间，单位分钟',
                  `business_risk_score` INT NULL COMMENT '营业状态风险分',
                  `theme_match_count` INT NULL COMMENT '主题命中数量',
                  `route_utility` DECIMAL(12,4) NULL COMMENT '路线综合效用值',
                  `selected_route_feature_json` LONGTEXT NULL COMMENT 'selected route LTR feature vector JSON',
                  `options_feature_json` LONGTEXT NULL COMMENT 'candidate options LTR feature vector JSON',
                  `trip_date` DATE NULL COMMENT '出行日期',
                  `trip_start_time` TIME NULL COMMENT '计划开始时间',
                  `trip_end_time` TIME NULL COMMENT '计划结束时间',
                  `budget_level` VARCHAR(20) NULL COMMENT '预算等级',
                  `is_rainy` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否雨天场景',
                  `is_night` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否夜游场景',
                  `walking_level` VARCHAR(20) NULL COMMENT '步行强度等级',
                  `companion_type` VARCHAR(50) NULL COMMENT '同行人类型',
                  `themes_json` LONGTEXT NULL COMMENT '主题偏好 JSON 数组',
                  `request_snapshot_json` LONGTEXT NULL COMMENT '原始请求快照 JSON',
                  `replan_from_itinerary_id` BIGINT NULL COMMENT '重排来源行程 ID',
                  `replace_target_poi_id` BIGINT NULL COMMENT '被替换的目标 POI ID',
                  `replaced_with_poi_id` BIGINT NULL COMMENT '最终替换成的 POI ID',
                  `success_flag` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '本次规划是否成功',
                  `fail_reason` VARCHAR(255) NULL COMMENT '失败原因或未命中的业务提示',
                  `planning_started_at` DATETIME(3) NULL COMMENT '规划开始时间',
                  `planning_finished_at` DATETIME(3) NULL COMMENT '规划结束时间',
                  `cost_ms` INT NULL COMMENT '规划总耗时，单位毫秒',
                  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '写入时间',
                  PRIMARY KEY (`id`),
                  KEY `idx_route_plan_user_time` (`user_id`, `create_time`),
                  KEY `idx_route_plan_itinerary` (`itinerary_id`),
                  KEY `idx_route_plan_source_time` (`plan_source`, `create_time`),
                  KEY `idx_route_plan_trip_date` (`trip_date`),
                  KEY `idx_route_plan_success_time` (`success_flag`, `create_time`),
                  KEY `idx_route_plan_replace_target` (`replace_target_poi_id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='路线规划事实表'
                """);
        tables.put("route_node_fact", """
                CREATE TABLE IF NOT EXISTS `route_node_fact` (
                  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                  `plan_fact_id` BIGINT NOT NULL COMMENT '关联 route_plan_fact 主键',
                  `itinerary_id` BIGINT NULL COMMENT '关联行程ID',
                  `user_id` BIGINT NULL COMMENT '用户ID',
                  `option_key` VARCHAR(64) NULL COMMENT '所属方案 Key',
                  `route_signature` VARCHAR(255) NULL COMMENT '所属路线签名',
                  `step_order` INT NOT NULL COMMENT '节点顺序，从 1 开始',
                  `poi_id` BIGINT NOT NULL COMMENT 'POI ID',
                  `poi_name` VARCHAR(100) NOT NULL COMMENT 'POI 名称快照',
                  `category` VARCHAR(50) NULL COMMENT 'POI 分类快照',
                  `district` VARCHAR(50) NULL COMMENT '行政区快照',
                  `start_time` TIME NULL COMMENT '计划开始游玩时间',
                  `end_time` TIME NULL COMMENT '计划结束游玩时间',
                  `stay_duration` INT NULL COMMENT '停留时长，单位分钟',
                  `travel_time` INT NULL COMMENT '从上一节点到当前节点的通行时间，单位分钟',
                  `node_cost` DECIMAL(10,2) NULL COMMENT '当前节点花费',
                  `sys_reason` VARCHAR(255) NULL COMMENT '系统给出的节点选择理由',
                  `operating_status` VARCHAR(32) NULL COMMENT '营业状态快照',
                  `status_note` VARCHAR(255) NULL COMMENT '营业状态说明',
                  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '写入时间',
                  PRIMARY KEY (`id`),
                  UNIQUE KEY `uk_route_node_plan_step` (`plan_fact_id`, `step_order`),
                  KEY `idx_route_node_itinerary_step` (`itinerary_id`, `step_order`),
                  KEY `idx_route_node_poi_time` (`poi_id`, `create_time`),
                  KEY `idx_route_node_user_time` (`user_id`, `create_time`),
                  KEY `idx_route_node_option_key` (`option_key`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='路线节点明细事实表'
                """);
        return Collections.unmodifiableMap(tables);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String maskUrl(String url) {
        return StringUtils.hasText(url) ? url : "(empty)";
    }
}
