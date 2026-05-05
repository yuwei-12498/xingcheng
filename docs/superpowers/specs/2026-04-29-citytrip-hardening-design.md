# CityTrip 工程硬化修复设计

日期：2026-04-29

## 范围

本次只处理工程代码问题，创新点暂不实现、不包装、不写入产品能力。目标是在不破坏现有功能的基础上，把已识别的优先级 1-8 问题做成可维护、可测试、可复现的长期方案，避免用临时环境变量或一次性绕过手段掩盖问题。

## 设计原则

1. 保留原有 Web / 小程序 / Flutter 调用语义；必要的安全默认值改变必须提供明确配置模板。
2. 修复测试不是跳过测试，而是让不同测试运行器各自负责自己的测试类型。
3. 配置和数据库迁移要能在新环境复现，不依赖个人机器上的隐式状态。
4. 管理员账号引导不能再内置危险默认密码；如需自动创建，必须显式配置强密码。
5. AI 类能力要有稳定的并发保护、单用户节流、超时预算和失败响应，不把资源控制交给前端等待。
6. DTO validation 只先覆盖外部入口和高风险请求，减少行为突变。
7. API contract 先用轻量 Markdown + JSON 示例落地，后续再升级 OpenAPI/codegen。

## 优先级 1：测试基线修复

前端：`frontend/build/__tests__` 当前使用 Node 内置 `node:test`，被 Vitest 收集后报 “No test suite found”。长期方案是拆分测试职责：Vitest 只收集 `frontend/src` 下的 Vue/工具测试，Node build 配置测试由 `npm run test:node` 执行，`npm test` 串行执行两类测试。

后端：`AuthAndItineraryControllerTest` 随新增 Mapper 扩展 mock 清单，保证 Web slice 测试不意外创建真实数据访问 bean。`DataSourceConfigFallbackTest` 改为直接验证 `DbCompatibilityEnvironmentPostProcessor`，不启动完整 Spring Boot 应用和真实 MySQL。

## 优先级 2：配置模板

新增 `backend/src/main/resources/application.example.yml`，列出可复现启动所需配置。示例文件不包含真实 secret，不提供危险密码默认值，并解释哪些配置在开发环境可关、哪些生产环境必须显式设置。

## 优先级 3：数据库迁移

引入 Flyway，新增 `backend/src/main/resources/db/migration`。第一阶段先迁移新增/当前最容易漂移的表结构，把启动期补表逻辑逐步转为迁移脚本来源。为避免破坏已有开发库，迁移采用 `baseline-on-migrate` 并保留现有 SQL 历史文件。

## 优先级 4：前端巨型组件拆分

不做视觉重写，只做边界拆分：从 `Result.vue` 和 `ItineraryMapCard.vue` 中抽出纯工具函数和小型展示组件。保留现有 prop、事件、store 交互和 CSS 语义，每次拆分用现有测试/新增回归测试保护。

## 优先级 5：管理员默认密码

`AdminAccountBootstrapRunner` 默认不启用；启用时必须显式设置非空且非 `Admin123456` 的密码。`sync-password` 默认关闭，避免每次启动把管理员密码重置成配置值。配置错误时 fail fast，而不是静默创建弱口令账号。

## 优先级 6：AI 限流与超时预算

新增后端本地请求保护组件，对 chat、stream chat、smart-fill、generate 分场景配置最大并发、单用户/匿名请求冷却和超时预算。控制逻辑位于后端，可测试、可配置、可统一返回 `SystemBusyException`，不是依赖前端 timeout。

## 优先级 7：DTO validation

引入 `spring-boot-starter-validation`，在 Generate、SmartFill、Chat 以及关键编辑请求 DTO 上加基础约束；Controller 使用 `@Valid`。错误响应复用全局异常处理，保证非法请求返回 400。

## 优先级 8：共享 API contract

新增 `docs/api-contract/citytrip-api-contract.md`，记录 Web/小程序/Flutter 共用的核心请求/响应字段和 JSON 示例。前端 API 层保留现状，但把关键路径与契约文件对齐，作为后续 OpenAPI 化的落脚点。

## 验证

每个阶段至少运行对应最小测试；最终运行：

- `cd backend && mvn test`
- `cd backend && mvn -q -DskipTests package`
- `cd frontend && npm test`
- `cd frontend && npm run build`
