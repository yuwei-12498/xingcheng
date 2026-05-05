USE `city_trip_db`;

DROP PROCEDURE IF EXISTS AddColumnIfMissing;
DROP PROCEDURE IF EXISTS RenameColumnIfNeeded;
DROP PROCEDURE IF EXISTS AddIndexIfMissing;
DROP PROCEDURE IF EXISTS AddUniqueIndexIfMissing;

DELIMITER //

CREATE PROCEDURE AddColumnIfMissing(IN tableName VARCHAR(64), IN columnName VARCHAR(64), IN columnDef TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = tableName
          AND COLUMN_NAME = columnName
    ) THEN
        SET @sql = CONCAT('ALTER TABLE `', tableName, '` ADD COLUMN `', columnName, '` ', columnDef);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //

CREATE PROCEDURE RenameColumnIfNeeded(IN tableName VARCHAR(64), IN oldColumnName VARCHAR(64), IN newColumnName VARCHAR(64), IN columnDef TEXT)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = tableName
          AND COLUMN_NAME = oldColumnName
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = tableName
          AND COLUMN_NAME = newColumnName
    ) THEN
        SET @sql = CONCAT(
                'ALTER TABLE `', tableName, '` CHANGE COLUMN `', oldColumnName, '` `', newColumnName, '` ',
                columnDef
        );
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //

CREATE PROCEDURE AddIndexIfMissing(IN tableName VARCHAR(64), IN indexName VARCHAR(64), IN indexDef TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = tableName
          AND INDEX_NAME = indexName
    ) THEN
        SET @sql = CONCAT('ALTER TABLE `', tableName, '` ADD INDEX `', indexName, '` ', indexDef);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //

CREATE PROCEDURE AddUniqueIndexIfMissing(IN tableName VARCHAR(64), IN indexName VARCHAR(64), IN indexDef TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = tableName
          AND INDEX_NAME = indexName
    ) THEN
        SET @sql = CONCAT('ALTER TABLE `', tableName, '` ADD UNIQUE INDEX `', indexName, '` ', indexDef);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //

DELIMITER ;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户行为事件事实表';

CALL RenameColumnIfNeeded('user_behavior_event', 'equest_id', 'request_id', 'VARCHAR(64) NULL COMMENT ''请求追踪ID''');
CALL RenameColumnIfNeeded('user_behavior_event', 'equest_uri', 'request_uri', 'VARCHAR(255) NULL COMMENT ''请求 URI''');
CALL RenameColumnIfNeeded('user_behavior_event', 'eferer', 'referer', 'VARCHAR(255) NULL COMMENT ''来源页面''');

CALL AddColumnIfMissing('user_behavior_event', 'user_id', 'BIGINT NULL COMMENT ''用户ID，匿名行为可为空''');
CALL AddColumnIfMissing('user_behavior_event', 'session_id', 'VARCHAR(64) NULL COMMENT ''会话ID，用于关联匿名会话''');
CALL AddColumnIfMissing('user_behavior_event', 'request_id', 'VARCHAR(64) NULL COMMENT ''请求追踪ID''');
CALL AddColumnIfMissing('user_behavior_event', 'event_type', 'VARCHAR(64) NOT NULL DEFAULT ''unknown'' COMMENT ''行为类型''');
CALL AddColumnIfMissing('user_behavior_event', 'event_source', 'VARCHAR(32) NOT NULL DEFAULT ''backend'' COMMENT ''事件来源''');
CALL AddColumnIfMissing('user_behavior_event', 'itinerary_id', 'BIGINT NULL COMMENT ''关联行程ID''');
CALL AddColumnIfMissing('user_behavior_event', 'poi_id', 'BIGINT NULL COMMENT ''关联 POI ID''');
CALL AddColumnIfMissing('user_behavior_event', 'option_key', 'VARCHAR(64) NULL COMMENT ''关联方案 Key''');
CALL AddColumnIfMissing('user_behavior_event', 'interaction_weight', 'DECIMAL(8,2) NOT NULL DEFAULT 1.00 COMMENT ''行为权重，用于隐式反馈建模''');
CALL AddColumnIfMissing('user_behavior_event', 'success_flag', 'TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''是否成功采集/执行''');
CALL AddColumnIfMissing('user_behavior_event', 'cost_ms', 'INT NULL COMMENT ''请求或行为耗时，单位毫秒''');
CALL AddColumnIfMissing('user_behavior_event', 'request_uri', 'VARCHAR(255) NULL COMMENT ''请求 URI''');
CALL AddColumnIfMissing('user_behavior_event', 'http_method', 'VARCHAR(16) NULL COMMENT ''HTTP 方法''');
CALL AddColumnIfMissing('user_behavior_event', 'client_ip', 'VARCHAR(64) NULL COMMENT ''客户端 IP''');
CALL AddColumnIfMissing('user_behavior_event', 'user_agent', 'VARCHAR(255) NULL COMMENT ''客户端 User-Agent''');
CALL AddColumnIfMissing('user_behavior_event', 'referer', 'VARCHAR(255) NULL COMMENT ''来源页面''');
CALL AddColumnIfMissing('user_behavior_event', 'extra_json', 'LONGTEXT NULL COMMENT ''扩展上下文 JSON''');
CALL AddColumnIfMissing('user_behavior_event', 'event_time', 'DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT ''行为发生时间''');

