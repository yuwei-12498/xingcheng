# 单镜像路演部署说明

## 方案说明

这个方案不是把 MySQL、Redis、Nginx、Spring Boot 分成多个容器，而是做成 **一个完整镜像**：

- 前端：Vite 打包后由 Nginx 直接托管
- 后端：Spring Boot Jar
- 数据库：MySQL 8
- 缓存：Redis
- 反向代理：Nginx

镜像首次启动时会自动：

1. 初始化 MySQL 数据目录
2. 导入 `deploy/onebox/mysql-init/001-citytrip-bootstrap.sql`
3. 内置演示账号
4. 启动 Redis、后端、Nginx

## 默认演示账号

- 普通演示账号：`demo / demo123456`
- 管理员账号：`roadshow_admin / Admin123456`

## 本地构建

```bash
cp .env.example .env
docker compose build
docker compose up -d
```

## 推送到 Docker Hub

先把 `.env` 里的 `DOCKERHUB_IMAGE` 改成你的仓库名，例如：

```env
DOCKERHUB_IMAGE=yourname/citytrip-allinone
IMAGE_TAG=latest
```

然后执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\deploy\onebox\push-dockerhub.ps1
```

## 云服务器部署

服务器上只需要 Docker 和 Compose 插件，然后：

```bash
docker compose pull
docker compose up -d
docker compose logs -f
```
