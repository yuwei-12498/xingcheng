# 行程真实路线与模型交通分析设计

**目标**

解决结果页路线生成的四个核心问题：
1. 地图不再画 POI 直线，而是绘制“当前位置 -> 首站 -> 后续站点”的真实路线 geometry；
2. 首站选择把当前位置到候选点的真实距离、耗时与交通方式纳入强约束；
3. 每个地点返回 3-5 条真实模型提示候选，前端从候选池展示，不再用固定模板覆盖；
4. 每段交通方式由真实 route facts + LLM 分析共同生成，输出推荐方式与解释。

**范围**

- 后端 GEO 路由解析：`F:\dachuang\backend\src\main\java\com\citytrip\service\geo\*`
- 路线优化：`F:\dachuang\backend\src\main\java\com\citytrip\service\impl\ItineraryRouteOptimizer.java`
- 路线分析与 AI 装饰：
  - `F:\dachuang\backend\src\main\java\com\citytrip\service\domain\planning\RouteAnalysisService.java`
  - `F:\dachuang\backend\src\main\java\com\citytrip\service\domain\ai\ItineraryAiDecorationService.java`
- 行程 VO：`F:\dachuang\backend\src\main\java\com\citytrip\model\vo\*.java`
- 前端地图与快照归一化：
  - `F:\dachuang\frontend\src\components\itinerary\ItineraryMapCard.vue`
  - `F:\dachuang\frontend\src\store\itinerary.js`
  - `F:\dachuang\frontend\src\views\Result.vue`

**设计决策**

## 1. 路线 geometry 进入节点入边数据

不新增单独 segment API 数组，而是在每个 `ItineraryNodeVO` 上增加“到达本节点的路段数据”：
- `routePathPoints`: 当前入边的路径点集合；
- `travelNarrative`: 当前入边的模型交通分析说明；
- `warmTipCandidates`: 当前点位的提示候选池；
- `selectedWarmTip`: 当前展示提示。

首站的 `routePathPoints` 表示“当前位置 -> 首站”的路线；其余节点表示“上一站 -> 当前站”。
前端地图通过 `departurePoint + displayNodes[*].routePathPoints` 绘制完整路径。

## 2. 首站排序改成显式出发可达性驱动

路线优化阶段保留原有 start travel minutes，但新增首站启动成本：
- 首段真实耗时惩罚；
- 首段真实距离惩罚；
- 交通方式适配惩罚（结合 walking level）；
- 开门时间等待惩罚保持原逻辑。

这样首站不会只因为 POI 分高就压过“明显更近、更顺路”的候选点。

## 3. 段间交通模式由事实层 + LLM 分析层组成

事实层：GEO route 返回时长、距离、模式、geometry。
分析层：LLM 基于起终点、时间、距离、已有模式与用户请求输出 JSON：
- `transportMode`
- `narrative`

如果模型返回空值或不合法，则回退到事实层模式与规则文案。

## 4. 前端禁止再用固定模板覆盖模型提示

前端归一化只做本地化/去脏词，不再把 generic statusNote 直接替换成固定模板。
若后端返回 `warmTipCandidates`，优先从候选池取 `selectedWarmTip`；
只有后端完全缺失时才允许兜底到单条 statusNote。

## 5. 地图展示升级

地图需要新增：
- 用户当前位置 marker（仅首日展示）；
- 基于 geometry 的分段 polyline；
- 分段 hover/pin 仍然保留；
- 首站 / 末站 / 外部 POI / 当前位置视觉区分更强。

**验证策略**

- 后端单测覆盖：route geometry 解析、首站排序、warmTipCandidates 保留、segment transport LLM 输出落盘；
- 前端单测覆盖：地图改用 `routePathPoints` 和 `departurePoint`，store 不再覆盖模型提示候选；
- 最终执行相关后端测试、前端 vitest 与 `npm run build`。
