<template>
  <section class="hero-section" id="hero">
    <div class="hero-container">
      <el-row :gutter="60" align="middle" class="hero-row">
        <!-- 左半部分：大号文案与操作组 -->
        <el-col :xs="24" :md="12" class="hero-text-col">
          <div class="hero-tag">
            <el-tag effect="light" type="primary" round>🚀 全新版本 - AIGC 驱动</el-tag>
          </div>
          <h1 class="hero-title">
            告别繁琐攻略<br>
            <span class="text-highlight">定制你的成都短途路线</span>
          </h1>
          <p class="hero-subtitle">
            只需告诉系统你的出行时间、心理预算和爱好偏好。
            我们通过动态通行时间估算与场景重排算法，
            为你一键生成科学、详尽且高度可执行的专属旅游计划。
          </p>
          <div class="hero-actions">
            <!-- 触发外层统一滚动到配置区 -->
            <el-button 
              type="primary" 
              size="large" 
              class="btn-primary" 
              @click="$emit('scrollTo', '#core')">
              免费规划行程
            </el-button>
            <el-button 
              size="large" 
              class="btn-secondary"
              @click="$emit('scrollTo', '#core')">
              <el-icon class="icon-chat"><ChatDotRound /></el-icon>
              先问问 AI 规划师
            </el-button>
          </div>

          <div class="hero-stats">
            <div class="stat-item">
              <div class="stat-num">500+</div>
              <div class="stat-label">覆盖成都 POI</div>
            </div>
            <div class="stat-devider"></div>
            <div class="stat-item">
              <div class="stat-num">~2s</div>
              <div class="stat-label">生成规划速度</div>
            </div>
            <div class="stat-devider"></div>
            <div class="stat-item">
              <div class="stat-num">100%</div>
              <div class="stat-label">AI场景定制</div>
            </div>
          </div>
        </el-col>

        <!-- 右半部分：真实规划能力预览 -->
        <el-col :xs="24" :md="12" class="hero-visual-col">
          <div class="route-preview-wrapper">
            <!-- 规划能力标签 1 -->
            <div class="float-badge badge-top">
              <el-icon color="#67C23A"><Filter /></el-icon>
              <span>预算约束实时参与评分</span>
            </div>
            <!-- 规划能力标签 2 -->
            <div class="float-badge badge-bottom">
              <el-icon color="#E6A23C"><Sunny /></el-icon>
              <span>高德路网耗时兜底可用</span>
            </div>

            <!-- 核心规划链路预览 -->
            <div class="route-preview-card">
              <div class="route-preview-header">
                <div class="route-preview-dots">
                  <span class="dot red"></span>
                  <span class="dot yellow"></span>
                  <span class="dot green"></span>
                </div>
                <div class="route-preview-title">真实路网 + 算法 + AI Critic</div>
              </div>
              <div class="route-preview-body">
                <el-timeline>
                  <el-timeline-item
                    color="#409eff"
                    timestamp="步骤 1"
                    placement="top">
                    <div class="planner-step">
                      <div class="node-title">高德路网耗时估算</div>
                      <div class="node-tags">
                        <span class="tag">公交 / 打车 / 步行</span>
                        <span class="tag">失败自动降级</span>
                      </div>
                    </div>
                  </el-timeline-item>
                  <el-timeline-item
                    color="#e4e7ed"
                    timestamp="真实交通时间进入时间窗约束"
                    placement="top"
                    class="travel-timeline">
                  </el-timeline-item>
                  <el-timeline-item
                    color="#67c23a"
                    timestamp="步骤 2"
                    placement="top">
                    <div class="planner-step">
                      <div class="node-title">Top 候选路线生成</div>
                      <div class="node-tags">
                        <span class="tag">DP / Beam Search</span>
                        <span class="tag">权重可配置</span>
                      </div>
                    </div>
                  </el-timeline-item>
                  <el-timeline-item
                    color="#409eff"
                    timestamp="步骤 3"
                    placement="top">
                    <div class="planner-step">
                      <div class="node-title">大模型交叉评估</div>
                      <div class="node-tags">
                        <span class="tag">常识判别</span>
                        <span class="tag">说明淘汰理由</span>
                      </div>
                    </div>
                  </el-timeline-item>
                </el-timeline>

                <div class="critic-note">
                  <div class="bot-avatar">AI</div>
                  <div class="bot-text">
                    后端先生成多条绝对可行路线，再由 AI Critic 结合用户自然语言需求选择最终展示方案。
                  </div>
                </div>
              </div>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>
  </section>
</template>

<script setup>
import { ChatDotRound, Filter, Sunny } from '@element-plus/icons-vue'
</script>

<style scoped>
.hero-section {
  position: relative;
  background-color: #f7f8fa;
  padding: 80px 24px;
  overflow: hidden;
}

/* 点缀背景 */
.hero-section::before {
  content: '';
  position: absolute;
  top: -100px;
  right: -100px;
  width: 500px;
  height: 500px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(64,158,255,0.06) 0%, rgba(255,255,255,0) 70%);
  z-index: 0;
}

.hero-container {
  max-width: 1200px;
  margin: 0 auto;
  position: relative;
  z-index: 1;
}

