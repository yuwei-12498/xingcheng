# 行程页逐段导航说明设计

**目标**

在路线生成结果页中，为每一个地点卡片增加“上一段怎么走”的真实导航说明，并在路线生成时一次性算完整条路线的逐段导航数据。用户进入结果页后：

1. 第一个地点看到“当前位置 -> 首站”的导航摘要；
2. 其余地点看到“上一站 -> 当前站”的导航摘要；
3. 点击“查看怎么走”后，看到导航级步骤说明；
4. 展开区域采用左右布局：左侧是详细步骤，右侧是该段可视化地图；
5. 只展示外部路由服务真实返回的结构化导航数据，不允许模型编造站名、出口名、线路名、换乘细节；
6. 如果某段拿不到完整结构化导航，仍显示摘要，并在展开区明确提示“该段暂未获取完整导航详情”。

---

## 一、范围

### 后端

- 路由服务与 GEO 解析
  - `F:\dachuang\backend\src\main\java\com\citytrip\service\geo\GeoSearchService.java`
  - `F:\dachuang\backend\src\main\java\com\citytrip\service\geo\impl\GeoSearchServiceImpl.java`
  - `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\GeoEnhancedTravelTimeServiceImpl.java`
- 路线分析与结果装配
  - `F:\dachuang\backend\src\main\java\com\citytrip\service\domain\planning\RouteAnalysisService.java`
  - `F:\dachuang\backend\src\main\java\com\citytrip\service\domain\ai\ItineraryAiDecorationService.java`
- 行程视图对象
  - `F:\dachuang\backend\src\main\java\com\citytrip\model\vo\ItineraryNodeVO.java`
  - 新增逐段导航 VO

### 前端

- 路线结果页
  - `F:\dachuang\frontend\src\views\Result.vue`
- 地图组件
  - `F:\dachuang\frontend\src\components\itinerary\ItineraryMapCard.vue`
- 新增逐段导航 UI 组件
  - `F:\dachuang\frontend\src\components\itinerary\SegmentRouteGuideCard.vue`
  - `F:\dachuang\frontend\src\components\itinerary\SegmentMiniMap.vue`
- 结果页工具与快照归一化
  - `F:\dachuang\frontend\src\utils\resultUi.js`
  - `F:\dachuang\frontend\src\store\itinerary.js`

### 不在本次范围内

- 社区页面改造；
- 独立全屏导航页；
- 实时 turn-by-turn 导航跟踪；
- 模型虚构导航细节补全；
- 接入新的第三方地图供应商。

---

## 二、当前问题

### 1. 右侧时间线缺少“上一段怎么走”的真实承载

当前右侧卡片只展示：

- 点位入选原因；
- 一段通行分析文案 `travelNarrative`；
- 温馨提示；
- 周边服务。

`travelNarrative` 更像“分析说明”，不是用户可执行的导航步骤，无法直接回答“具体怎么走、走到哪、坐几站、从哪里进出”。

### 2. 数据结构不够表达导航级步骤

当前 `ItineraryNodeVO` 仅能表达：

- `travelTransportMode`
- `travelTime`
- `travelDistanceKm`
- `travelNarrative`
- `routePathPoints`

它能支持“这一段怎么分析”，但无法稳定支持：

- 步行到哪个站；
- 哪条线路；
- 坐几站；
- 从哪个出口出；
- 哪一步没有拿到真实数据。

### 3. 左侧地图已具备 segment 高亮，但右侧没有一一对应的逐段导航卡

现在地图侧已经按 segment 组织高亮与联动，右侧时间线也已经支持 segment 联动状态，但缺少真正和 segment 对齐的数据面板，导致“地图能看路径，右侧却不能详细说明路径”。

---

## 三、用户已确认的产品决策

本设计基于以下已确认决策：

1. 逐段说明必须做到导航级表达；
2. 右侧采用“默认摘要 + 点击展开详情”；
3. 所有段在生成整条路线时一次性计算完成；
4. 右侧每个地点只展示“上一段 -> 当前点”；
5. 第一个点展示“当前位置 -> 首站”；
6. 采用方案 A：导航说明嵌入地点卡片内；
7. 展开区采用左右布局，另一侧是该段可视化地图；
8. 只允许使用真实返回的结构化导航数据；
9. 若无法拿到完整导航详情，允许降级，但必须明确提示。

这些决策在实现期不再回退为：

- 卡片之间插交通条；
- 点击后异步现算单段；
- 让模型虚构线路/出口/站点；
- 把详情弹到独立侧边栏。

---

## 四、交互设计

