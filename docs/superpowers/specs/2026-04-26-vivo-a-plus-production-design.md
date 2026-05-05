# vivo A++ 准生产能力设计

**日期**：2026-04-26  
**状态**：已完成设计评审（用户口头确认）  
**适用范围**：`F:\dachuang` 现有项目  
**设计目标**：在不回退结果页与社区页现有视觉的前提下，把 vivo 的真实在线能力接入为项目内可灰度、可降级、可观测、可回滚、重启后稳定的准生产能力底座，并优先强化聊天主链路。

---

## 1. 设计目标与边界

### 1.1 本次 A++ 的核心目标

本轮不是“再接几个接口”，而是把当前项目中已经部分可用的 AI 能力升级为一套**准生产可上线**的能力架构，重点满足以下要求：

1. **真实使用 vivo 大模型能力**
   - 聊天主链路默认走 vivo 在线能力。
   - AI 文案、温馨提示、推荐说明默认走 vivo 在线生成。
   - 社区搜索默认优先接入 vivo 语义能力。
2. **Fail Open，但不是假成功**
   - 在线能力失败后，页面继续可用。
   - 降级必须可解释，不能伪装成“在线成功”。
3. **准确优先**
   - 对依赖外部实时检索的问题，优先通过 tool / retrieval 获取真实结果，再让模型回答。
   - 允许受控等待，但不允许无限等待。
4. **灰度双轨**
   - 保留当前稳定降级链路。
   - 真实 vivo 能力按能力级 feature flag 逐步切换。
5. **重启与重新部署后行为稳定**
   - 不依赖首次请求临时初始化。
   - 不依赖缓存偶然命中。
   - 同一配置下，多次重启结果可重复。

### 1.2 用户已确认的设计基线

- **A**：选择“vivo 真实在线能力 + 生产级硬化”方向。
- **A1**：按准生产可上线标准设计。
- **P1**：默认 Fail Open。
- **S1**：聊天链路 P0。
- **L3**：聊天链路准确优先。
- **R1**：采用灰度 / 双轨模式。

### 1.3 本轮要解决的主要问题

1. 真实在线能力接入不彻底，chat / embedding / rerank 仍未形成统一主链路。
2. 聊天 tool loop 不够生产化，缺少触发规则、轮次控制、超时预算与回灌纪律。
3. readiness 仍偏布尔值，不能解释为什么不可用、为什么被降级、当前到底走了哪条路径。
4. Fail Open 缺少制度化设计，降级行为不够明确。
5. 缺少能力级灰度开关，导致上线与回滚粒度过粗。
6. 冷启动、重启、重新部署后的行为仍带有“靠运气”的不确定性。

### 1.4 明确不做

本轮设计明确不包含以下内容：

- 不重做结果页和社区页视觉。
- 不把 vivo `/search/geo` 包装为真实导航或 turn-by-turn 路线规划。
- 不重写现有路线生成主 planner 算法。
- 不为了“统一架构”进行无关大重构。
- 不把 OCR、翻译、TTS、ASR、图像/视频生成纳入本轮主交付。
- 不要求先建设完整离线向量平台才允许语义能力上线。

---

## 2. A++ 核心架构

### 2.1 总体原则

本轮采用**双轨增强架构**：

- **Stable Fallback Lane**：保留当前已可运行的稳定降级链路，作为保底路径。
- **Vivo Online Lane**：引入 vivo 真实在线能力，通过 feature flag 灰度接流量。

核心原则：

> 业务层只依赖项目内部能力接口，不直接依赖 vivo provider 细节。

### 2.2 五层架构

#### 第 1 层：Entry / API 层
负责：
- 接收前端请求
- 参数校验
- 输出统一响应结构
- 屏蔽 provider 差异

#### 第 2 层：Orchestrator 编排层
负责：
- 判断场景
- 决定是否触发工具
- 决定是否允许等待在线结果
- 决定如何降级
- 组织上下文后再交给模型或 fallback

建议的核心编排器：
- `ChatOrchestrator`
- `PoiRetrievalOrchestrator`
- `CommunitySemanticOrchestrator`

#### 第 3 层：Capability 能力层
对上提供稳定能力接口，对下适配真实 provider：
- `ChatCapability`
- `ToolLoopCapability`
- `GeoSearchCapability`
- `EmbeddingCapability`
- `RerankCapability`

