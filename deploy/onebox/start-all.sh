#!/usr/bin/env bash
set -Eeuo pipefail

log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

require_secret_env() {
  local name="$1"
  local value="${!name:-}"
  if [[ -z "${value}" ]]; then
    log "ERROR: ${name} is required and must not be empty."
    exit 64
  fi
  if [[ "${value}" == ChangeMe* ]] || [[ "${value}" == *replace-with* ]] || [[ "${value}" == your-* ]] || [[ "${value}" == example-* ]]; then
    log "ERROR: ${name} looks like a placeholder. Set a strong unique value before starting."
    exit 64
  fi
}

cleanup() {
  local exit_code=$?
  log "Shutdown signal received. Stopping all processes..."
  for pid in ${NGINX_PID:-} ${JAVA_PID:-} ${REDIS_PID:-} ${MYSQL_PID:-}; do
    if [[ -n "${pid:-}" ]] && kill -0 "$pid" 2>/dev/null; then
      kill "$pid" 2>/dev/null || true
    fi
  done
  wait || true
  exit "$exit_code"
}

trap cleanup INT TERM EXIT

export TZ="${TZ:-Asia/Shanghai}"
export MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-}"
export APP_DB_NAME="${APP_DB_NAME:-city_trip_db}"
export APP_DB_USERNAME="${APP_DB_USERNAME:-citytrip}"
export APP_DB_PASSWORD="${APP_DB_PASSWORD:-}"
export REDIS_PASSWORD="${REDIS_PASSWORD:-}"
export APP_JWT_SECRET="${APP_JWT_SECRET:-}"
export OPENAI_BASE_URL="${OPENAI_BASE_URL:-https://api.openai.com/v1}"
export OPENAI_MODEL="${OPENAI_MODEL:-gpt-5.4}"
export APP_AUTH_MAIL_FROM="${APP_AUTH_MAIL_FROM:-noreply@050923.xyz}"
export JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx1024m}"
export APP_PORT="${APP_PORT:-8081}"
export NGINX_PORT="${NGINX_PORT:-80}"

require_secret_env MYSQL_ROOT_PASSWORD
require_secret_env APP_DB_PASSWORD
require_secret_env REDIS_PASSWORD
require_secret_env APP_JWT_SECRET

if [[ -z "${OPENAI_API_KEY:-}" ]]; then
  export LLM_PROVIDER="${LLM_PROVIDER:-mock}"
  export LLM_FALLBACK_TO_MOCK="${LLM_FALLBACK_TO_MOCK:-true}"
  export LLM_OPENAI_ENABLED="${LLM_OPENAI_ENABLED:-false}"
else
  export LLM_PROVIDER="${LLM_PROVIDER:-real}"
  export LLM_FALLBACK_TO_MOCK="${LLM_FALLBACK_TO_MOCK:-false}"
  export LLM_OPENAI_ENABLED="${LLM_OPENAI_ENABLED:-true}"
fi

MYSQL_DATA_DIR="${MYSQL_DATA_DIR:-/opt/citytrip/data/mysql}"
REDIS_DATA_DIR="${REDIS_DATA_DIR:-/opt/citytrip/data/redis}"
MYSQL_SOCKET="${MYSQL_SOCKET:-/run/mysqld/mysqld.sock}"
MYSQL_PID_FILE="${MYSQL_PID_FILE:-/run/mysqld/mysqld.pid}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
REDIS_PORT="${REDIS_PORT:-6379}"

mkdir -p "$MYSQL_DATA_DIR" "$REDIS_DATA_DIR" /run/mysqld /opt/citytrip/logs /var/log/nginx /var/lib/nginx
chown -R mysql:mysql "$MYSQL_DATA_DIR" /run/mysqld
chown -R redis:redis "$REDIS_DATA_DIR"
chown -R www-data:www-data /var/log/nginx /var/lib/nginx /usr/share/nginx/html

wait_for_mysql() {
  local retries="${1:-60}"
  for ((i=1; i<=retries; i++)); do
    if mysqladmin --protocol=tcp -h127.0.0.1 -P"${MYSQL_PORT}" -uroot -p"${MYSQL_ROOT_PASSWORD}" ping >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  return 1
}

wait_for_redis() {
  local retries="${1:-60}"
  for ((i=1; i<=retries; i++)); do
    if redis-cli -a "${REDIS_PASSWORD}" -p "${REDIS_PORT}" ping >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  return 1
}

