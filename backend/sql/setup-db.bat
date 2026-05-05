@echo off
if "%MYSQL_PWD%"=="" (
  echo Please set MYSQL_PWD before running this script.
  exit /b 1
)
mysql -u root --default-character-set=utf8mb4 -e "USE city_trip_db; source f:/dachuang/backend/sql/init.sql; source f:/dachuang/backend/sql/upgrade_poi_city_dimension_20260424.sql; source f:/dachuang/backend/sql/upgrade_itinerary_edit_tables_20260429.sql; source f:/dachuang/backend/sql/seed_poi_chengdu_enriched_20260424_final.sql;"