#### 第 4 层：Provider Client 层
只负责 vivo 文档协议细节：
- `VivoChatClient`
- `VivoGeoClient`
- `VivoEmbeddingClient`
- `VivoRerankClient`

职责限定为：
- URL / path
- 鉴权
- `requestId` 注入
- 请求/响应映射
- provider 错误标准化
- timeout / retry / 熔断接入点

#### 第 5 层：Fallback / Policy 层
负责：
- feature flag
- 超时预算
- 降级顺序
- 熔断
- readiness 输出
- active path / fallback path 标记

### 2.3 灰度双轨设计

#### Stable Fallback Lane
保底链路示例：
- chat：现有 mock / 简化回答链路
- poi：本地候选或安全空结果
- community：现有 `keyword/theme/sort` 逻辑
- route-ai：保守兜底文案

#### Vivo Online Lane
增强链路示例：
- chat：真实 vivo chat
- tool：真实 tool loop
- poi：真实 vivo `/search/geo`
- community：真实 vivo embedding + rerank

### 2.4 能力级灰度开关

至少提供：

- `chatOnlineEnabled`
- `toolLoopEnabled`
- `poiLiveEnabled`
- `semanticOnlineEnabled`
- `embeddingOnlineEnabled`
- `rerankOnlineEnabled`
- `preferAccuracyMode`
- `failOpenEnabled`

### 2.5 统一状态机

每条能力链路统一输出：

- `disabled`
- `bypassed`
- `ready`
- `degraded`
- `failing`
- `blocked`

并附带：
- `activePath`
- `fallbackPath`
- `reason`
- `providerRequestId`
- `lastErrorCode`
- `lastErrorAt`

### 2.6 准确优先与 Fail Open 的平衡

- 对需要真实外部信息的问题，优先走在线能力。
- 超时或失败后自动降级。
- `Fail Open` 只保证继续服务，不代表隐藏失败事实。

---

## 3. 聊天链路 P0 设计

### 3.1 核心目标

聊天链路必须同时满足：

1. 聊天回答真正使用 vivo 大模型。
2. 依赖真实外部信息的问题优先检索后回答。
3. AI 温馨提示、路线摘要类文案默认使用 vivo 在线生成。
4. 在线能力失败时页面仍可用，不假死、不 500。

### 3.2 四种执行模式

#### 模式 A：Direct Answer
适用：
- 闲聊
- 普通建议
- 文案润色
- 不依赖外部实时信息的问题

策略：
- 直接调用 vivo chat
- 不进 tool loop

#### 模式 B：Prefetch + Answer
适用：
- 意图明显、但需要少量检索的问题

策略：
1. 编排层先判意图
2. 先检索 POI / community
3. 将真实结果写入上下文
4. 再由 vivo chat 总结输出

#### 模式 C：Tool Loop Answer
适用：
- 复合问题
- 需要多步工具决策的问题

策略：
1. 先调用支持工具决策的 vivo 模型
2. 模型选择工具
3. 工具执行
4. 结果回灌模型
5. 模型生成最终答案

#### 模式 D：Degraded Answer
适用：
- vivo 不可用
- tool 超时
- GEO/semantic 出错
- provider 鉴权或额度异常

策略：
- 回退到 stable lane
- 返回可用但更保守的答案

### 3.3 工具触发原则

#### 直接走 vivo chat 即可
- 情绪陪伴 / 闲聊
- 普通旅行建议
- 文案润色
- 对页面已有文案的解释

#### 优先走 vivo + tool / retrieval
- 指定城市 / 主题的 POI 搜索
- 社区帖子推荐、对比、总结
- 基于当前路线页上下文的问答
- 需要结合 POI 与社区信息的复合问题
- “附近 / 适合 / 推荐 / 对比 / 哪个更好” 等易产生幻觉的问题

原则：

> 只要正确性依赖外部检索结果，就不能只让模型裸答。

### 3.4 P0 工具集合

首批只接入三类工具：

1. **`search_poi`**
   - 调 vivo `/search/geo`
   - 只做 POI 搜索 / 地理检索
   - 不包装成导航能力

2. **`search_community_posts`**
   - 调社区语义检索链路
   - 返回高相关帖子、摘要、主题与热度信息

3. **`get_route_context`**
   - 读取当前路线页上下文
   - 包括用户当前位置、当前顺序、已选点位、主题偏好、已生成摘要
   - 只读取项目内部上下文，不代表真实导航能力

