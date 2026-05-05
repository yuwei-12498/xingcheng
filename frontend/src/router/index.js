import { ElMessage } from 'element-plus'
import { createRouter, createWebHistory } from 'vue-router'
import { initAuthState, useAuthState } from '@/store/auth'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/Home.vue'),
    meta: { title: '\u9996\u9875 - \u5F00\u59CB\u89C4\u5212' }
  },
  {
    path: '/community',
    name: 'Community',
    component: () => import('@/views/Community.vue'),
    meta: { title: '\u793E\u533A\u5927\u5385' }
  },
  {
    path: '/community/:id',
    name: 'CommunityDetail',
    component: () => import('@/views/CommunityDetail.vue'),
    meta: { title: '\u793E\u533A\u52A8\u6001' }
  },
  {
    path: '/result',
    name: 'Result',
    component: () => import('@/views/Result.vue'),
    meta: { title: '\u4F60\u7684\u4E13\u5C5E\u884C\u7A0B' }
  },
  {
    path: '/history',
    name: 'History',
    component: () => import('@/views/History.vue'),
    meta: { title: '\u5386\u53F2\u884C\u7A0B\u4E0E\u6536\u85CF', requiresAuth: true }
  },
  {
    path: '/auth',
    name: 'Auth',
    component: () => import('@/views/Auth.vue'),
    meta: { title: '\u767B\u5F55\u4E0E\u6CE8\u518C', hideGlobalChat: true }
  },
  {
    path: '/detail/:id',
    name: 'Detail',
    component: () => import('@/views/Detail.vue'),
    meta: { title: '\u70B9\u4F4D\u8BE6\u60C5\u4E0E\u8C03\u6574', requiresAuth: true }
  },
  {
    path: '/admin',
    component: () => import('@/views/admin/AdminLayout.vue'),
    meta: { requiresAdmin: true, hideGlobalChat: true },
    children: [
      {
        path: '',
        redirect: '/admin/users'
      },
      {
        path: 'users',
        name: 'AdminUsers',
        component: () => import('@/views/admin/UserManage.vue'),
        meta: { title: '\u7528\u6237\u4E2D\u5FC3\u5927\u76D8' }
      },
      {
        path: 'pois',
        name: 'AdminPois',
        component: () => import('@/views/admin/PoiManage.vue'),
        meta: { title: 'POI \u8D44\u6E90\u6CBB\u7406' }
      },
      {
        path: 'community',
        name: 'AdminCommunity',
        component: () => import('@/views/admin/CommunityManage.vue'),
        meta: { title: '\u793E\u533A\u5185\u5BB9\u6CBB\u7406' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to, from, next) => {
  if (to.meta.title) {
    document.title = `${to.meta.title} - \u884C\u57CE\u6709\u6570`
  }

  if (to.meta.requiresAuth) {
    const authState = useAuthState()
    if (!authState.initialized) {
      await initAuthState()
    }
    if (!authState.user) {
      next({
        path: '/auth',
        query: {
          redirect: to.fullPath
        }
      })
      return
    }
  }

  if (to.meta.requiresAdmin) {
    const authState = useAuthState()
    if (!authState.initialized) {
      await initAuthState()
    }
    if (!authState.user || authState.user.role !== 1) {
      ElMessage.error('\u672A\u6388\u6743\u8BBF\u95EE\u540E\u53F0\u7BA1\u7406')
      next('/')
      return
    }
  }

  next()
})

export default router
