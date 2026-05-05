USE `city_trip_db`;

-- Procedure to safely add a column
DROP PROCEDURE IF EXISTS AddColumn;
DELIMITER //
CREATE PROCEDURE AddColumn(IN tableName VARCHAR(64), IN columnName VARCHAR(64), IN columnDef VARCHAR(255))
BEGIN
    IF NOT EXISTS (
        SELECT *
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
DELIMITER ;

DROP PROCEDURE IF EXISTS AddIndexIfMissing;
DELIMITER //
CREATE PROCEDURE AddIndexIfMissing(IN tableName VARCHAR(64), IN indexName VARCHAR(64), IN indexDef VARCHAR(255))
BEGIN
    IF NOT EXISTS (
        SELECT *
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
DELIMITER ;

-- Apply POI upgrades
CALL AddColumn('poi', 'closed_weekdays', 'VARCHAR(100) NULL AFTER `close_time`');
CALL AddColumn('poi', 'temporarily_closed', 'TINYINT(1) NOT NULL DEFAULT 0 AFTER `closed_weekdays`');
CALL AddColumn('poi', 'status_note', 'VARCHAR(255) NULL AFTER `temporarily_closed`');
CALL AddColumn('poi', 'status_source', 'VARCHAR(50) NOT NULL DEFAULT "seed" AFTER `status_note`');
CALL AddColumn('poi', 'status_updated_at', 'DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER `status_source`');
CALL AddColumn('poi', 'created_at', 'DATETIME NULL DEFAULT CURRENT_TIMESTAMP');
CALL AddColumn('poi', 'updated_at', 'DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');
CALL AddColumn('poi', 'created_by', 'BIGINT NULL');
CALL AddColumn('poi', 'updated_by', 'BIGINT NULL');
CALL AddColumn('poi', 'crowd_penalty', 'DECIMAL(6,2) NULL DEFAULT 0.00 COMMENT "拥挤惩罚系数，值越高越拥挤"');

-- Apply User upgrades
CALL AddColumn('trip_user', 'role', 'TINYINT(1) NOT NULL DEFAULT 0 COMMENT "瑙掕壊锛?-鏅€氱敤鎴? 1-绠＄悊鍛? AFTER `nickname`');
CALL AddColumn('trip_user', 'status', 'TINYINT(1) NOT NULL DEFAULT 1 COMMENT "鐘舵€侊細1-姝ｅ父, 0-鍐荤粨灏佺" AFTER `role`');
CALL AddColumn('trip_user', 'avatar', 'VARCHAR(255) NULL');
CALL AddColumn('trip_user', 'gender', 'TINYINT NULL COMMENT "0-鏈煡, 1-鐢? 2-濂?');

-- Apply SavedItinerary upgrades
CREATE TABLE IF NOT EXISTS `saved_itinerary` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id` BIGINT NOT NULL,
  `request_json` LONGTEXT NOT NULL,
  `itinerary_json` LONGTEXT NOT NULL,
  `custom_title` VARCHAR(120) NULL,
  `is_public` TINYINT(1) NOT NULL DEFAULT 0,
  `node_count` INT NOT NULL DEFAULT 0,
  `total_duration` INT NULL,
  `total_cost` DECIMAL(10, 2) NULL,
  `route_signature` VARCHAR(255) NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY `idx_saved_itinerary_user` (`user_id`)
) ENGINE=InnoDB COMMENT='Saved itinerary snapshots';

CALL AddColumn('saved_itinerary', 'favorited', 'TINYINT(1) NOT NULL DEFAULT 0');
CALL AddColumn('saved_itinerary', 'favorite_time', 'DATETIME NULL');
CALL AddColumn('saved_itinerary', 'is_public', 'TINYINT(1) NOT NULL DEFAULT 0');
CALL AddColumn('saved_itinerary', 'share_note', 'VARCHAR(300) NULL AFTER `custom_title`');
CALL AddColumn('saved_itinerary', 'is_favorite', 'TINYINT(1) NOT NULL DEFAULT 0');
CALL AddColumn('saved_itinerary', 'is_history', 'TINYINT(1) NOT NULL DEFAULT 1');
CALL AddColumn('saved_itinerary', 'source_page', 'VARCHAR(50) NULL COMMENT "鐢熸垚鏉ユ簮椤甸潰鏍囪瘑"');
CALL AddColumn('saved_itinerary', 'is_deleted', 'TINYINT(1) NOT NULL DEFAULT 0 COMMENT "community soft delete flag"');
CALL AddColumn('saved_itinerary', 'deleted_at', 'DATETIME NULL COMMENT "community deletion time"');
CALL AddColumn('saved_itinerary', 'deleted_by', 'BIGINT NULL COMMENT "community deletion operator"');
CALL AddColumn('saved_itinerary', 'is_global_pinned', 'TINYINT(1) NOT NULL DEFAULT 0 COMMENT "community global pinned flag"');
CALL AddColumn('saved_itinerary', 'global_pinned_at', 'DATETIME NULL COMMENT "community global pinned time"');
CALL AddColumn('saved_itinerary', 'global_pinned_by', 'BIGINT NULL COMMENT "community global pinned operator"');
CALL AddColumn('saved_itinerary', 'pinned_comment_id', 'BIGINT NULL COMMENT "community author pinned comment id"');
CALL AddIndexIfMissing('saved_itinerary', 'idx_saved_itinerary_public_deleted_updated', '(`is_public`, `is_deleted`, `update_time`)');
CALL AddIndexIfMissing('saved_itinerary', 'idx_saved_itinerary_global_pinned', '(`is_global_pinned`, `global_pinned_at`)');

-- Apply Community upgrades
CREATE TABLE IF NOT EXISTS `community_comment` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `itinerary_id` BIGINT NOT NULL,
  `parent_id` BIGINT NULL,
  `user_id` BIGINT NOT NULL,
  `content` VARCHAR(300) NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY `idx_community_comment_itinerary` (`itinerary_id`),
  KEY `idx_community_comment_parent` (`parent_id`),
  KEY `idx_community_comment_user` (`user_id`)
) ENGINE=InnoDB COMMENT='Community itinerary comments';

CALL AddColumn('community_comment', 'parent_id', 'BIGINT NULL AFTER `itinerary_id`');

CREATE TABLE IF NOT EXISTS `community_like` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `itinerary_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_community_like_user_itinerary` (`itinerary_id`, `user_id`),
  KEY `idx_community_like_user` (`user_id`)
) ENGINE=InnoDB COMMENT='Community itinerary likes';

-- Data maintenance
UPDATE `trip_user` SET `role` = 1 WHERE `username` = 'admin';
UPDATE `trip_user` SET `status` = 1 WHERE `status` = 0; -- Ensure existing users aren't locked out

DROP PROCEDURE IF EXISTS AddIndexIfMissing;
DROP PROCEDURE IF EXISTS AddColumn;

source F:/dachuang/backend/sql/upgrade_analytics_fact_tables_20260417.sql;
source F:/dachuang/backend/sql/upgrade_itinerary_edit_tables_20260429.sql;
source F:/dachuang/backend/sql/upgrade_poi_city_dimension_20260424.sql;
source F:/dachuang/backend/sql/seed_poi_chengdu_enriched_20260424_final.sql;
