# 开发环境统一端口源与单一启动入口设计

**日期：** 2026-04-27  
**范围：** `F:\dachuang\frontend`、`F:\dachuang\backend`、`F:\dachuang\.env`、新增 `F:\dachuang\scripts`  
**目标：** 消除开发环境 `3000 -> 8081/18080` 端口漂移问题，建立“一个端口真相源 + 一个启动入口”的可复现开发流程

---

## 1. 背景与问题

当前项目存在两套互相独立的开发端口来源：

- 前端 `F:\dachuang\frontend\vite.config.js` 将 `/api` 代理固定写死到 `http://127.0.0.1:8081`
- 根目录 `F:\dachuang\.env` 已存在 `HOST_HTTP_PORT=18080`
- 实际运行中的后端进程曾以 `--server.port=18080` 启动

结果是：

1. 前端页面始终以为后端在 `8081`
2. 后端实际可能跑在 `18080`
3. 每次开机或换一套启动方式后，登录、注册等请求都会出现误连，表现为前端 500

本轮设计不尝试解决所有部署形态，只聚焦**本机开发启动一致性**。

---

## 2. 设计原则

1. **单一真相源**：开发后端端口只能从一处读取，不能再前后端各写一份。
2. **开发与部署解耦**：开发端口配置不干扰 Docker/onebox 运行路径。
3. **优先兼容现状**：保留当前根目录 `.env` 作为共享配置入口，减少额外认知成本。
4. **启动可复现**：从仓库根目录执行一个命令即可拉起前后端。
5. **排障可见**：启动脚本要输出最终 URL、端口和日志位置。

---

## 3. 方案概览

### 3.1 单一端口真相源

新增根目录开发变量：

- `DEV_BACKEND_PORT=18080`

它将成为**开发环境后端端口唯一来源**。

使用规则：

- **后端**：`server.port` 优先读取 `SERVER_PORT`，否则读取 `DEV_BACKEND_PORT`，最后才回退默认值
- **前端**：Vite 代理目标不再写死 `8081`，改为从根目录 `.env` 中读取 `DEV_BACKEND_PORT`

这样可以同时满足：

- 本机开发：前后端都跟随 `DEV_BACKEND_PORT`
- Docker / onebox：已有 `SERVER_PORT` 或容器内 `APP_PORT` 仍可覆盖，不受影响

### 3.2 单一启动入口

新增：

- `F:\dachuang\scripts\dev-start.ps1`

职责：

1. 从仓库根目录读取 `.env`
2. 解析 `DEV_BACKEND_PORT`
3. 检查 `3000` 和后端端口是否被占用
4. 以同一组环境变量启动后端
5. 启动前端 Vite
6. 输出最终访问地址与日志文件位置

为避免后台孤儿进程，再补充：

- `F:\dachuang\scripts\dev-stop.ps1`

职责：

- 根据启动脚本记录的 PID 停止对应前后端进程

---

## 4. 具体改动设计

### 4.1 `F:\dachuang\.env`

新增或显式维护：

```env
DEV_BACKEND_PORT=18080
```

说明：

- `HOST_HTTP_PORT` 保留给 Docker / 反向代理暴露口使用
- `DEV_BACKEND_PORT` 专门描述“本机开发时 Spring Boot 监听端口”
- 两者允许相同，但语义不再混用

### 4.2 `F:\dachuang\backend\src\main\resources\application.yml`

将当前固定值：

```yaml
server:
  port: 8081
```

调整为按优先级解析：

```yaml
server:
  port: ${SERVER_PORT:${DEV_BACKEND_PORT:8081}}
```

含义：

1. 若启动脚本或容器显式传入 `SERVER_PORT`，优先使用
2. 否则读取根目录 `.env` 中的 `DEV_BACKEND_PORT`
3. 再否则回退到 `8081`

### 4.3 `F:\dachuang\frontend\vite.config.js`

当前问题是代理目标写死：

- `target: 'http://127.0.0.1:8081'`

改造后：

1. 从仓库根目录加载 `.env`
2. 读取 `DEV_BACKEND_PORT`
3. 生成 `http://127.0.0.1:${DEV_BACKEND_PORT}`
4. 若变量缺失则回退 `8081`

为了便于测试，不建议把解析逻辑直接塞在 `defineConfig` 内。更推荐提取一个纯函数模块，例如：

- `F:\dachuang\frontend\build\resolveDevProxyTarget.js`

职责：

- 读取根目录 `.env`
- 计算代理目标
- 返回字符串结果