CALL AddIndexIfMissing('user_behavior_event', 'idx_user_behavior_user_time', '(`user_id`, `event_time`)');
CALL AddIndexIfMissing('user_behavior_event', 'idx_user_behavior_event_type_time', '(`event_type`, `event_time`)');
CALL AddIndexIfMissing('user_behavior_event', 'idx_user_behavior_itinerary_time', '(`itinerary_id`, `event_time`)');
CALL AddIndexIfMissing('user_behavior_event', 'idx_user_behavior_poi_time', '(`poi_id`, `event_time`)');
CALL AddIndexIfMissing('user_behavior_event', 'idx_user_behavior_session_time', '(`session_id`, `event_time`)');
CALL AddIndexIfMissing('user_behavior_event', 'idx_user_behavior_request_id', '(`request_id`)');

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='路线规划事实表';

CALL AddColumnIfMissing('route_plan_fact', 'user_id', 'BIGINT NULL COMMENT ''用户ID，匿名规划可为空''');
CALL AddColumnIfMissing('route_plan_fact', 'itinerary_id', 'BIGINT NULL COMMENT ''关联行程ID''');
CALL AddColumnIfMissing('route_plan_fact', 'plan_source', 'VARCHAR(32) NOT NULL DEFAULT ''generate'' COMMENT ''规划来源''');
CALL AddColumnIfMissing('route_plan_fact', 'algorithm_version', 'VARCHAR(64) NOT NULL DEFAULT ''unknown'' COMMENT ''算法版本号''');
CALL AddColumnIfMissing('route_plan_fact', 'recall_strategy', 'VARCHAR(64) NULL COMMENT ''候选召回/筛选策略标识''');
CALL AddColumnIfMissing('route_plan_fact', 'raw_candidate_count', 'INT NOT NULL DEFAULT 0 COMMENT ''原始候选 POI 数量''');
CALL AddColumnIfMissing('route_plan_fact', 'filtered_candidate_count', 'INT NOT NULL DEFAULT 0 COMMENT ''过滤后的候选 POI 数量''');
CALL AddColumnIfMissing('route_plan_fact', 'final_candidate_count', 'INT NOT NULL DEFAULT 0 COMMENT ''进入最终求解阶段的候选数量''');
CALL AddColumnIfMissing('route_plan_fact', 'max_stops', 'INT NOT NULL DEFAULT 0 COMMENT ''本次规划允许的最大站点数''');
CALL AddColumnIfMissing('route_plan_fact', 'generated_route_count', 'INT NOT NULL DEFAULT 0 COMMENT ''算法生成的候选路线总数''');
CALL AddColumnIfMissing('route_plan_fact', 'displayed_option_count', 'INT NOT NULL DEFAULT 0 COMMENT ''最终展示给用户的方案数''');
CALL AddColumnIfMissing('route_plan_fact', 'selected_option_key', 'VARCHAR(64) NULL COMMENT ''最终选中的方案 Key''');
CALL AddColumnIfMissing('route_plan_fact', 'selected_route_signature', 'VARCHAR(255) NULL COMMENT ''最终路线签名''');
CALL AddColumnIfMissing('route_plan_fact', 'total_duration', 'INT NULL COMMENT ''总时长，单位分钟''');
CALL AddColumnIfMissing('route_plan_fact', 'total_cost', 'DECIMAL(10,2) NULL COMMENT ''总花费''');
CALL AddColumnIfMissing('route_plan_fact', 'total_travel_time', 'INT NULL COMMENT ''总通行时间，单位分钟''');
CALL AddColumnIfMissing('route_plan_fact', 'business_risk_score', 'INT NULL COMMENT ''营业状态风险分''');
CALL AddColumnIfMissing('route_plan_fact', 'theme_match_count', 'INT NULL COMMENT ''主题命中数量''');
CALL AddColumnIfMissing('route_plan_fact', 'route_utility', 'DECIMAL(12,4) NULL COMMENT ''路线综合效用值''');
CALL AddColumnIfMissing('route_plan_fact', 'trip_date', 'DATE NULL COMMENT ''出行日期''');
CALL AddColumnIfMissing('route_plan_fact', 'trip_start_time', 'TIME NULL COMMENT ''计划开始时间''');
CALL AddColumnIfMissing('route_plan_fact', 'trip_end_time', 'TIME NULL COMMENT ''计划结束时间''');
CALL AddColumnIfMissing('route_plan_fact', 'budget_level', 'VARCHAR(20) NULL COMMENT ''预算等级''');
CALL AddColumnIfMissing('route_plan_fact', 'is_rainy', 'TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否雨天场景''');
CALL AddColumnIfMissing('route_plan_fact', 'is_night', 'TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否夜游场景''');
CALL AddColumnIfMissing('route_plan_fact', 'walking_level', 'VARCHAR(20) NULL COMMENT ''步行强度等级''');
CALL AddColumnIfMissing('route_plan_fact', 'companion_type', 'VARCHAR(50) NULL COMMENT ''同行人类型''');
CALL AddColumnIfMissing('route_plan_fact', 'themes_json', 'LONGTEXT NULL COMMENT ''主题偏好 JSON 数组''');
CALL AddColumnIfMissing('route_plan_fact', 'request_snapshot_json', 'LONGTEXT NULL COMMENT ''原始请求快照 JSON''');
CALL AddColumnIfMissing('route_plan_fact', 'replan_from_itinerary_id', 'BIGINT NULL COMMENT ''重排来源行程 ID''');
CALL AddColumnIfMissing('route_plan_fact', 'replace_target_poi_id', 'BIGINT NULL COMMENT ''被替换的目标 POI ID''');
CALL AddColumnIfMissing('route_plan_fact', 'replaced_with_poi_id', 'BIGINT NULL COMMENT ''最终替换成的 POI ID''');
CALL AddColumnIfMissing('route_plan_fact', 'success_flag', 'TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''本次规划是否成功''');
CALL AddColumnIfMissing('route_plan_fact', 'fail_reason', 'VARCHAR(255) NULL COMMENT ''失败原因或未命中的业务提示''');
CALL AddColumnIfMissing('route_plan_fact', 'planning_started_at', 'DATETIME(3) NULL COMMENT ''规划开始时间''');
CALL AddColumnIfMissing('route_plan_fact', 'planning_finished_at', 'DATETIME(3) NULL COMMENT ''规划结束时间''');
CALL AddColumnIfMissing('route_plan_fact', 'cost_ms', 'INT NULL COMMENT ''规划总耗时，单位毫秒''');
CALL AddColumnIfMissing('route_plan_fact', 'create_time', 'DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT ''写入时间''');