### 3.5 模型角色分工

#### 聊天回答模型
负责：
- 普通问答
- 检索结果总结
- 社区推荐解释
- 路线页上下文解读

#### 工具决策模型
负责：
- 是否触发工具
- 调用哪些工具
- 如何组织多工具回答

#### AI 文案生成模型
负责：
- 温馨提示
- 个性化短建议
- 页面 AI 辅助文案
- 路线摘要润色

要求：
- 默认在线调用 vivo
- 每次生成可做短时 session cache
- 不再依赖静态模板批量拼装

### 3.6 L3 准确优先的时间预算

#### 非流式预算
- 意图判断：`<= 150ms`
- 单次工具执行目标：`<= 1200ms`
- 单次工具执行上限：`<= 2200ms`
- 最终模型生成目标：`<= 2200ms`
- 总软预算：`<= 4500ms`
- 总硬上限：`<= 6500ms`

#### 流式预算
- 首个状态事件：`<= 800ms`
- 若进入工具链，先返回“正在检索相关信息”
- 最终有效内容目标：`<= 5500ms`

### 3.7 Tool Loop 控制纪律

- 最多 **2 轮模型往返**
- 最多 **3 次工具调用**
- 同一工具最多连续调用 **1 次**
- 参数修复最多 **1 次**
- 禁止递归工具链

### 3.8 真值优先级

1. 工具真实结果
2. 当前页面与项目上下文
3. 模型通用知识
4. 模型自由发挥

禁止：
- 编造精确 POI
- 编造营业时间
- 把 `/search/geo` 说成导航能力
- 把社区检索假装成已阅读大量帖子

### 3.9 失败分类与降级终态

#### 可自动重试
- connect timeout
- read timeout
- 502 / 503
- provider 短暂抖动

策略：最多 1 次快速重试。

#### 直接失败不重试
- 鉴权错误
- 模型名错误
- tool schema 错误
- 参数错误
- provider 明确 `bad request`

#### 最终降级终态
- chat online 失败 → stable answer
- tool 失败 → 无工具回答
- poi 失败 → 通用建议 / 安全空结果
- community semantic 失败 → `keyword/theme/sort`
- 文案生成失败 → 兜底短文案模板

---

## 4. vivo 语义能力在线接入设计

### 4.1 能力定位

- **chat**：负责回答、总结、生成文案
- **function calling**：负责决定何时使用真实检索工具
- **POI / GEO**：负责真实地点检索
- **embedding**：负责语义找候选
- **rerank**：负责候选精排

原则：

> embedding 负责找，rerank 负责排，chat 负责说。

### 4.2 社区搜索标准链路

```text
用户 keyword
-> 基础过滤(theme / city / status / pinned 规则)
-> 本地候选集
-> vivo embedding 语义粗召回
-> vivo rerank 精排
-> 业务规则回填
-> 最终列表
```

#### 第一步：保留现有业务过滤
包括：
- `theme`
- 可见性规则
- 置顶 / 推荐位规则
- 基础排序边界

#### 第二步：构造候选池
候选池由两类结果组成：
- lexical 候选
- semantic 候选

合并、去重后形成稳定候选池，避免明显命中的帖子被语义波动覆盖。

#### 第三步：使用 vivo rerank 精排
rerank 只负责候选内部排序，不负责决定“是否有结果”。

### 4.3 语义能力复用场景

#### 场景 1：社区页搜索
- `keyword` 非空时默认进入 semantic pipeline

#### 场景 2：聊天工具 `search_community_posts`
- 在 tool loop 中复用同一套 semantic pipeline
- 检索结果作为证据喂给 vivo chat 总结

#### 场景 3：路线页 / 结果页 AI 说明
- 将高相关社区帖子摘要作为辅助证据
- 再由 vivo chat 生成更可信的推荐说明

### 4.4 在线语义链路约束

- 只有 `keyword` 非空时进入 semantic pipeline
- 候选池规模受控，建议粗候选上限 `20~30`
- rerank 只排有限 `topN`
- query embedding 可做短 TTL cache
- 不以前置建设全量离线向量平台作为上线门槛

### 4.5 降级规则

- embedding 失败 → lexical recall + 现有排序
- rerank 失败 → 保留候选集，用现有业务排序输出
- embedding + rerank 都失败 → 完全退回当前社区搜索逻辑

原则：

