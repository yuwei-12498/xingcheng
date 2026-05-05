<template>
  <section class="feature-section" id="features">
    <div class="feature-container">
      <div class="section-header">
        <h2 class="section-title">不止于排版，而是深度计算</h2>
        <p class="section-subtitle">融合本地算法与大语言模型，将每一分钟的出行细节落到实处</p>
      </div>

      <!-- Feature 1: 行程动态时长估算 -->
      <div class="feature-row">
        <el-row :gutter="60" align="middle">
          <el-col :md="12" class="feature-text">
            <div class="feature-label">01 / Dynamic Time Estimation</div>
            <h3 class="feature-title">基于时序的动态时间分配</h3>
            <p class="feature-desc">
              系统并非简单地拼凑景点。它会严格遵循你设定的起止时间，并根据每个 POI 的推荐深度游玩时长（结合文化底蕴与规模），以及两点之间的路程估算，自动推演出精确到分钟的时间轴。
            </p>
            <ul class="feature-list">
              <li><el-icon><Check /></el-icon> <span>硬约束不超时</span></li>
              <li><el-icon><Check /></el-icon> <span>自动预留通行与午餐间隙</span></li>
            </ul>
          </el-col>
          <el-col :md="12" class="feature-visual">
            <div class="visual-placeholder type-1">
              <div class="wireframe-time">
                <div class="time-block" style="width: 20%;">09:00 出发</div>
                <div class="time-block active" style="width: 50%;">杜甫草堂 (150分钟)</div>
                <div class="time-block" style="width: 30%;">午餐预留</div>
              </div>
            </div>
          </el-col>
        </el-row>
      </div>

      <!-- Feature 2: 智能重排与替换 -->
      <div class="feature-row reverse">
        <el-row :gutter="60" align="middle">
          <el-col :md="12" class="feature-text">
            <div class="feature-label">02 / Intelligent Rescheduling</div>
            <h3 class="feature-title">灵活的单点替换与自适应重排</h3>
            <p class="feature-desc">
              计划永远没有变化快？没关系。在生成结果页面，如果对某个景点不满意，点击“换一个”，系统将推荐同等游玩时长/相近属性的候补点位，并瞬间重排后续时间轴，保持整体连贯。
            </p>
            <ul class="feature-list">
              <li><el-icon><Check /></el-icon> <span>同分类候选池推荐</span></li>
              <li><el-icon><Check /></el-icon> <span>局部微调不破坏全局结构</span></li>
            </ul>
          </el-col>
          <el-col :md="12" class="feature-visual">
            <div class="visual-placeholder type-2">
              <div class="wireframe-card">
                <div class="card-item strikethrough">锦里古街</div>
                <el-icon class="switch-icon"><Switch /></el-icon>
                <div class="card-item highlight">宽窄巷子</div>
              </div>
            </div>
          </el-col>
        </el-row>
      </div>

      <!-- Feature 3: AI 旅行助理 -->
      <div class="feature-row">
        <el-row :gutter="60" align="middle">
          <el-col :md="12" class="feature-text">
            <div class="feature-label">03 / AI Travel Assistant</div>
            <h3 class="feature-title">全天候的 AI 背景导游</h3>
            <p class="feature-desc">
              不想做枯燥的攻略功课？随时唤出右下角的 AI 旅行助手！无论是询问武侯祠背后的三国轶事、打探东郊记忆最出片的废土机位，还是查询带宝宝的如厕换尿布贴士，AI 都会结合你当前行程的上下文（甚至包括是否雨天）给出灵动解答。
            </p>
          </el-col>
          <el-col :md="12" class="feature-visual">
            <div class="visual-placeholder type-3">
              <div class="wireframe-chat">
                <div class="chat-bubble left">武侯祠有红墙竹影吗？</div>
                <div class="chat-bubble right">不仅有，下午三点光线最好！另外检测到您今天安排了雨天...</div>
              </div>
            </div>
          </el-col>
        </el-row>
      </div>
    </div>
  </section>
</template>

<script setup>
import { Check, Switch } from '@element-plus/icons-vue'
</script>

<style scoped>
.feature-section {
  padding: 100px 24px;
  background-color: #ffffff;
}

.feature-container {
  max-width: 1100px;
  margin: 0 auto;
}

.section-header {
  text-align: center;
  margin-bottom: 80px;
}

.section-title {
  font-size: 32px;
  font-weight: 800;
  color: #1f2d3d;
  margin: 0 0 16px 0;
}

.section-subtitle {
  font-size: 16px;
  color: #606266;
  margin: 0;
}

.feature-row {
  margin-bottom: 100px;
}

.feature-row:last-child {
  margin-bottom: 0;
}

@media (min-width: 992px) {
  .feature-row.reverse .el-row {
    flex-direction: row-reverse;
  }
}

.feature-label {
  font-size: 13px;
  font-weight: 700;
  color: #409EFF;
  letter-spacing: 1px;
  text-transform: uppercase;
  margin-bottom: 16px;
}

.feature-title {
  font-size: 28px;
  font-weight: 700;
  color: #1f2d3d;
  margin: 0 0 20px 0;
  line-height: 1.3;
}

.feature-desc {
  font-size: 16px;
  color: #606266;
  line-height: 1.7;
  margin-bottom: 24px;
}

.feature-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.feature-list li {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  font-size: 15px;
  color: #475669;
  font-weight: 500;
}

.feature-list li .el-icon {
  color: #67C23A;
  font-size: 18px;
}

/* ======== Visual Placeholders (UI 骨架感) ======== */
.feature-visual {
  display: flex;
  justify-content: center;
  align-items: center;
}

.visual-placeholder {
  width: 100%;
  aspect-ratio: 4/3;
  border-radius: 20px;
  background: #f7f8fa;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(228, 231, 237, 0.6);
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.03);
  padding: 30px;
  box-sizing: border-box;
}

/* 时间轴骨架 */
.wireframe-time {
  width: 100%;
  height: 40px;
  background: #ebeef5;
  border-radius: 8px;
  display: flex;
  overflow: hidden;
}
.time-block {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  color: #909399;
  border-right: 2px solid white;
}
.time-block.active {
  background: #409EFF;
  color: white;
}

/* 重排骨架 */
.wireframe-card {
  display: flex;
  align-items: center;
  gap: 20px;
}
.card-item {
  padding: 16px 24px;
  border-radius: 8px;
  font-size: 15px;
  font-weight: 600;
}
.strikethrough {
  background: #f4f4f5;
  color: #a8abb2;
  text-decoration: line-through;
}
.highlight {
  background: #ecf5ff;
  color: #409EFF;
  border: 1px solid #c6e2ff;
}
.switch-icon {
  font-size: 24px;
  color: #909399;
}

/* 聊天骨架 */
.wireframe-chat {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.chat-bubble {
  max-width: 80%;
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.5;
}
.chat-bubble.left {
  align-self: flex-start;
  background: #ffffff;
  border: 1px solid #e4e7ed;
  color: #303133;
}
.chat-bubble.right {
  align-self: flex-end;
  background: #409EFF;
  color: #ffffff;
}

@media (max-width: 991px) {
  .feature-text {
    margin-bottom: 40px;
  }
}
</style>
