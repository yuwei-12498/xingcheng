<template>
  <el-header class="app-navbar">
    <div class="navbar-container">
      <button type="button" class="brand-button" @click="router.push('/')">
        <span class="logo-badge">
          <el-icon :size="18"><Location /></el-icon>
        </span>
        <span class="brand-copy">
          <strong class="logo-text">行城有数</strong>
          <small class="logo-subtitle">City route studio</small>
        </span>
      </button>

      <nav v-if="isHome" class="navbar-center" aria-label="首页导航">
        <a href="#hero" class="nav-link" @click.prevent="scrollTo('#hero')">首页</a>
        <a href="#core" class="nav-link" @click.prevent="scrollTo('#core')">开始规划</a>
        <a href="#scenarios" class="nav-link" @click.prevent="scrollTo('#scenarios')">热门场景</a>
        <a href="#features" class="nav-link" @click.prevent="scrollTo('#features')">系统能力</a>
        <a href="#examples" class="nav-link" @click.prevent="scrollTo('#examples')">示例路线</a>
      </nav>

      <div class="navbar-right">
        <el-button round class="glass-btn" @click="goCommunity">社区大厅</el-button>
        <el-button
          v-if="isAdmin && !isAdminPage"
          round
          class="glass-btn admin-btn"
          @click="goAdmin"
        >
          管理后台
        </el-button>
        <el-button
          v-if="authState.user && !isHistory"
          round
          class="glass-btn"
          @click="goHistory"
        >
          历史行程
        </el-button>

        <el-button
          v-if="isHome"
          type="primary"
          round
          class="primary-btn"
          @click="scrollTo('#core')"
        >
          立即规划
        </el-button>
        <el-button
          v-else
          round
          class="glass-btn"
          @click="router.push('/')"
        >
          返回首页
        </el-button>

        <el-dropdown
          v-if="authState.user"
          trigger="click"
          placement="bottom-end"
          class="user-dropdown"
          @command="handleUserCommand"
        >
          <div class="user-entry">
            <span class="user-avatar">{{ userInitial }}</span>
            <span class="user-name">{{ displayName }}</span>
            <el-icon class="user-arrow"><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item disabled>
                当前账号：{{ authState.user.username }}
              </el-dropdown-item>
              <el-dropdown-item command="community">社区大厅</el-dropdown-item>
              <el-dropdown-item v-if="isAdmin" command="admin">管理后台</el-dropdown-item>
              <el-dropdown-item command="history">历史行程与收藏</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <el-button v-else round class="glass-btn auth-btn" @click="goAuth">
          登录 / 注册
        </el-button>
      </div>
    </div>
  </el-header>
</template>

<script setup>
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowDown, Location } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { clearAuthUser, useAuthState } from '@/store/auth'

const route = useRoute()
const router = useRouter()
const authState = useAuthState()

const isHome = computed(() => route.path === '/')
const isHistory = computed(() => route.path === '/history')
const isAdmin = computed(() => authState.user?.role === 1)
const isAdminPage = computed(() => route.path.startsWith('/admin'))
const displayName = computed(() => authState.user?.nickname || authState.user?.username || '')
const userInitial = computed(() => {
  const value = displayName.value
  return value ? value.slice(0, 1).toUpperCase() : 'U'
})

const scrollTo = selector => {
  const el = document.querySelector(selector)
  if (el) {
    const y = el.getBoundingClientRect().top + window.scrollY - 80
    window.scrollTo({ top: y, behavior: 'smooth' })
  }
}

const goAuth = () => {
  const redirect = route.fullPath === '/auth' ? '/' : route.fullPath
  router.push({
    path: '/auth',
    query: {
      redirect
    }
  })
}

const goCommunity = () => {
  router.push('/community')
}

const goHistory = () => {
  router.push('/history')
}

const goAdmin = () => {
  router.push('/admin/users')
}

const handleUserCommand = async command => {
  if (command === 'community') {
    goCommunity()
    return
  }
  if (command === 'admin') {
    goAdmin()
    return
  }
  if (command === 'history') {
    goHistory()
    return
  }
  if (command !== 'logout') {
    return
  }

  await clearAuthUser()
  ElMessage.success('已退出登录')
  if (route.path !== '/') {
    router.replace('/')
  }
}
</script>

