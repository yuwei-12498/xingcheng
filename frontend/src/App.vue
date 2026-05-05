<template>
  <el-config-provider :locale="locale">
    <el-container class="app-container" direction="vertical">
      <AppNavbar />

      <el-main class="app-main">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>

    <ChatWidget />
  </el-config-provider>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import ChatWidget from '@/components/ChatWidget.vue'
import AppNavbar from '@/components/layout/AppNavbar.vue'
import { initAuthState } from '@/store/auth'

const locale = ref(zhCn)

onMounted(() => {
  initAuthState()
})
</script>

<style>
:root {
  --brand-25: #f8fbff;
  --brand-50: #f1f7ff;
  --brand-100: #deecff;
  --brand-200: #c5ddff;
  --brand-300: #9cc8ff;
  --brand-500: #5f9eff;
  --brand-600: #497fe0;
  --brand-700: #305aa8;
  --text-strong: #183153;
  --text-body: #55708f;
  --text-soft: #7c90aa;
  --surface-card: rgba(255, 255, 255, 0.92);
  --surface-soft: rgba(246, 250, 255, 0.9);
  --surface-glass: rgba(248, 252, 255, 0.8);
  --border-soft: rgba(188, 214, 255, 0.8);
  --border-strong: rgba(120, 171, 245, 0.45);
  --shadow-soft: 0 18px 40px rgba(81, 120, 177, 0.1);
  --shadow-strong: 0 24px 54px rgba(81, 120, 177, 0.14);
  --radius-panel: 28px;
  --radius-card: 22px;
  --font-display: 'Georgia', 'Times New Roman', 'STSong', serif;
  --font-body: 'Avenir Next', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', 'Segoe UI', sans-serif;
}

html {
  background: linear-gradient(180deg, #f7fbff 0%, #f2f7ff 100%);
}

body {
  margin: 0;
  background:
    radial-gradient(circle at top left, rgba(125, 182, 255, 0.12), transparent 24%),
    radial-gradient(circle at top right, rgba(205, 229, 255, 0.16), transparent 26%),
    linear-gradient(180deg, #f7fbff 0%, #f2f7ff 100%);
  font-family: var(--font-body);
  color: var(--text-strong);
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

button,
input,
textarea,
select {
  font: inherit;
}

#app {
  min-height: 100vh;
}

html.lenis,
html.lenis body {
  height: auto;
}

.lenis.lenis-smooth {
  scroll-behavior: auto !important;
}

.lenis.lenis-stopped {
  overflow: hidden;
}

.app-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.app-main {
  padding: 0;
  width: 100%;
  flex: 1;
  overflow: visible !important;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.28s ease, transform 0.28s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

@media (max-width: 768px) {
  :root {
    --radius-panel: 22px;
    --radius-card: 18px;
  }

  html,
  body,
  #app {
    width: 100%;
    overflow-x: hidden;
  }

  .app-main {
    min-width: 0;
  }

  .el-dialog {
    width: calc(100vw - 24px) !important;
    max-width: calc(100vw - 24px);
    border-radius: 20px;
  }

  .el-message-box {
    width: calc(100vw - 32px) !important;
    max-width: calc(100vw - 32px);
  }
}
</style>
