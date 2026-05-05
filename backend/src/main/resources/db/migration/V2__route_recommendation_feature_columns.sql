-- Adds LTR feature-vector columns for route recommendation telemetry on databases
-- that already applied V1 before the feature columns were introduced.

SET @has_selected_route_feature_json = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'route_plan_fact'
    AND COLUMN_NAME = 'selected_route_feature_json'
);
SET @ddl_selected_route_feature_json = IF(
  @has_selected_route_feature_json = 0,
  'ALTER TABLE `route_plan_fact` ADD COLUMN `selected_route_feature_json` LONGTEXT NULL COMMENT ''selected route LTR feature vector JSON'' AFTER `route_utility`',
  'SELECT 1'
);
PREPARE stmt_selected_route_feature_json FROM @ddl_selected_route_feature_json;
EXECUTE stmt_selected_route_feature_json;
DEALLOCATE PREPARE stmt_selected_route_feature_json;

SET @has_options_feature_json = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'route_plan_fact'
    AND COLUMN_NAME = 'options_feature_json'
);
SET @ddl_options_feature_json = IF(
  @has_options_feature_json = 0,
  'ALTER TABLE `route_plan_fact` ADD COLUMN `options_feature_json` LONGTEXT NULL COMMENT ''candidate options LTR feature vector JSON'' AFTER `selected_route_feature_json`',
  'SELECT 1'
);
PREPARE stmt_options_feature_json FROM @ddl_options_feature_json;
EXECUTE stmt_options_feature_json;
DEALLOCATE PREPARE stmt_options_feature_json;
