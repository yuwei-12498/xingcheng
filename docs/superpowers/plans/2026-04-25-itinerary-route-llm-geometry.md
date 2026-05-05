# 行程真实路线与模型交通分析 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让结果页地图展示真实路线 geometry，并让首站排序、温馨提示、段间交通方式真正接入模型与 route facts。

**Architecture:** 后端把 route facts（geometry / minutes / distance / mode）挂到每个节点的入边数据上，再由 AI 装饰层补充 warm tip candidates 与 segment transport analysis；前端地图消费 `departurePoint + routePathPoints` 绘制真实路线，store 禁止覆盖模型提示候选。

**Tech Stack:** Spring Boot 3、Jackson、Vue 3、Leaflet、Vitest、Maven

---

### Task 1: 后端测试先行，锁定 geometry/首站排序/模型候选输出

**Files:**
- Modify: `F:\dachuang\backend\src\test\java\com\citytrip\service\geo\impl\GeoSearchServiceImplTest.java`
- Modify: `F:\dachuang\backend\src\test\java\com\citytrip\service\impl\ItineraryRouteOptimizerDpTest.java`
- Modify: `F:\dachuang\backend\src\test\java\com\citytrip\service\domain\planning\RouteAnalysisServiceTest.java`
- Modify: `F:\dachuang\backend\src\test\java\com\citytrip\service\domain\ai\ItineraryAiDecorationServiceTest.java`

- [ ] 为 `GeoRouteEstimate` 增加 pathPoints 解析失败测试
- [ ] 为首站排序增加“近点优先于远点”的失败测试
- [ ] 为 `RouteAnalysisService` 增加 routePathPoints 挂载失败测试
- [ ] 为 `ItineraryAiDecorationService` 增加 warmTipCandidates / selectedWarmTip / travelNarrative 失败测试
- [ ] 运行对应 Maven 单测，确认先红

### Task 2: 后端实现真实路段数据与首站排序增强

**Files:**
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\TravelTimeService.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\geo\GeoRouteEstimate.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\geo\impl\GeoSearchServiceImpl.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\GeoEnhancedTravelTimeServiceImpl.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\LocalTravelTimeServiceImpl.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\ItineraryRouteOptimizer.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\domain\planning\RouteAnalysisService.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\model\vo\ItineraryNodeVO.java`

- [ ] 扩展 route estimate / travel leg，携带 geometry path points
- [ ] 解析 route API 返回的 polyline / steps / coordinates
- [ ] 把 geometry 写入 node 入边数据 `routePathPoints`
- [ ] 在 optimizer 中加入首站 distance/time/mode 强惩罚
- [ ] 重新运行后端目标测试，确认转绿

### Task 3: 后端实现模型提示候选与段间交通分析

**Files:**
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\LlmService.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\RealLlmGatewayService.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\OpenAiLlmServiceImpl.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\RoutingLlmServiceImpl.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\MockLlmServiceImpl.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\SafePromptBuilder.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\domain\ai\ItineraryAiDecorationService.java`

- [ ] 新增 segment transport analysis 的 prompt / gateway / mock fallback
- [ ] 保留 3-5 条 warmTipCandidates，并单独产出 selectedWarmTip
- [ ] 为每段入边生成 transport narrative
- [ ] 重新运行 AI 装饰相关测试，确认转绿

### Task 4: 前端测试先行并改造地图/提示消费

**Files:**
- Modify: `F:\dachuang\frontend\src\components\itinerary\__tests__\ItineraryMapCard.test.js`
- Modify: `F:\dachuang\frontend\src\store\__tests__\itinerary.test.js`
- Modify: `F:\dachuang\frontend\src\components\itinerary\ItineraryMapCard.vue`
- Modify: `F:\dachuang\frontend\src\store\itinerary.js`
- Modify: `F:\dachuang\frontend\src\views\Result.vue`

- [ ] 先写测试，锁定地图使用 `departurePoint` + `routePathPoints`
- [ ] 先写测试，锁定 store 不再覆盖模型 warmTipCandidates
- [ ] 地图增加当前位置 marker、真实分段路线绘制、首末站/外部 POI 更强视觉区分
- [ ] 结果页把 departurePoint 传给地图，并兼容 segment 0 = 出发段联动
- [ ] 运行前端目标测试，确认转绿

### Task 5: 整体验证

**Files:**
- Verify only

- [ ] 运行后端相关 Maven 单测
- [ ] 运行前端 `vitest` 相关用例
- [ ] 运行 `npm run build`
- [ ] 汇总未解决风险与后续建议
