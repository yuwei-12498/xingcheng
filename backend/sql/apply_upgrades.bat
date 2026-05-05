@echo off
if "%MYSQL_PWD%"=="" (
  echo Please set MYSQL_PWD before running this script.
  exit /b 1
)
mysql -u root --default-character-set=utf8mb4 -e "source f:/dachuang/backend/sql/full_upgrade_safe.sql"
