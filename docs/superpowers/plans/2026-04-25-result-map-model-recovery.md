# 结果页与地图驾驶舱恢复实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 恢复结果页高级视觉层与地图驾驶舱，并把模型与路线计算产出的信息更完整地展示到结果页中。

**Architecture:** 结果页的展示推导逻辑收敛到 `resultUi.js`，负责统计卡、Hero 信息、按天切分和路上信息格式化；`Result.vue` 仅组合这些结构并渲染；`ItineraryMapCard.vue` 负责地图本体、空间概览浮层、路线图例与社区状态提示。

**Tech Stack:** Vue 3、Element Plus、Leaflet、Vitest、Vite

---

### Task 1: 锁定结果页回归测试

**Files:**
- Modify: `F:\dachuang\frontend\src\views\__tests__\Result.test.js`
- Modify: `F:\dachuang\frontend\src\components\itinerary\__tests__\ItineraryMapCard.test.js`
- Modify: `F:\dachuang\frontend\src\utils\__tests__\resultUi.test.js`

- [ ] **Step 1: 写失败测试，锁定高级布局与地图驾驶舱结构**

```js
expect(resultSource).toContain('<aside class="hero-side">')
expect(resultSource).toContain('class="result-focus-grid"')
expect(resultSource).toContain('class="day-switcher"')
expect(mapCardSource).toContain('class="overview-panel"')
expect(mapCardSource).toContain('class="legend-panel"')
expect(mapCardSource).toContain('class="insight-grid"')
```

- [ ] **Step 2: 运行测试，确认当前版本失败**

Run:

```bash
npm test -- src/views/__tests__/Result.test.js src/components/itinerary/__tests__/ItineraryMapCard.test.js src/utils/__tests__/resultUi.test.js
```

Expected:

```text
FAIL ... Result.test.js
FAIL ... ItineraryMapCard.test.js
```

- [ ] **Step 3: 为展示逻辑新增失败测试**

```js
const hero = buildResultHeroContent({...})
expect(hero.pills).toContain('多方案对比')
expect(hero.departureSummary).toContain('首段')

const plans = buildDayPlans(nodes)
expect(plans).toHaveLength(2)
```

- [ ] **Step 4: 再次运行工具函数测试并确认失败**

Run:

```bash
npm test -- src/utils/__tests__/resultUi.test.js
```

Expected:

```text
FAIL ... buildResultHeroContent is not a function
```

### Task 2: 重建结果页展示逻辑

**Files:**
- Modify: `F:\dachuang\frontend\src\utils\resultUi.js`
- Modify: `F:\dachuang\frontend\src\views\Result.vue`
- Test: `F:\dachuang\frontend\src\utils\__tests__\resultUi.test.js`
- Test: `F:\dachuang\frontend\src\views\__tests__\Result.test.js`

- [ ] **Step 1: 在 `resultUi.js` 中补齐结果页推导逻辑**

```js
export const buildDayPlans = nodes => { /* 按 dayNo 或时间重置切分 */ }
export const buildResultHeroContent = ({ itinerary, activeOption, displayNodes, dayPlans, displayOptions, isLoggedIn }) => { /* 生成摘要、首段说明、胶囊、脚注 */ }
export const formatTravelMode = node => { /* 首段/路段交通方式 */ }
export const formatTravelDistance = node => { /* 公里字符串 */ }
export const formatNodeTravelLabel = node => { /* 从出发地前往约 X 分钟 / 上一站前往约 X 分钟 */ }
```

- [ ] **Step 2: 让结果页接入 Hero 双栏、按天切换与驾驶舱布局**

```vue
<aside class="hero-side">
  <p class="hero-side-copy">{{ heroRecommendation }}</p>
</aside>

<section class="result-focus-grid">
  <ItineraryMapCard :nodes="displayNodes" :community-status-text="shareStatusText" class="map-section" />
  <section class="timeline-panel">...</section>
</section>
```

- [ ] **Step 3: 把模型与路线信息完整上屏**

```vue
<p v-if="departureSummary" class="hero-location">{{ departureSummary }}</p>
<span v-for="pill in heroPills" :key="pill" class="hero-pill">{{ pill }}</span>
<div v-if="node.statusNote" class="stop-tip-box">...</div>
<div v-if="node.nearbyHotels?.length || node.nearbyFoods?.length || node.nearbyShops?.length" class="nearby-box">...</div>
```

- [ ] **Step 4: 运行目标测试，确认结果页与工具函数通过**

Run:

```bash
npm test -- src/views/__tests__/Result.test.js src/utils/__tests__/resultUi.test.js
```

Expected:

```text
PASS ... Result.test.js
PASS ... resultUi.test.js
```

### Task 3: 重建地图驾驶舱

**Files:**
- Modify: `F:\dachuang\frontend\src\components\itinerary\ItineraryMapCard.vue`
- Test: `F:\dachuang\frontend\src\components\itinerary\__tests__\ItineraryMapCard.test.js`

- [ ] **Step 1: 恢复地图驾驶舱结构**

```vue
<div class="map-stage">
  <div class="floating-panel overview-panel">...</div>
  <div class="floating-panel legend-panel">...</div>
</div>

<div v-if="mapInsightItems.length" class="insight-grid">...</div>
<p v-if="communityStatusText" class="community-status-copy">{{ communityStatusText }}</p>
```

- [ ] **Step 2: 恢复地图强化渲染**

```js
let routeHaloLayer = null
routeHaloLayer = L.polyline(points, { color: 'rgba(95, 158, 255, 0.18)', weight: 14 }).addTo(map)
polylineLayer = L.polyline(points, { color: '#4f9fff', weight: 5 }).addTo(map)
```

- [ ] **Step 3: 运行地图卡测试**

Run:

```bash
npm test -- src/components/itinerary/__tests__/ItineraryMapCard.test.js
```

Expected:

```text
PASS ... ItineraryMapCard.test.js
```

### Task 4: 完整验证

**Files:**
- Verify only

- [ ] **Step 1: 运行前端相关测试**

Run:

```bash
npm test -- src/views/__tests__/Result.test.js src/components/itinerary/__tests__/ItineraryMapCard.test.js src/utils/__tests__/resultUi.test.js
```

Expected:

```text
PASS ... 3 files
```

- [ ] **Step 2: 运行前端构建**

Run:

```bash
npm run build
```

Expected:

```text
vite build completed successfully
```
