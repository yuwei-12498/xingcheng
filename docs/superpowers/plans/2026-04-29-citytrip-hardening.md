# CityTrip 工程硬化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复优先级 1-8 的工程问题，让测试、配置、迁移、安全、限流、validation 和 API contract 具备长期可维护基础。

**Architecture:** 先修测试基线，再做安全和配置默认值调整；数据库迁移、限流、validation 和 contract 以小步方式接入，不改业务主流程语义。前端大组件先抽纯函数和小展示单元，不做 UI 重写。

**Tech Stack:** Spring Boot 3.4、MyBatis-Plus、Flyway、Jakarta Validation、Vue 3、Vitest、Node test。

---

## File Map

- Modify: `F:/dachuang/frontend/package.json` — 分离 Vitest 与 Node build 测试。
- Modify: `F:/dachuang/frontend/vitest.config.js` — 限定 Vitest 收集范围。
- Modify: `F:/dachuang/backend/src/test/java/com/citytrip/controller/AuthAndItineraryControllerTest.java` — 补齐 Web slice mocks。
- Modify: `F:/dachuang/backend/src/test/java/com/citytrip/config/DataSourceConfigFallbackTest.java` — 改为环境后处理器单测。
- Modify: `F:/dachuang/backend/pom.xml` — 加 validation、Flyway 依赖。
- Modify: `F:/dachuang/backend/src/main/resources/application.yml` — 安全默认值、Flyway、AI guard 配置。
- Create: `F:/dachuang/backend/src/main/resources/application.example.yml` — 可复现配置模板。
- Create: `F:/dachuang/backend/src/main/resources/db/migration/V1__baseline_core_schema.sql` — 核心基线迁移。
- Modify: `F:/dachuang/backend/src/main/java/com/citytrip/config/AdminAccountBootstrapRunner.java` — 移除危险默认密码。
- Create: `F:/dachuang/backend/src/main/java/com/citytrip/config/AiRequestGuardProperties.java` — AI 请求保护配置。
- Create: `F:/dachuang/backend/src/main/java/com/citytrip/service/guard/AiRequestGuard.java` — 后端并发/冷却保护。
- Modify: `F:/dachuang/backend/src/main/java/com/citytrip/controller/ChatController.java` — chat/stream 接入 guard 和 validation。
- Modify: `F:/dachuang/backend/src/main/java/com/citytrip/controller/ItineraryController.java` — generate/smart-fill 接入 guard 和 validation。
- Modify: `F:/dachuang/backend/src/main/java/com/citytrip/model/dto/*.java` — 关键 DTO validation。
- Modify/Create tests under `F:/dachuang/backend/src/test/java` — guard、admin bootstrap、validation 回归。
- Create: `F:/dachuang/docs/api-contract/citytrip-api-contract.md` — 共享 API contract。
- Frontend refactor files under `F:/dachuang/frontend/src` — 按测试保护抽出纯函数/组件。

---

### Task 1: Restore test baseline

- [ ] Run current frontend/backend failing tests to confirm baseline.
- [ ] Change `frontend/package.json` so `npm test` runs `test:unit` then `test:node`.
- [ ] Restrict Vitest to `src/**/*.{test,spec}.js` and exclude `build/**`.
- [ ] Add missing mapper mocks to `AuthAndItineraryControllerTest`.
- [ ] Rewrite `DataSourceConfigFallbackTest` to call `DbCompatibilityEnvironmentPostProcessor` directly.
- [ ] Run `npm test` and targeted Maven tests.

### Task 2: Safe config and admin bootstrap

- [ ] Add tests for disabled-by-default admin bootstrap and rejection of `Admin123456` when explicitly enabled.
- [ ] Change `application.yml` bootstrap-admin defaults to disabled, blank password, no sync.
- [ ] Change `AdminAccountBootstrapRunner` to fail fast for enabled blank/unsafe password.
- [ ] Add `application.example.yml` with safe values.
- [ ] Run admin/config tests.

### Task 3: Flyway migration foundation

- [ ] Add Flyway dependencies.
- [ ] Add Flyway config with baseline-on-migrate and migration location.
- [ ] Add V1 baseline schema containing current core/new tables needed by runtime.
- [ ] Ensure startup diagnostics no longer owns new DDL as the preferred source; leave compatibility fallback where needed.
- [ ] Run package/tests.

### Task 4: AI guard and validation

- [ ] Add `AiRequestGuardProperties` and `AiRequestGuard` tests for concurrent saturation and per-key cooldown.
- [ ] Register configuration properties.
- [ ] Wrap chat, stream chat, smart-fill and generate endpoints in guard execution.
- [ ] Add validation dependency and constraints to key DTOs.
- [ ] Add controller tests for invalid payload returning 400 and saturated guard returning busy response.

### Task 5: Component size reduction

- [ ] Identify pure logic already covered by tests.
- [ ] Extract no-behavior-change helpers from `Result.vue` and `ItineraryMapCard.vue`.
- [ ] Keep existing visual DOM structure and CSS classes stable.
- [ ] Run frontend tests/build.

### Task 6: API contract

- [ ] Create `docs/api-contract/citytrip-api-contract.md` with core endpoint schemas and JSON examples.
- [ ] Cross-check frontend API modules against documented paths and field names.
- [ ] Run full verification.

## Final verification

- [ ] `cd F:/dachuang/backend && mvn test`
- [ ] `cd F:/dachuang/backend && mvn -q -DskipTests package`
- [ ] `cd F:/dachuang/frontend && npm test`
- [ ] `cd F:/dachuang/frontend && npm run build`