<style scoped>
.app-navbar {
  position: sticky;
  top: 0;
  width: 100%;
  height: 72px !important;
  z-index: 1000;
  padding: 0;
  background: rgba(247, 251, 255, 0.68);
  backdrop-filter: blur(18px);
  border-bottom: 1px solid rgba(194, 216, 248, 0.72);
  box-shadow: 0 6px 18px rgba(106, 145, 198, 0.08);
}

.navbar-container {
  max-width: 1240px;
  height: 100%;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 0 24px;
}

.brand-button {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 0;
}

.logo-badge {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: linear-gradient(135deg, var(--brand-500), #8ac2ff);
  box-shadow: 0 12px 26px rgba(95, 158, 255, 0.28);
}

.brand-copy {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.logo-text {
  color: var(--text-strong);
  font-size: 19px;
  line-height: 1;
}

.logo-subtitle {
  margin-top: 4px;
  color: var(--text-soft);
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.navbar-center {
  display: none;
}

@media (min-width: 860px) {
  .navbar-center {
    display: inline-flex;
    align-items: center;
    gap: 24px;
    padding: 10px 18px;
    border-radius: 999px;
    background: rgba(255, 255, 255, 0.72);
    border: 1px solid rgba(198, 220, 252, 0.84);
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7);
  }
}

.nav-link {
  color: var(--text-body);
  font-size: 14px;
  font-weight: 600;
  text-decoration: none;
  transition: color 0.2s ease, transform 0.2s ease;
}

.nav-link:hover {
  color: var(--brand-600);
  transform: translateY(-1px);
}

.navbar-right {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  flex-shrink: 0;
}

.primary-btn,
.glass-btn,
.auth-btn {
  min-height: 40px;
  padding: 0 18px;
  border-radius: 999px;
}

.primary-btn {
  box-shadow: 0 12px 28px rgba(95, 158, 255, 0.26);
}

.glass-btn,
.auth-btn {
  color: var(--text-strong);
  border-color: rgba(184, 212, 255, 0.84);
  background: rgba(255, 255, 255, 0.78);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.82);
}

.glass-btn:hover,
.auth-btn:hover {
  color: var(--brand-600);
  border-color: rgba(95, 158, 255, 0.5);
  background: rgba(255, 255, 255, 0.96);
}

.user-dropdown {
  display: flex;
  align-items: center;
}

.user-entry {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 42px;
  padding: 0 14px 0 10px;
  border-radius: 999px;
  border: 1px solid rgba(184, 212, 255, 0.84);
  background: rgba(255, 255, 255, 0.78);
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.user-entry:hover {
  transform: translateY(-1px);
  box-shadow: 0 10px 24px rgba(95, 158, 255, 0.14);
}

.user-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  background: linear-gradient(135deg, var(--brand-500), #8ac2ff);
}

.user-name {
  color: var(--text-strong);
  font-size: 14px;
  font-weight: 600;
  max-width: 96px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-arrow {
  color: var(--text-soft);
  font-size: 12px;
}

@media (max-width: 767px) {
  .app-navbar {
    height: 64px !important;
  }

  .navbar-container {
    padding: 0 16px;
  }

  .navbar-right {
    gap: 8px;
  }

  .glass-btn,
  .auth-btn,
  .primary-btn {
    padding: 0 14px;
  }

  .admin-btn,
  .user-name {
    display: none;
  }

  .logo-subtitle {
    display: none;
  }
}

@media (max-width: 768px) {
  .app-navbar {
    height: 60px !important;
  }

  .navbar-container {
    padding: 0 12px;
    gap: 10px;
  }

  .brand-button {
    gap: 8px;
    min-width: 0;
  }

  .logo-badge {
    width: 36px;
    height: 36px;
    border-radius: 12px;
  }

  .logo-text {
    font-size: 17px;
    white-space: nowrap;
  }

  .navbar-right {
    gap: 6px;
    min-width: 0;
  }

  .glass-btn,
  .auth-btn,
  .primary-btn {
    min-height: 36px;
    padding: 0 12px;
    font-size: 13px;
  }

  .admin-btn,
  .user-name,
  .logo-subtitle {
    display: none;
  }

  .user-entry {
    min-height: 36px;
    padding: 0 8px;
  }

  .user-avatar {
    width: 26px;
    height: 26px;
  }
}
</style>
