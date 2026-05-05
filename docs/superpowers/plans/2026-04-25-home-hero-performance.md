# 首页首屏性能优化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不牺牲桌面端首页电影感的前提下，降低首页首屏脚本阻塞、Lottie 资源开销与弱设备滚动负担。

**Architecture:** 将首页首屏拆分为 poster-first + deferred-enhancement 双阶段策略。通过 `heroPerformance` 统一判断是否允许完整动画与平滑滚动；Hero 区动态加载 Lottie，Core 区异步加载 AI 面板。

**Tech Stack:** Vue 3, Vite, Vitest, lottie-web, Lenis

---

### Task 1: 新增首屏运行时能力判断工具

**Files:**
- Create: `F:\dachuang\frontend\src\utils\heroPerformance.js`
- Test: `F:\dachuang\frontend\src\utils\__tests__\heroPerformance.test.js`

- [ ] 写入设备/网络能力判断与 idle 调度工具
- [ ] 为高配桌面、Save-Data、弱网、小屏、低内存场景补单测
- [ ] 运行对应单测验证判断逻辑

### Task 2: 改造 Hero 为 poster-first + deferred Lottie

**Files:**
- Modify: `F:\dachuang\frontend\src\components\home\HomeImmersiveStory.vue`

- [ ] 移除静态 Lottie 引入，改为动态 import
- [ ] 增加 poster 图层与动画 ready 淡入逻辑
- [ ] 增加 idle / intersection 触发的延后加载
- [ ] 增加失败兜底与模块 / JSON 缓存

### Task 3: 只在高配环境启用 Lenis

**Files:**
- Modify: `F:\dachuang\frontend\src\views\Home.vue`

- [ ] 读取运行时能力画像
- [ ] 仅在允许条件下初始化 Lenis
- [ ] 保持原有滚动到锚点逻辑兼容

### Task 4: 延后加载首页 AI 面板

**Files:**
- Modify: `F:\dachuang\frontend\src\components\home\HomeCoreSection.vue`

- [ ] 将 `HomeAiPanel` 改为异步组件
- [ ] 在核心区接近可视区时再挂载面板
- [ ] 保留桌面布局占位，避免 UI 塌陷

### Task 5: 完整验证

**Files:**
- Verify only

- [ ] 运行 `npm test`
- [ ] 运行 `npm run build`
- [ ] 检查是否生成新的异步 chunk
- [ ] 记录优化前后 `Home-*.js` 体积变化
