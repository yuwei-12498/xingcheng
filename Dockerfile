FROM node:20-bookworm-slim AS frontend-build
WORKDIR /workspace/frontend

ARG VITE_AMAP_JS_API_KEY=""
ARG VITE_AMAP_SECURITY_JS_CODE=""
ARG VITE_AMAP_MAP_STYLE=""
ARG VITE_AMAP_JS_API_URL=""
ARG VITE_AMAP_JS_API_VERSION=""
ARG VITE_API_BASE_URL="/api"

ENV VITE_AMAP_JS_API_KEY="${VITE_AMAP_JS_API_KEY}" \
    VITE_AMAP_SECURITY_JS_CODE="${VITE_AMAP_SECURITY_JS_CODE}" \
    VITE_AMAP_MAP_STYLE="${VITE_AMAP_MAP_STYLE}" \
    VITE_AMAP_JS_API_URL="${VITE_AMAP_JS_API_URL}" \
    VITE_AMAP_JS_API_VERSION="${VITE_AMAP_JS_API_VERSION}" \
    VITE_API_BASE_URL="${VITE_API_BASE_URL}"

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/index.html ./index.html
COPY frontend/vite.config.js ./vite.config.js
COPY frontend/build ./build
COPY frontend/public ./public
COPY frontend/src ./src
RUN npm run build


FROM maven:3.9.9-eclipse-temurin-17 AS backend-build
WORKDIR /workspace/backend

COPY backend/pom.xml ./pom.xml
COPY backend/src ./src
RUN rm -f src/main/resources/application.yml \
    && cp src/main/resources/application.example.yml src/main/resources/application.yml \
    && mvn -q -DskipTests package


FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive
ENV TZ=Asia/Shanghai
ENV APP_HOME=/opt/citytrip
ENV MYSQL_DATA_DIR=/opt/citytrip/data/mysql
ENV REDIS_DATA_DIR=/opt/citytrip/data/redis
ENV MYSQL_SOCKET=/run/mysqld/mysqld.sock
ENV MYSQL_PID_FILE=/run/mysqld/mysqld.pid
ENV APP_PORT=8081
ENV NGINX_PORT=80

RUN printf '#!/bin/sh\nexit 101\n' > /usr/sbin/policy-rc.d \
    && chmod +x /usr/sbin/policy-rc.d \
    && apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        nginx \
        openjdk-17-jre-headless \
        redis-server \
        mysql-server \
        netcat \
        tini \
        tzdata \
    && rm -rf /var/lib/apt/lists/* \
    && rm -f /usr/sbin/policy-rc.d

RUN mkdir -p \
        /opt/citytrip/bin \
        /opt/citytrip/app \
        /opt/citytrip/mysql-init \
        /opt/citytrip/data/mysql \
        /opt/citytrip/data/redis \
        /opt/citytrip/logs \
        /run/mysqld \
    && rm -rf /usr/share/nginx/html/* \
    && chown -R mysql:mysql /opt/citytrip/data/mysql /run/mysqld \
    && chown -R redis:redis /opt/citytrip/data/redis \
    && chown -R www-data:www-data /var/lib/nginx /var/log/nginx /usr/share/nginx/html

COPY --from=frontend-build /workspace/frontend/dist /usr/share/nginx/html
COPY --from=backend-build /workspace/backend/target/citytrip-backend-0.0.1-SNAPSHOT.jar /opt/citytrip/app/citytrip-backend.jar

COPY deploy/onebox/nginx.conf /etc/nginx/nginx.conf
COPY deploy/onebox/start-all.sh /opt/citytrip/bin/start-all.sh
COPY deploy/onebox/mysql-init/ /opt/citytrip/mysql-init/

RUN sed -i 's/\r$//' /opt/citytrip/bin/start-all.sh \
    && chmod +x /opt/citytrip/bin/start-all.sh

ARG VCS_REF=unknown
ARG BUILD_DATE=unknown

LABEL org.opencontainers.image.title="citytrip-allinone" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.created="${BUILD_DATE}"

VOLUME ["/opt/citytrip/data"]

EXPOSE 80 8081 3306 6379

HEALTHCHECK --interval=20s --timeout=5s --start-period=120s --retries=6 \
  CMD curl -fsS -H 'Host: www.050923.xyz' http://127.0.0.1/api/pois >/dev/null || exit 1

ENTRYPOINT ["tini", "--", "/opt/citytrip/bin/start-all.sh"]