> 语义能力失效时，只降低智能度，不能降低可用性。

### 4.6 readiness 与观测字段

至少输出：
- `semanticOnlineEnabled`
- `embeddingReady`
- `rerankReady`
- `semanticActivePath`
- `semanticFallbackPath`
- `lastSemanticError`
- `providerRequestId`

---

## 5. readiness / 观测 / 启动稳定性设计

### 5.1 启动契约

系统是否可用不能依赖：
- 首次请求触发初始化
- 人工试出来
- 某次缓存恰好命中

启动后系统必须明确回答：
- chat 是否可用
- tool 是否可用
- GEO 是否可用
- embedding 是否可用
- rerank 是否可用
- 当前到底走 online 还是 fallback
- 不能用的原因是什么

### 5.2 冷启动三段式

#### 阶段 A：静态配置校验
启动时先做本地校验：
- API Key
- base URL
- scene 配置完整性
- tool model 配置
- vivo 模型白名单
- GEO/embedding/rerank 开关匹配
- tool schema 预构建

#### 阶段 B：在线能力探测
启动后异步轻量 probe：
- chat probe
- tool capability probe
- GEO probe
- embedding probe
- rerank probe

要求：
- 不阻塞服务整体启动
- probe 结果写入 readiness
- 探测失败自动转为 degraded / blocked

#### 阶段 C：启动快照固化
每次启动保存脱敏启动快照：
- `bootId`
- `startedAt`
- `activeProfile`
- `featureFlags`
- `sanitizedConfigFingerprint`
- 各能力 readiness
- warnings
- 首次 probe 结果

### 5.3 readiness 结构

每项能力统一输出：
- `enabled`
- `configured`
- `reachable`
- `healthy`
- `state`
- `activePath`
- `fallbackPath`
- `reason`
- `lastErrorCode`
- `lastErrorAt`
- `providerRequestId`
- `lastProbeAt`

### 5.4 运行时稳定机制

#### 超时预算固定
chat / tool / GEO / embedding / rerank 各自拥有独立 timeout budget。

#### 有限重试
仅对短暂错误重试一次：
- timeout
- 502 / 503
- 网络抖动

以下不重试：
- 鉴权错误
- 模型错误
- schema 错误
- 参数错误

#### 熔断与冷却窗口
- 连续失败达到阈值（建议 3 次）进入 `failing/degraded`
- 冷却窗口内直接走 fallback
- 冷却后只允许少量探测请求尝试恢复

#### 粘性降级
在一个降级窗口内保持一致降级行为，避免用户体验忽左忽右。

### 5.5 禁止首次请求临时初始化

以下能力必须在启动期预构建或预校验，不允许延迟到首次用户请求：
- tool registry
- 模型配置校验
- rerank 配置发现
- prompt / schema 基础装配

### 5.6 可观测性要求

#### 单请求记录
- 内部 `traceId`
- vivo `requestId`
- capability 名称
- active path / fallback path
- latency
- tool 调用次数
- 是否降级
- 错误码
- 错误分类

#### 聚合指标
- chat online 成功率
- tool loop 触发率
- tool loop 降级率
- GEO 可用率
- embedding 成功率
- rerank 成功率
- semantic fallback rate
- p95 / p99 延迟

#### 启动摘要日志
每次启动必须打印一段精简摘要：
- 哪些能力 ready
- 哪些 degraded
- 哪些 blocked
- 当前 feature flag
- warnings

### 5.7 重启后确定性行为

重启后行为必须只由以下两者决定：
- feature flag
- readiness 状态

而不能由以下偶然因素决定：
- 请求顺序
- 某次缓存
- 人工先点击哪个页面

---

## 6. 验收标准 / 灰度上线 / 回滚方案

### 6.1 P0 必过验收项

1. **聊天主链路真实走 vivo**
   - 普通问答可用
   - 可追踪 provider requestId
   - 失败时自动降级

2. **tool loop 真正可用**
   - 稳定调用 `search_poi` / `search_community_posts` / `get_route_context`
   - 最多 2 轮、最多 3 次调用
   - 超时后正常降级

3. **POI 搜索真实走 vivo GEO**
   - 返回真实 POI
   - 语义明确为 POI 搜索
   - 不再伪装成导航能力

4. **社区搜索接入 embedding + rerank**
   - `keyword` 非空时进入 semantic pipeline
   - embedding 失败可退 lexical
   - rerank 失败不影响结果可用性

