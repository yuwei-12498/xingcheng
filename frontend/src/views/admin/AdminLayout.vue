<template>
  <el-container class="admin-layout">
    <el-aside width="220px" class="admin-aside">
      <div class="logo">
        <h2>行城有数后台</h2>
      </div>
      <el-menu
        :default-active="activeMenu"
        class="el-menu-vertical"
        router
        background-color="#f8fafc"
        text-color="#475569"
        active-text-color="#14B8A6"
      >
        <el-menu-item index="/admin/users">
          <el-icon><User /></el-icon>
          <span>用户池监控</span>
        </el-menu-item>
        <el-menu-item index="/admin/pois">
          <el-icon><MapLocation /></el-icon>
          <span>POI 资源治理</span>
        </el-menu-item>
        <el-menu-item index="/admin/community">
          <el-icon><ChatLineSquare /></el-icon>
          <span>社区内容治理</span>
        </el-menu-item>
        <el-menu-item index="/">
           <el-icon><HomeFilled /></el-icon>
           <span>返回大厅</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    
    <el-container>
      <el-header class="admin-header">
        <div class="breadcrumb">
          <h3>{{ $route.meta.title || '系统管理' }}</h3>
        </div>
        <div class="admin-user-info">
          <span>欢迎，管理员</span>
        </div>
      </el-header>
      
      <el-main class="admin-main">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { User, MapLocation, HomeFilled, ChatLineSquare } from '@element-plus/icons-vue'

const route = useRoute()
const activeMenu = computed(() => route.path)
</script>

<style scoped>
.admin-layout {
  height: 100vh;
  background-color: #f1f5f9;
}

.admin-aside {
  background-color: #f8fafc;
  border-right: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
}

.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  border-bottom: 1px solid #e2e8f0;
}

.logo h2 {
  margin: 0;
  color: #14B8A6;
  font-size: 1.25rem;
}

.el-menu-vertical {
  border-right: none;
}

.admin-header {
  background-color: #ffffff;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 24px;
}

.breadcrumb h3 {
  margin: 0;
  font-size: 1.1rem;
  color: #334155;
}

.admin-user-info {
  font-size: 0.9rem;
  color: #64748b;
  font-weight: 500;
}

.admin-main {
  padding: 24px;
  box-sizing: border-box;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