## 4.1 卡片内新增“怎么走”区域

在 `Result.vue` 的每个 `stop-card` 中新增一块“怎么走”区域，位置为：

1. 点位入选原因 `sysReason`
2. **怎么走**
3. AI 温馨提示 `statusNote`
4. 周边服务
5. 底部 meta 信息

这样用户会先理解“为什么去”，再看到“怎么去”，信息层级清晰。

## 4.2 折叠态

默认只展示一行导航摘要，例如：

- `步行 300 米 → 地铁 2 站 → 步行 450 米`
- `公交 4 站 → 步行 280 米`
- `打车约 14 分钟，约 5.1 公里`

并提供交互入口：

- `查看怎么走`

如果该段缺少完整步骤但有基础事实数据，折叠态仍显示摘要，并增加轻提示：

- `详情不完整`

## 4.3 展开态

展开后使用双栏结构：

### 左栏：详细步骤

步骤列表按真实 route steps 展示，典型结构：

1. 从成都博物馆正门步行约 300 米到天府广场地铁站 B 口；
2. 从 B 口进站，乘 1 号线往文殊院方向，坐 2 站；
3. 到文殊院站 C 口出站，步行约 450 米到文殊院入口。

每步显示的字段只允许来自真实返回值：

- instruction
- type
- distance
- duration
- lineName
- station / entrance / exit
- stopCount

### 右栏：该段迷你地图

只渲染当前 segment：

- 起点 marker；
- 终点 marker；
- 该段高亮路线；
- 自动 fit 当前段；
- 若有 step 级 path，可进一步分步高亮；
- 若无完整 step path，则至少展示 segment-level path。

## 4.4 展开规则

为了保持右侧阅读节奏稳定：

- 同一时间只允许展开一个地点卡片的“怎么走”；
- 展开新的卡片时，自动收起其他卡片；
- 点击左侧地图 segment 时，右侧对应地点卡片自动展开；
- 点击右侧卡片时，左侧总地图同步高亮对应 segment。

---

## 五、数据模型设计

## 5.1 新增逐段导航对象

不再把导航级数据继续塞进 `travelNarrative`。  
`travelNarrative` 保留为“这一段的说明性分析”，新增结构化字段承载真实导航细节。

### 新增 `SegmentRouteGuideVO`

建议新增文件：

- `F:\dachuang\backend\src\main\java\com\citytrip\model\vo\SegmentRouteGuideVO.java`

字段建议：

- `summary`: 折叠态摘要；
- `transportMode`: 该段推荐交通方式；
- `durationMinutes`: 总时长；
- `distanceKm`: 总距离；
- `detailAvailable`: 是否拿到了完整结构化步骤；
- `incompleteReason`: 不完整原因；
- `steps`: `List<SegmentRouteStepVO>`；
- `pathPoints`: 该段总路线 geometry；
- `source`: 数据来源，值限定为真实 route provider；

### 新增 `SegmentRouteStepVO`

建议新增文件：

- `F:\dachuang\backend\src\main\java\com\citytrip\model\vo\SegmentRouteStepVO.java`

字段建议：

- `stepOrder`
- `type`，例如：`walk / metro / bus / taxi / transfer / enter / exit`
- `instruction`
- `distanceMeters`
- `durationMinutes`
- `lineName`
- `fromStation`
- `toStation`
- `entranceName`
- `exitName`
- `stopCount`
- `pathPoints`

## 5.2 挂载位置

在 `ItineraryNodeVO` 上新增：

- `segmentRouteGuide`

含义统一为“到达本节点的上一段导航信息”：

- 第一个节点：`当前位置 -> 首站`
- 其余节点：`上一站 -> 当前节点`

补充约束：

- 若 itinerary 存在多日分段，则**每个 day 的首节点**都沿用当前 departure 语义，显示 `当前位置/出发位置 -> 当日首站`；
- 当前版本**不**把“前一日末站 -> 后一日首站”强行串成一段，避免在没有真实住宿点/次日出发点时编造跨日导航。

这样前端不需要引入新的 segment 平行数组，只需要读取 `displayNodes[*].segmentRouteGuide` 即可。

---

## 六、后端设计

## 6.1 路线生成时逐段计算

在 itinerary 生成阶段，对每段单独计算真实导航数据：

1. `当前位置 -> 首站`
2. `节点 1 -> 节点 2`
3. `节点 2 -> 节点 3`
4. ...

计算时机保持在“整条路线生成过程中”，而不是前端点开后懒加载。

## 6.2 GEO route 解析增强

当前 `GeoSearchServiceImpl` 已经能够解析：