5. **readiness 可解释**
   - 至少覆盖 chat / tool / geo / embedding / rerank
   - 能看到 `state / activePath / fallbackPath / reason`

6. **重启后行为可重复**
   - 冷启动后无需人工预热
   - 首次请求不依赖临时初始化
   - 同一配置下多次重启 readiness 结果一致

### 6.2 P1 增强项

- 启动后异步 probe 快照
- 熔断与冷却窗口
- 粘性降级
- 能力级成功率 / 延迟指标
- 文案生成短 TTL cache
- shadow 模式对比日志

### 6.3 测试验收矩阵

#### 单元测试
验证：
- 配置解析
- allow-list
- tool registry
- tool loop 轮次控制
- fallback 分支
- readiness 计算

#### 集成测试
验证：
- chat 请求必须带 `requestId`
- vivo `200 + error body` 能正确判失败
- GEO 请求格式符合文档
- embedding / rerank 失败时按预期降级

#### 启动验证
验证：
- 启动日志输出能力摘要
- readiness 完整
- 首次请求与第二次请求行为一致
- 不存在“第一次不行、第二次才好”的现象

#### 人工回归
至少覆盖：
- 首页聊天普通问答
- 聊天触发 POI 搜索
- 聊天触发社区搜索
- 社区 keyword 搜索
- 路线页 AI 文案输出
- 能力 flag 关闭后的降级表现
- 重启项目后的首次体验

### 6.4 灰度上线顺序

#### 第 1 步：Shadow
- 用户链路仍走稳定轨
- 后台并行调用 vivo 能力
- 只记录结果，不影响用户

#### 第 2 步：能力级单开
建议顺序：
1. `poiLiveEnabled`
2. `embeddingOnlineEnabled`
3. `rerankOnlineEnabled`
4. `chatOnlineEnabled`
5. `toolLoopEnabled`

#### 第 3 步：流量扩张
如支持按流量灰度，则建议：
- 10%
- 30%
- 60%
- 100%

若暂不支持流量分级，则按环境推进：
- 本地验证
- 测试环境全量
- 线上全量但保留独立开关

#### 第 4 步：Fallback Drill
必须演练：
- 关闭 GEO
- 关闭 rerank
- 模拟 chat timeout
- 模拟 tool schema 错误

确认：
- 页面仍可用
- 状态接口解释正确
- 日志能定位问题

### 6.5 回滚方案

#### 一级回滚：关闭单项能力
- 关 tool loop
- 关 GEO live
- 关 embedding
- 关 rerank

#### 二级回滚：切回 stable lane
- 保留 vivo 配置
- 但所有请求强制走 fallback

#### 三级回滚：停用 vivo provider
- 完全停用在线能力
- 恢复到当前稳定方案

### 6.6 “下次开机不靠运气”的验收口径

只有同时满足以下五条，才算真正解决：

1. 重启后无需人工点一次“激活”
2. 首次请求与后续请求行为一致
3. 能力状态在启动后 30 秒内稳定可见
4. 任一能力失败时自动进入明确降级
5. 相同配置下，多次重启结果可重复

若上述五条全部达成，则本次设计的稳定性结论为：

> **解决强度：高（工程级，不是临时补丁）**

若再补齐：
- 熔断
- probe 快照
- fallback drill
- 指标监控

则可提升为：

> **解决强度：很高（准生产级，可持续）**

---

## 7. 实施约束与落地顺序建议

### 7.1 落地优先级

建议实施顺序：

1. 配置模型与 readiness 契约
2. vivo chat / tool gateway 硬化
3. tool loop 与聊天主链路
4. vivo GEO / POI live 搜索
5. embedding + rerank 语义链路
6. 启动探测 / 观测 / 灰度 / 回滚硬化

### 7.2 对现有页面的约束

- 不允许回退结果页与社区页现有视觉。
- 能力升级优先通过后端链路、状态契约、数据结构扩展完成。
- 前端改动遵循“最小接线”原则，不为了接入能力而重做视觉层。

### 7.3 实施成功的标志

最终不是“接口能调通”，而是：
- vivo 能力真正进入主链路
- 社区搜索真正有语义能力
- 聊天回答、温馨提示、推荐说明真正使用 vivo 大模型
- 失败会降级，不拖死页面
- 重启 / 部署 / 开机后行为稳定，不再靠运气