.hero-text-col {
  padding-right: 20px;
}

.hero-tag {
  margin-bottom: 24px;
}

.hero-title {
  font-size: 52px;
  line-height: 1.2;
  font-weight: 800;
  color: #1f2d3d;
  margin: 0 0 24px 0;
  letter-spacing: -0.5px;
}

.text-highlight {
  color: transparent;
  background: linear-gradient(90deg, #409EFF, #66b1ff);
  background-clip: text;
  -webkit-background-clip: text;
}

.hero-subtitle {
  font-size: 18px;
  line-height: 1.6;
  color: #606266;
  margin-bottom: 40px;
  max-width: 480px;
}

.hero-actions {
  display: flex;
  gap: 16px;
  margin-bottom: 50px;
}

.btn-primary {
  font-size: 16px;
  font-weight: 600;
  padding: 0 32px;
  height: 52px;
  border-radius: 26px;
  box-shadow: 0 8px 16px rgba(64,158,255,0.25);
  transition: all 0.3s;
}

.btn-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 24px rgba(64,158,255,0.35);
}

.btn-secondary {
  font-size: 16px;
  font-weight: 500;
  height: 52px;
  padding: 0 28px;
  border-radius: 26px;
  background-color: white;
  color: #303133;
  border: 1px solid #dcdfe6;
  transition: all 0.3s;
}

.btn-secondary:hover {
  border-color: #409EFF;
  color: #409EFF;
}

.icon-chat {
  margin-right: 6px;
  font-size: 18px;
}

.hero-stats {
  display: flex;
  align-items: center;
  gap: 32px;
}

.stat-item {
  display: flex;
  flex-direction: column;
}

.stat-num {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}

.stat-devider {
  width: 1px;
  height: 30px;
  background-color: #e4e7ed;
}

/* ================ 右侧视觉化 ================ */
.hero-visual-col {
  position: relative;
  display: flex;
  justify-content: center;
  align-items: center;
}

.route-preview-wrapper {
  position: relative;
  width: 100%;
  max-width: 420px;
  /* 轻微透视营造产品层次 */
  transform: perspective(1000px) rotateY(-5deg) rotateX(2deg);
  transition: transform 0.5s ease;
}

.route-preview-wrapper:hover {
  transform: perspective(1000px) rotateY(0deg) rotateX(0deg);
}

.float-badge {
  position: absolute;
  background: white;
  padding: 10px 16px;
  border-radius: 20px;
  box-shadow: 0 8px 24px rgba(0,0,0,0.08);
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 8px;
  z-index: 10;
}

.badge-top {
  top: 20px;
  right: -30px;
  animation: float 4s ease-in-out infinite;
}

.badge-bottom {
  bottom: 80px;
  left: -40px;
  animation: float 5s ease-in-out infinite reverse;
}

.route-preview-card {
  background: white;
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.08);
  border: 1px solid rgba(228,231,237,0.5);
  overflow: hidden;
}

.route-preview-header {
  height: 48px;
  background: #fbfbfc;
  border-bottom: 1px solid #f0f2f5;
  display: flex;
  align-items: center;
  padding: 0 16px;
}

.route-preview-dots {
  display: flex;
  gap: 6px;
  margin-right: 16px;
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
.red { background-color: #ff5f56; }
.yellow { background-color: #ffbd2e; }
.green { background-color: #27c93f; }

.route-preview-title {
  font-size: 14px;
  font-weight: 600;
  color: #606266;
}

.route-preview-body {
  padding: 30px 24px 20px;
}

.planner-step .node-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 6px;
}

.planner-step .tag {
  font-size: 12px;
  color: #909399;
  background: #f4f4f5;
  padding: 2px 8px;
  border-radius: 4px;
  margin-right: 6px;
}

/* 覆盖 Element Timeline 默认过长的留白 */
:deep(.el-timeline-item) {
  padding-bottom: 20px;
}
:deep(.el-timeline-item__timestamp.is-top) {
  font-size: 13px;
  color: #909399;
}

.critic-note {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-top: 10px;
  background: #f4f8ff;
  border: 1px solid #d9ecff;
  padding: 12px 16px;
  border-radius: 8px;
}

.bot-avatar {
  background: #409EFF;
  color: white;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-size: 12px;
  font-weight: bold;
}

.bot-text {
  font-size: 13px;
  color: #1f2d3d;
  line-height: 1.5;
  flex: 1;
}

@keyframes float {
  0% { transform: translateY(0px); }
  50% { transform: translateY(-10px); }
  100% { transform: translateY(0px); }
}

/* 响应式适配 */
@media (max-width: 991px) {
  .hero-row {
    flex-direction: column-reverse; /* 小频幕图在上文字在下或反之，由于视觉冲突这里保持右半部下移 */
  }
  .hero-text-col {
    padding-right: 0;
    margin-top: 40px;
    text-align: center;
  }
  .hero-subtitle {
    margin-left: auto;
    margin-right: auto;
  }
  .hero-actions {
    justify-content: center;
  }
  .hero-stats {
    justify-content: center;
  }
  .badge-top { right: -10px; }
  .badge-bottom { left: -10px; }
}
</style>