这样前端可以对“代理目标解析”写单测，而不是硬测整个 Vite 配置对象。

### 4.4 `F:\dachuang\scripts\dev-start.ps1`

脚本行为设计：

1. 定位仓库根目录
2. 解析 `.env`
3. 读取 `DEV_BACKEND_PORT`
4. 若 `3000` 被占用，直接报错并退出
5. 若 `DEV_BACKEND_PORT` 被占用：
   - 若已是本项目后端，提示复用或退出
   - 否则报错，避免误连其他服务
6. 启动后端：
   - 工作目录：`F:\dachuang\backend`
   - 环境变量：至少注入 `SERVER_PORT=$DEV_BACKEND_PORT`
   - 命令：优先 `mvn spring-boot:run`
7. 启动前端：
   - 工作目录：`F:\dachuang\frontend`
   - 命令：`npm run dev`
8. 将 PID 和日志路径写入运行时文件，例如：
   - `F:\dachuang\artifacts\dev-runtime\processes.json`
9. 输出：
   - 前端地址 `http://127.0.0.1:3000`
   - 后端地址 `http://127.0.0.1:<DEV_BACKEND_PORT>`
   - 日志路径

### 4.5 `F:\dachuang\scripts\dev-stop.ps1`

脚本行为设计：

1. 读取 `artifacts/dev-runtime/processes.json`
2. 校验 PID 是否仍存在
3. 仅停止启动记录内的前后端进程
4. 清理运行时记录文件

该脚本不是部署能力，只是为本机开发提供可回收启动链路。

---

## 5. 数据流与运行时关系

### 开发启动链路

1. 用户在仓库根目录运行 `scripts/dev-start.ps1`
2. 脚本读取根目录 `.env`
3. 得到 `DEV_BACKEND_PORT=18080`
4. 脚本以 `SERVER_PORT=18080` 启动后端
5. 前端 Vite 配置从同一根目录 `.env` 解析出 `18080`
6. 浏览器访问 `http://127.0.0.1:3000`
7. `/api/*` 请求被 Vite 转发到 `http://127.0.0.1:18080`

### 部署链路

部署容器继续沿用自身变量：

- `APP_PORT`
- `SERVER_PORT`
- `HOST_HTTP_PORT`

本轮不改变 `deploy/onebox/start-all.sh` 的行为，只确保开发态不会再误用部署态端口语义。

---

## 6. 错误处理

- `.env` 缺失 `DEV_BACKEND_PORT`：前后端统一回退 `8081`
- `DEV_BACKEND_PORT` 非数字：启动脚本直接失败并给出清晰错误
- 3000 已被占用：启动脚本拒绝继续，避免多份前端并存
- 后端端口被未知进程占用：启动脚本拒绝继续，避免代理到错误服务
- 启动后端失败：脚本不再继续拉前端，避免“前端活着但代理空转”
- 启动前端失败：脚本保留后端日志信息并提示手动清理或执行 `dev-stop.ps1`

---

## 7. 测试与验收

### 自动验证

前端新增解析逻辑单测，至少覆盖：

1. `.env` 中存在 `DEV_BACKEND_PORT=18080` 时，返回 `http://127.0.0.1:18080`
2. 缺失变量时回退 `http://127.0.0.1:8081`
3. 非法端口值时抛出明确错误或回退策略符合设计

### 手动验证

1. 运行 `F:\dachuang\scripts\dev-start.ps1`
2. 访问 `http://127.0.0.1:3000/auth`
3. 使用管理员账号登录
4. 确认 `/api/sessions` 经由 3000 代理后返回 200，而不是 500
5. 修改 `.env` 中 `DEV_BACKEND_PORT` 为另一空闲端口
6. 重新启动，确认前后端同时切换成功

### 回归验证

- `frontend`：`npm test`
- `frontend`：`npm run build`
- `backend`：`mvn test`

---

## 8. 非目标

- 不重构 Docker / onebox 的整体启动架构
- 不把前端开发端口 `3000` 也纳入本轮动态化
- 不实现完整进程守护器或 GUI 启动器
- 不处理生产环境 Nginx 反代策略变更

---

## 9. 推荐实施顺序

1. 先新增前端代理目标解析模块与测试
2. 再调整 `vite.config.js` 使用该解析结果
3. 再修改后端 `application.yml` 端口解析优先级
4. 最后增加 `dev-start.ps1` / `dev-stop.ps1`
5. 通过手动登录链路完成端到端验证
