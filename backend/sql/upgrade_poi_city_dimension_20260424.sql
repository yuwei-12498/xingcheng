-- POI 城市维度增强（多城市规划）
-- 安全幂等：可重复执行

DROP PROCEDURE IF EXISTS EnsurePoiCityDimension;
DELIMITER $$
CREATE PROCEDURE EnsurePoiCityDimension()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'poi'
          AND COLUMN_NAME = 'city_code'
    ) THEN
        ALTER TABLE poi
            ADD COLUMN city_code VARCHAR(16) NULL COMMENT 'planning city code';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'poi'
          AND COLUMN_NAME = 'city_name'
    ) THEN
        ALTER TABLE poi
            ADD COLUMN city_name VARCHAR(64) NULL COMMENT 'planning city name';
    END IF;

    UPDATE poi
    SET city_code = COALESCE(NULLIF(city_code, ''), 'CD'),
        city_name = COALESCE(NULLIF(city_name, ''), '成都')
    WHERE city_code IS NULL
       OR city_code = ''
       OR city_name IS NULL
       OR city_name = '';

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'poi'
          AND INDEX_NAME = 'idx_poi_city_code_priority_id'
    ) THEN
        CREATE INDEX idx_poi_city_code_priority_id
            ON poi (city_code, priority_score, id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'poi'
          AND INDEX_NAME = 'idx_poi_city_name_priority_id'
    ) THEN
        CREATE INDEX idx_poi_city_name_priority_id
            ON poi (city_name, priority_score, id);
    END IF;
END$$
DELIMITER ;

CALL EnsurePoiCityDimension();
DROP PROCEDURE IF EXISTS EnsurePoiCityDimension;
