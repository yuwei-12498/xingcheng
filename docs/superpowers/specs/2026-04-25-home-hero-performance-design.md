# 首页首屏性能优化设计

**日期：** 2026-04-25  
**范围：** `F:\dachuang\frontend` 首页首屏与首屏后首个核心交互区  
**目标：** 保留桌面端电影感首屏体验，同时降低首页首屏阻塞脚本、弱设备卡顿与不必要的 Lottie 资源拉取

---

## 1. 背景与问题

当前首页首屏使用 `lottie-web` 驱动开门动画，资源目录 `F:\dachuang\frontend\public\animations\travelnextlvl-door` 总体积约 10 MB，其中图片序列约 9.93 MB、`data.json` 约 79 KB。现状存在以下问题：

- 首屏动画在首页进入时就参与初始化，增加主线程与网络压力。
- 移动端、弱网、低性能设备并不适合强制加载完整动画。
- 首页 `Home` 路由块承载首屏、表单区和多个营销段落，存在进一步切分空间。
- 平滑滚动 `Lenis` 在所有设备上都会初始化，对低性能设备并不划算。

本轮优化必须满足：

- **不破坏现有首页视觉语言**
- **桌面端继续保留 Lottie 电影感**
- **移动端 / 弱网 / 低性能设备允许降级为高质静态封面**
- **不影响结果页、社区页、地图增强结果**

---

## 2. 设计原则

1. **首屏优先可见，再加载增强动效**：先让用户看到高质量封面和文案层，再决定是否装载 Lottie。
2. **按设备能力分流**：高配桌面走完整动画，低配环境走封面模式。
3. **只延后非关键逻辑**：首屏必须的文案、滚动揭示保留；动画、平滑滚动和右侧 AI 面板属于可延后增强。
4. **小步可验证**：每个优化点都要能通过构建产物和功能回归验证。

---

## 3. 运行时策略

### 3.1 Hero 动画策略

首页 Hero 将分成两种运行模式：

- **Full animation mode**
  - 用于桌面端、正常网络、非节流环境
  - 首屏先显示 poster
  - 通过 `requestIdleCallback` / `setTimeout` 与可见区判断延后加载 Lottie 模块和动画数据
  - 动画真正可用后再淡入 canvas 层

- **Poster mode**
  - 用于移动端、小屏、弱网、Save-Data、低内存、低核心数、`prefers-reduced-motion`
  - 只显示高质封面与文案/滚动揭示
  - 不主动加载完整 Lottie 资源

### 3.2 平滑滚动策略

- **高配环境**：保留 `Lenis`
- **低配环境**：回退浏览器原生滚动，避免持续 RAF 驱动

### 3.3 首屏后增强内容策略

- 右侧 AI 面板延后为**核心区接近可视区后再异步加载**
- 首屏之外的额外逻辑不抢占首页第一屏的网络与脚本执行窗口

---

## 4. 组件改动设计

### 4.1 `F:\dachuang\frontend\src\components\home\HomeImmersiveStory.vue`

改造点：

- 移除静态 `lottie-web` 顶层引入，改成动态 `import()`
- 新增 Hero poster 图层，用于首屏展示与动画加载前兜底
- 增加动画是否允许加载的运行时判断
- 增加延后加载调度（idle + intersection）
- 对 Lottie 模块与动画 JSON 做内存级缓存，减少 SPA 回访重复开销
- 加载失败时保持 poster，不让首屏出现空白

### 4.2 `F:\dachuang\frontend\src\views\Home.vue`

改造点：

- 初始化时先读取设备/网络能力
- 仅在允许条件下启用 `Lenis`
- 保留现有滚动联动与 reveal 机制

### 4.3 `F:\dachuang\frontend\src\components\home\HomeCoreSection.vue`

改造点：

- 将右侧 `HomeAiPanel` 改为异步组件
- 当核心表单区域接近可视区时再加载 AI 面板
- 未加载前展示轻量骨架 / 占位外壳，避免桌面布局塌陷

### 4.4 `F:\dachuang\frontend\src\utils\heroPerformance.js`

新增职责：

- 统一封装设备与网络能力判断
- 统一输出：
  - 是否允许 Lottie
  - 是否允许 `Lenis`
  - 是否优先 poster
- 提供 idle 调度降级封装

---

## 5. 数据流与状态

### Hero

1. 页面 mounted
2. 读取运行环境能力画像
3. 立即显示 poster + 文案层
4. 若允许 Lottie：
   - 等待 idle 或接近可见区
   - 动态加载 Lottie 模块
   - 拉取并缓存 animation data
   - 初始化动画实例
   - 动画 ready 后淡入 canvas
5. 若不允许 Lottie：
   - 保持 poster 模式
   - 继续使用滚动进度控制文案切换与 reveal

### Core AI Panel

1. 首页 mounted 时不立即加载 AI 面板
2. `#core` 区域接近可视区后触发异步组件装载
3. 装载完成后替换占位面板

---

## 6. 错误处理

- Lottie 动态 import 失败：保留 poster，不阻塞页面其余逻辑
- 动画 JSON 拉取失败：记录错误并继续 poster 模式
- `IntersectionObserver` 不可用：直接按 idle 策略执行
- `requestIdleCallback` 不可用：回退 `setTimeout`
- 环境探测 API 不可用：默认按保守策略执行

---

## 7. 测试与验收

### 自动验证

- 新增 `heroPerformance` 纯函数单测：
  - 高配桌面返回 full animation
  - 弱网 / Save-Data / 小屏返回 poster mode
  - idle 调度降级行为可验证

### 回归验证

- `npm test`
- `npm run build`
- 比较构建输出中 `Home-*.js` 是否下降
- 确认新增 Lottie 独立 chunk / AI 面板异步 chunk 的拆分结果

### 手动体验验证

- 桌面端：
  - 首屏先出 poster
  - 稍后进入完整 Lottie 动画
  - 按钮、文案、滚动揭示保持正常
- 移动端 / 弱网模拟：
  - 首屏不加载完整动画
  - 页面能稳定展示封面与文案
  - 不出现明显卡顿或白屏

---

## 8. 非目标

- 不重做首页视觉风格
- 不更换动画素材体系
- 不修改结果页 / 社区页现有增强效果
- 不引入新的重型前端依赖