- 时长；
- 距离；
- transport mode；
- pathPoints。

本次需要进一步增强 route response 解析，优先从真实返回中提取：

- steps / legs / segments；
- instruction；
- lineName；
- fromStation / toStation；
- entranceName / exitName；
- stopCount；
- step-level pathPoints。

解析策略：

1. 优先识别 route 响应中的结构化步骤数组；
2. 如果 provider 只返回总 path，没有 steps，则构造 `detailAvailable = false` 的 guide；
3. 如果 provider 连 path 都缺失，则仅保留总 mode/time/distance，并回退已有 segment path/fallback geometry。

为保证 step 级信息不会在 service 边界提前丢失，`GeoSearchService` 契约需要同步增强。实现时只能落地以下两种方式之一，并在代码中统一：

1. 扩展 `GeoRouteEstimate`，补充结构化 steps / station / entrance / exit / stopCount / step path 等字段；
2. 或新增专门的详细 route 方法（例如 `estimateTravelDetailed(...)`），返回可直接装配 guide 的 route DTO。

无论采用哪种方式，都要求：

- `GeoEnhancedTravelTimeServiceImpl` 继续复用同一份真实 route 数据，稳定产出 `minutes / distance / mode / path`；
- `SegmentRouteGuideService` 消费增强后的 detailed route contract，而不是重新猜测或拼接 narrative；
- 禁止把原始 `JsonNode` 解析散落到 `RouteAnalysisService` 或前端组件里。

## 6.3 新增 route guide 装配服务

建议新增一个专门的领域服务，例如：

- `F:\dachuang\backend\src\main\java\com\citytrip\service\domain\planning\SegmentRouteGuideService.java`

职责：

1. 接收起点与终点；
2. 调用增强后的 route contract（扩展版 `GeoRouteEstimate` 或详细 route DTO）；
3. 解析成 `SegmentRouteGuideVO`；
4. 生成折叠摘要；
5. 标记 `detailAvailable` 与 `incompleteReason`；
6. 保证结果只基于真实数据。

这样可以避免把导航组装逻辑堆进：

- `GeoEnhancedTravelTimeServiceImpl`
- `RouteAnalysisService`
- `ItineraryAiDecorationService`

## 6.4 RouteAnalysisService 挂载 guide

在 `RouteAnalysisService` 里构建节点时：

- 原有 `travelTime / travelTransportMode / travelDistanceKm / routePathPoints` 继续保留；
- 额外挂载 `segmentRouteGuide`；
- 第一个节点同时保持 `departure*` 字段，用于总览摘要与兼容旧逻辑。

## 6.5 AI 的角色收缩

本功能中，模型不负责生成可执行导航细节。  
模型只能做以下补充性工作：

- 在真实事实基础上生成一句解释性摘要；
- 若后端已有更可靠模板，则第一版可以完全不让 LLM参与导航详情。

明确禁止：

- 模型补造站名；
- 模型补造入口/出口；
- 模型补造几站换乘；
- 模型编造公交线路名。

## 6.6 降级策略

### 情况 A：有 mode/time/distance，也有总 path，但没有 step 明细

输出：

- `detailAvailable = false`
- 折叠摘要正常展示
- 展开区提示：
  - `该段暂未获取完整导航详情`
- 迷你地图仍展示该段 path

### 情况 B：只有 mode/time/distance，没有 path

输出：

- `detailAvailable = false`
- 展开区提示：
  - `该段暂未获取完整导航详情`
  - `当前仅展示概略路径`

### 情况 C：连基础 route facts 都失败

输出：

- 回退已有 travel fields；
- 使用现有 fallback geometry；
- 前端仍允许展示折叠区，但注明：
  - `该段导航数据获取失败`

---

## 七、前端设计

## 7.1 新增 `SegmentRouteGuideCard.vue`

职责：

- 渲染摘要；
- 控制展开/收起；
- 展开时左文右图；
- 处理不完整状态提示；
- 发出卡片展开联动事件。

组件输入建议：

- `guide`
- `fromName`
- `toName`
- `expanded`
- `segmentIndex`

组件输出建议：

- `toggle`
- `focus-segment`

## 7.2 新增 `SegmentMiniMap.vue`

职责：

- 只绘制当前 segment；
- 展示起终点；
- 展示该段 path；
- 在 path 缺失时展示概略路径；
- 不承担整天路线逻辑。

## 7.3 `Result.vue` 改造

每个 `stop-card` 中插入 `SegmentRouteGuideCard`。

页面级状态新增：

- `expandedRouteGuideIndex`

规则：

