import { createApp } from 'vue'
import App from './App.vue'
import router from './router'

import { ElMessage } from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

const app = createApp(App)

// 注册所有图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(router)

app.config.errorHandler = (err, instance, info) => {
  console.error('Vue application error:', err, info, instance)
  ElMessage.error(err?.message || '页面发生异常，请刷新后重试')
}

router.onError((err) => {
  console.error('Vue router error:', err)
  ElMessage.error(err?.message || '页面跳转失败，请稍后重试')
})

app.mount('#app')
