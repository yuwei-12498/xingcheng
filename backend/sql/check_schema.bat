@echo off
if "%MYSQL_PWD%"=="" (
  echo Please set MYSQL_PWD before running this script.
  exit /b 1
)
mysql -u root -e "USE city_trip_db; DESC poi;" > poi_schema.txt
mysql -u root -e "USE city_trip_db; DESC trip_user;" > user_schema.txt
mysql -u root -e "USE city_trip_db; SHOW TABLES;" > tables.txt