- 默认 `null`
- 点开一个后，其余自动关闭
- 与现有 `activeTimelineSegmentIndex / pinnedSegmentIndex` 保持联动

## 7.4 `ItineraryMapCard.vue` 联动

左侧总地图继续负责全局路线可视化。  
新增或强化联动规则：

- 打开某个 guide 时，高亮对应 segment；
- 点击左侧某个 segment 时，右侧对应 guide 自动展开；
- 切天数时，重置展开项。

## 7.5 快照归一化

`store/itinerary.js` 需要支持新的 `segmentRouteGuide` 字段归一化：

- 数字字段保留；
- step 数组保留；
- pathPoints 保留；
- 不对导航结构做伪造补齐。

---

## 八、摘要生成规则

折叠态摘要必须稳定、短、可扫读。

建议优先根据真实步骤生成：

### 示例规则

- 单纯步行：
  - `步行 650 米，约 9 分钟`
- 打车：
  - `打车约 14 分钟，约 5.1 公里`
- 步行 + 地铁 + 步行：
  - `步行 300 米 → 地铁 2 站 → 步行 450 米`
- 步行 + 公交 + 步行：
  - `步行 180 米 → 公交 4 站 → 步行 260 米`

若 steps 缺失，则退回：

- `${transportMode} 约 ${durationMinutes} 分钟，约 ${distanceKm} 公里`

---

## 九、错误处理与用户感知

用户感知目标不是“假装全都有”，而是“真实、稳定、可解释”。

### 页面级要求

- 不因为某一段缺 steps 就让整条路线失败；
- 不因为 provider 缺出口名就生成伪细节；
- 不在 UI 上静默吞掉失败；
- 每一段独立降级，不互相拖垮。

### 文案要求

不完整时统一使用明确文案：

- `该段暂未获取完整导航详情`
- `当前仅展示概略路径`
- `该段导航数据获取失败，请以地图实时导航为准`

---

## 十、测试策略

## 10.1 后端测试

新增或补强测试：

1. `GeoSearchServiceImpl`：
   - 能解析 steps / station / exit / line / stopCount；
   - 缺 steps 时能正确输出 `detailAvailable = false`；
   - 只有总 path 时能保留 geometry。

2. `SegmentRouteGuideService`：
   - 能生成 summary；
   - 能正确标注 incompleteReason；
   - 不会在字段缺失时编造导航细节。

3. `RouteAnalysisService`：
   - 每个 node 挂上对应 `segmentRouteGuide`；
   - 首站挂的是“当前位置 -> 首站”；
   - 多日 itinerary 中，每个 day 首节点挂的是“当前位置/出发位置 -> 当日首站”；
   - routePathPoints 与 guide path 一致或兼容。

4. `ItineraryAiDecorationService`：
   - 不会覆盖 `segmentRouteGuide` 的真实结构化字段；
   - 只允许对 narrative 类字段做补充。

## 10.2 前端测试

新增或补强测试：

1. 折叠态显示摘要；
2. 点击后展开详细步骤；
3. 不完整时显示兜底提示；
4. 同时只展开一个 guide；
5. mini map 仅展示当前 segment；
6. 与左侧地图联动；
7. 切换天数时重置展开状态；
8. itinerary snapshot 能保留 `segmentRouteGuide`。

## 10.3 运行态验证

最终需要验证：

1. 路线生成接口返回每个节点的 `segmentRouteGuide`；
2. 第一个点正确展示“当前位置 -> 首站”；
3. 展开区左文右图正常；
4. 没有完整 details 的段落会明确提示；
5. 左右地图联动正常；
6. 页面刷新后快照仍可恢复 guide 数据。

---

## 十一、实施顺序建议

### 第一阶段（必须完成）

1. 后端逐段真实 guide 数据结构；
2. route response 结构化步骤解析；
3. 节点挂载 `segmentRouteGuide`；
4. 右侧卡片折叠摘要；
5. 展开区左文右图；
6. 不完整降级文案；
7. 基础联动与测试。

### 第二阶段（可后续增强）

1. step 级 geometry 分步高亮；
2. 更细的图标与交通视觉系统；
3. 展开动画；
4. 更强的交互引导与地图镜头动画。

---

## 十二、结论

本次设计采用：

- **卡片内摘要 + 展开详情**
- **生成时一次性计算全部段**
- **展开区左文右图**
- **真实数据优先，缺失明确降级，不允许模型编造**

最终结果应让结果页从“有路线分析”升级为“每段都能告诉用户上一段应该怎么走”，同时保持页面结构稳定，不破坏你当前已经认可的左右布局与地图联动方式。
