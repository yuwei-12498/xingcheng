USE `city_trip_db`;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户私有可复用自定义 POI';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='行程编辑版本历史';
