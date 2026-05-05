<template>
  <section class="cta-section">
    <div class="cta-container">
      <div class="cta-content">
        <h2 class="cta-title">准备好探索成都的下一个角落了吗？</h2>
        <p class="cta-subtitle">抛弃繁细的攻略文，体验由 AI 和计算为您专属定制的智能短途游。</p>
        <el-button 
          type="primary" 
          size="large" 
          class="final-btn"
          @click="$emit('scrollTo', '#core')">
          立即免费生成行程
        </el-button>
      </div>
      <!-- 简单的产品信息尾栏 -->
      <footer class="footer-bar">
        <span>&copy; {{ new Date().getFullYear() }} 行“城”有数 - 城市短途个性化行程生成平台</span>
        <span class="version">大创展示版 v2.0</span>
      </footer>
    </div>
  </section>
</template>

<script setup>
</script>

<style scoped>
.cta-section {
  background-color: #1f2d3d;
  color: #ffffff;
  padding: 80px 24px 0 24px;
}

.cta-container {
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.cta-content {
  text-align: center;
  max-width: 600px;
  margin-bottom: 80px;
}

.cta-title {
  font-size: 36px;
  font-weight: 800;
  margin: 0 0 20px 0;
  line-height: 1.3;
}

.cta-subtitle {
  font-size: 16px;
  color: #a0aec0;
  margin: 0 0 40px 0;
  line-height: 1.6;
}

.final-btn {
  font-size: 16px;
  font-weight: 600;
  padding: 0 40px;
  height: 56px;
  border-radius: 28px;
  box-shadow: 0 8px 16px rgba(0, 0, 0, 0.2);
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
}

.final-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 24px rgba(64, 158, 255, 0.4);
}

.footer-bar {
  width: 100%;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  padding: 24px 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  color: #8492a6;
}

@media (max-width: 768px) {
  .footer-bar {
    flex-direction: column;
    gap: 10px;
    text-align: center;
  }
}
</style>