CALL AddIndexIfMissing('route_plan_fact', 'idx_route_plan_user_time', '(`user_id`, `create_time`)');
CALL AddIndexIfMissing('route_plan_fact', 'idx_route_plan_itinerary', '(`itinerary_id`)');
CALL AddIndexIfMissing('route_plan_fact', 'idx_route_plan_source_time', '(`plan_source`, `create_time`)');
CALL AddIndexIfMissing('route_plan_fact', 'idx_route_plan_trip_date', '(`trip_date`)');
CALL AddIndexIfMissing('route_plan_fact', 'idx_route_plan_success_time', '(`success_flag`, `create_time`)');
CALL AddIndexIfMissing('route_plan_fact', 'idx_route_plan_replace_target', '(`replace_target_poi_id`)');

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='路线节点明细事实表';

CALL AddColumnIfMissing('route_node_fact', 'plan_fact_id', 'BIGINT NOT NULL COMMENT ''关联 route_plan_fact 主键''');
CALL AddColumnIfMissing('route_node_fact', 'itinerary_id', 'BIGINT NULL COMMENT ''关联行程ID''');
CALL AddColumnIfMissing('route_node_fact', 'user_id', 'BIGINT NULL COMMENT ''用户ID''');
CALL AddColumnIfMissing('route_node_fact', 'option_key', 'VARCHAR(64) NULL COMMENT ''所属方案 Key''');
CALL AddColumnIfMissing('route_node_fact', 'route_signature', 'VARCHAR(255) NULL COMMENT ''所属路线签名''');
CALL AddColumnIfMissing('route_node_fact', 'step_order', 'INT NOT NULL DEFAULT 0 COMMENT ''节点顺序，从 1 开始''');
CALL AddColumnIfMissing('route_node_fact', 'poi_id', 'BIGINT NOT NULL DEFAULT 0 COMMENT ''POI ID''');
CALL AddColumnIfMissing('route_node_fact', 'poi_name', 'VARCHAR(100) NOT NULL DEFAULT '''' COMMENT ''POI 名称快照''');
CALL AddColumnIfMissing('route_node_fact', 'category', 'VARCHAR(50) NULL COMMENT ''POI 分类快照''');
CALL AddColumnIfMissing('route_node_fact', 'district', 'VARCHAR(50) NULL COMMENT ''行政区快照''');
CALL AddColumnIfMissing('route_node_fact', 'start_time', 'TIME NULL COMMENT ''计划开始游玩时间''');
CALL AddColumnIfMissing('route_node_fact', 'end_time', 'TIME NULL COMMENT ''计划结束游玩时间''');
CALL AddColumnIfMissing('route_node_fact', 'stay_duration', 'INT NULL COMMENT ''停留时长，单位分钟''');
CALL AddColumnIfMissing('route_node_fact', 'travel_time', 'INT NULL COMMENT ''从上一节点到当前节点的通行时间，单位分钟''');
CALL AddColumnIfMissing('route_node_fact', 'node_cost', 'DECIMAL(10,2) NULL COMMENT ''当前节点花费''');
CALL AddColumnIfMissing('route_node_fact', 'sys_reason', 'VARCHAR(255) NULL COMMENT ''系统给出的节点选择理由''');
CALL AddColumnIfMissing('route_node_fact', 'operating_status', 'VARCHAR(32) NULL COMMENT ''营业状态快照''');
CALL AddColumnIfMissing('route_node_fact', 'status_note', 'VARCHAR(255) NULL COMMENT ''营业状态说明''');
CALL AddColumnIfMissing('route_node_fact', 'create_time', 'DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT ''写入时间''');

CALL AddUniqueIndexIfMissing('route_node_fact', 'uk_route_node_plan_step', '(`plan_fact_id`, `step_order`)');
CALL AddIndexIfMissing('route_node_fact', 'idx_route_node_itinerary_step', '(`itinerary_id`, `step_order`)');
CALL AddIndexIfMissing('route_node_fact', 'idx_route_node_poi_time', '(`poi_id`, `create_time`)');
CALL AddIndexIfMissing('route_node_fact', 'idx_route_node_user_time', '(`user_id`, `create_time`)');
CALL AddIndexIfMissing('route_node_fact', 'idx_route_node_option_key', '(`option_key`)');

DROP PROCEDURE IF EXISTS AddColumnIfMissing;
DROP PROCEDURE IF EXISTS RenameColumnIfNeeded;
DROP PROCEDURE IF EXISTS AddIndexIfMissing;
DROP PROCEDURE IF EXISTS AddUniqueIndexIfMissing;
