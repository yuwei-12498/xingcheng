<template>
  <section class="scenario-section" id="scenarios">
    <div class="scenario-container">
      <div class="section-header">
        <h2 class="section-title">满足多种出行场景</h2>
        <p class="section-subtitle">无论是特种兵打卡还是慵懒周末，都能为你找到最优解</p>
      </div>

      <el-row :gutter="24" class="card-row">
        <!-- 场景 1 -->
        <el-col :xs="24" :sm="12" :md="6">
          <div class="scenario-card">
            <div class="card-icon-wrapper bg-blue">
              <el-icon><User /></el-icon>
            </div>
            <h3 class="card-title">朋友结伴</h3>
            <p class="card-desc">自动平衡众口难调的偏好，涵盖网红打卡与地道美食，让友谊在旅途中升温。</p>
          </div>
        </el-col>
        <!-- 场景 2 -->
        <el-col :xs="24" :sm="12" :md="6">
          <div class="scenario-card">
            <div class="card-icon-wrapper bg-pink">
              <el-icon><Present /></el-icon>
            </div>
            <h3 class="card-title">情侣约会</h3>
            <p class="card-desc">过滤高难度爬山，推荐浪漫夜游与精致下午茶，让每一站都充满仪式感。</p>
          </div>
        </el-col>
        <!-- 场景 3 -->
        <el-col :xs="24" :sm="12" :md="6">
          <div class="scenario-card">
            <div class="card-icon-wrapper bg-orange">
              <el-icon><Bicycle /></el-icon>
            </div>
            <h3 class="card-title">亲子遛娃</h3>
            <p class="card-desc">大幅降低步行强度，优先安排公园、博物馆等寓教于乐的安全无障碍点位。</p>
          </div>
        </el-col>
        <!-- 场景 4 -->
        <el-col :xs="24" :sm="12" :md="6">
          <div class="scenario-card">
            <div class="card-icon-wrapper bg-purple">
              <el-icon><Umbrella /></el-icon>
            </div>
            <h3 class="card-title">雨天探索</h3>
            <p class="card-desc">智能感知天气偏好，一键切换至室内展馆与大型商业综合体，风雨无阻。</p>
          </div>
        </el-col>
      </el-row>
    </div>
  </section>
</template>

<script setup>
import { User, Present, Bicycle, Umbrella } from '@element-plus/icons-vue'
</script>

<style scoped>
.scenario-section {
  padding: 100px 24px;
  background-color: #f7f8fa;
}

.scenario-container {
  max-width: 1200px;
  margin: 0 auto;
}

.section-header {
  text-align: center;
  margin-bottom: 60px;
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

.card-row {
  display: flex;
  flex-wrap: wrap;
}

.scenario-card {
  background: #ffffff;
  border-radius: 16px;
  padding: 40px 30px;
  height: 100%;
  box-sizing: border-box;
  text-align: left;
  transition: all 0.3s ease;
  border: 1px solid rgba(228, 231, 237, 0.4);
}

.scenario-card:hover {
  transform: translateY(-8px);
  box-shadow: 0 16px 32px rgba(0, 0, 0, 0.06);
  border-color: transparent;
}

.card-icon-wrapper {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  color: white;
  margin-bottom: 24px;
}

.bg-blue { background: linear-gradient(135deg, #409EFF, #3a8ee6); }
.bg-pink { background: linear-gradient(135deg, #ff758c, #ff7eb3); }
.bg-orange { background: linear-gradient(135deg, #f6d365, #fda085); }
.bg-purple { background: linear-gradient(135deg, #a18cd1, #fbc2eb); }

.card-title {
  font-size: 20px;
  font-weight: 700;
  color: #303133;
  margin: 0 0 16px 0;
}

.card-desc {
  font-size: 14px;
  color: #606266;
  line-height: 1.6;
  margin: 0;
}

@media (max-width: 768px) {
  .scenario-card {
    margin-bottom: 24px;
  }
}
</style>