initialize_mysql_if_needed() {
  if [[ -d "${MYSQL_DATA_DIR}/mysql" ]]; then
    log "Existing MySQL data directory detected. Skip initialization."
    return
  fi

  log "First boot detected. Initializing MySQL data directory..."
  mysqld --initialize-insecure \
    --user=mysql \
    --datadir="${MYSQL_DATA_DIR}"

  log "Starting temporary MySQL for bootstrap import..."
  mysqld \
    --user=mysql \
    --datadir="${MYSQL_DATA_DIR}" \
    --socket="${MYSQL_SOCKET}" \
    --pid-file="${MYSQL_PID_FILE}" \
    --bind-address=127.0.0.1 \
    --port="${MYSQL_PORT}" \
    --daemonize

  for ((i=1; i<=60; i++)); do
    if mysqladmin --protocol=socket --socket="${MYSQL_SOCKET}" -uroot ping >/dev/null 2>&1; then
      break
    fi
    sleep 1
  done

  mysql --protocol=socket --socket="${MYSQL_SOCKET}" -uroot <<SQL
ALTER USER 'root'@'localhost' IDENTIFIED BY '${MYSQL_ROOT_PASSWORD}';
CREATE DATABASE IF NOT EXISTS \`${APP_DB_NAME}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${APP_DB_USERNAME}'@'%' IDENTIFIED BY '${APP_DB_PASSWORD}';
GRANT ALL PRIVILEGES ON \`${APP_DB_NAME}\`.* TO '${APP_DB_USERNAME}'@'%';
FLUSH PRIVILEGES;
SQL

  for sql_file in /opt/citytrip/mysql-init/*.sql; do
    [[ -f "${sql_file}" ]] || continue
    log "Running bootstrap SQL: ${sql_file}"
    mysql --protocol=tcp -h127.0.0.1 -P"${MYSQL_PORT}" -uroot -p"${MYSQL_ROOT_PASSWORD}" < "${sql_file}"
  done

  mysqladmin --protocol=tcp -h127.0.0.1 -P"${MYSQL_PORT}" -uroot -p"${MYSQL_ROOT_PASSWORD}" shutdown
  log "MySQL bootstrap completed."
}

start_mysql() {
  log "Starting MySQL..."
  mysqld \
    --user=mysql \
    --datadir="${MYSQL_DATA_DIR}" \
    --socket="${MYSQL_SOCKET}" \
    --pid-file="${MYSQL_PID_FILE}" \
    --bind-address=0.0.0.0 \
    --port="${MYSQL_PORT}" &
  MYSQL_PID=$!
  wait_for_mysql 90
  log "MySQL is ready."
}

start_redis() {
  log "Starting Redis..."
  runuser -u redis -- redis-server \
    --bind 0.0.0.0 \
    --protected-mode yes \
    --port "${REDIS_PORT}" \
    --appendonly yes \
    --dir "${REDIS_DATA_DIR}" \
    --requirepass "${REDIS_PASSWORD}" &
  REDIS_PID=$!
  wait_for_redis 60
  log "Redis is ready."
}

start_backend() {
  log "Starting Spring Boot backend..."
  export DB_URL="jdbc:mysql://127.0.0.1:${MYSQL_PORT}/${APP_DB_NAME}?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia%2FShanghai&allowPublicKeyRetrieval=true&useSSL=false"
  export DB_USERNAME="${APP_DB_USERNAME}"
  export DB_PASSWORD="${APP_DB_PASSWORD}"
  export REDIS_HOST="127.0.0.1"
  export REDIS_PORT="${REDIS_PORT}"
  export APP_REDIS_ENABLED="true"
  export APP_GEO_ENABLED="${APP_GEO_ENABLED:-true}"
  export APP_AMAP_ENABLED="${APP_AMAP_ENABLED:-true}"
  export REDIS_TIMEOUT="${REDIS_TIMEOUT:-3s}"
  export APP_DB_VERIFY_ON_STARTUP="${APP_DB_VERIFY_ON_STARTUP:-true}"
  export APP_REQUIRE_EXTERNAL_SERVICES="${APP_REQUIRE_EXTERNAL_SERVICES:-true}"
  export APP_JWT_EXPIRATION_HOURS="${APP_JWT_EXPIRATION_HOURS:-24}"
  export SERVER_PORT="${APP_PORT}"

  java ${JAVA_OPTS} -jar /opt/citytrip/app/citytrip-backend.jar > /opt/citytrip/logs/backend.out.log 2>&1 &
  JAVA_PID=$!
  log "Spring Boot started. PID=${JAVA_PID}"
}

start_nginx() {
  log "Starting Nginx..."
  nginx -g 'daemon off;' &
  NGINX_PID=$!
  log "Nginx started. PID=${NGINX_PID}"
}

initialize_mysql_if_needed
start_mysql
start_redis
start_backend
start_nginx

log "All services started: Nginx=${NGINX_PID} Backend=${JAVA_PID} Redis=${REDIS_PID} MySQL=${MYSQL_PID}"

wait -n "${MYSQL_PID}" "${REDIS_PID}" "${JAVA_PID}" "${NGINX_PID}"
log "A core process exited. Container will stop now."
