<template>
  <div class="auth-page">
    <div class="auth-shell">
      <section class="auth-intro">
        <div class="intro-badge">开启专属旅程</div>
        <h1>登录后继续规划</h1>
        <p>
          登录后即可向 AI 询问路线建议、生成专属行程，并同步保存你的历史记录与收藏。
        </p>

        <ul class="intro-list">
          <li>
            <strong>更顺路</strong>
            <span>系统会结合你的时间窗、预算和主题偏好继续规划。</span>
          </li>
          <li>
            <strong>更省心</strong>
            <span>登录后可以持续调整路线、替换点位，并保留上下文。</span>
          </li>
          <li>
            <strong>可沉淀</strong>
            <span>历史行程、收藏和社区互动都需要账号状态来承接。</span>
          </li>
        </ul>
      </section>

      <el-card class="auth-card" shadow="never">
        <div class="card-head">
          <div class="head-kicker">开启专属旅程</div>
          <h2>{{ authTitle }}</h2>
          <p>
            {{ authSubtitle }}
          </p>
        </div>

        <el-tabs v-model="activeTab" stretch class="auth-tabs">
          <el-tab-pane label="登录" name="login">
            <el-form
              ref="loginFormRef"
              :model="loginForm"
              :rules="loginRules"
              label-position="top"
              size="large"
              class="auth-form">
              <el-form-item label="用户名" prop="username">
                <el-input
                  v-model="loginForm.username"
                  placeholder="请输入用户名"
                  autocomplete="username"
                  @keyup.enter="handleLogin" />
              </el-form-item>
              <el-form-item label="密码" prop="password">
                <el-input
                  v-model="loginForm.password"
                  type="password"
                  show-password
                  placeholder="请输入密码"
                  autocomplete="current-password"
                  @keyup.enter="handleLogin" />
              </el-form-item>
              <div class="form-link-row">
                <button type="button" class="switch-link" @click="activeTab = 'forgot'">忘记密码？</button>
              </div>
              <el-button
                type="primary"
                size="large"
                class="submit-btn"
                :loading="loginLoading"
                @click="handleLogin">
                登录并开始规划
              </el-button>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="找回密码" name="forgot">
            <el-form
              ref="forgotFormRef"
              :model="forgotForm"
              :rules="forgotRules"
              label-position="top"
              size="large"
              class="auth-form">
              <el-form-item label="注册邮箱" prop="email">
                <div class="code-row">
                  <el-input
                    v-model="forgotForm.email"
                    type="email"
                    placeholder="请输入注册时绑定的邮箱"
                    autocomplete="email" />
                  <el-button
                    class="code-btn"
                    :disabled="resetCodeDisabled"
                    :loading="resetCodeLoading"
                    @click="handleSendResetCode">
                    {{ resetCodeCountdown > 0 ? `${resetCodeCountdown}s 后重试` : '获取验证码' }}
                  </el-button>
                </div>
              </el-form-item>
              <el-form-item label="邮箱验证码" prop="emailCode">
                <el-input
                  v-model="forgotForm.emailCode"
                  maxlength="6"
                  placeholder="请输入 6 位验证码"
                  autocomplete="one-time-code" />
              </el-form-item>
              <el-form-item label="新密码" prop="password">
                <el-input
                  v-model="forgotForm.password"
                  type="password"
                  show-password
                  placeholder="6~20 位新密码"
                  autocomplete="new-password" />
              </el-form-item>
              <el-form-item label="确认新密码" prop="confirmPassword">
                <el-input
                  v-model="forgotForm.confirmPassword"
                  type="password"
                  show-password
                  placeholder="请再次输入新密码"
                  autocomplete="new-password"
                  @keyup.enter="handleResetPassword" />
              </el-form-item>
              <el-button
                type="primary"
                size="large"
                class="submit-btn"
                :loading="resetPasswordLoading"
                @click="handleResetPassword">
                重置密码
              </el-button>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="注册" name="register">
            <el-form
              ref="registerFormRef"
              :model="registerForm"
              :rules="registerRules"
              label-position="top"
              size="large"
              class="auth-form">
              <el-form-item label="用户名" prop="username">
                <el-input
                  v-model="registerForm.username"
                  placeholder="4~20 位字母、数字或下划线"
                  autocomplete="username" />
              </el-form-item>
              <el-form-item label="昵称" prop="nickname">
                <el-input
                  v-model="registerForm.nickname"
                  placeholder="请输入显示昵称"
                  autocomplete="nickname" />
              </el-form-item>
              <el-form-item label="邮箱" prop="email">
                <div class="code-row">
                  <el-input
                    v-model="registerForm.email"
                    type="email"
                    placeholder="请输入接收验证码的邮箱"
                    autocomplete="email" />
                  <el-button
                    class="code-btn"
                    :disabled="sendCodeDisabled"
                    :loading="sendCodeLoading"
                    @click="handleSendCode">
                    {{ sendCodeCountdown > 0 ? `${sendCodeCountdown}s 后重试` : '获取验证码' }}
                  </el-button>
                </div>
              </el-form-item>
              <el-form-item label="邮箱验证码" prop="emailCode">
                <el-input
                  v-model="registerForm.emailCode"
                  maxlength="6"
                  placeholder="请输入 6 位验证码"
                  autocomplete="one-time-code" />
              </el-form-item>
              <el-form-item label="密码" prop="password">
                <el-input
                  v-model="registerForm.password"
                  type="password"
                  show-password
                  placeholder="6~20 位密码"
                  autocomplete="new-password" />
              </el-form-item>
              <el-form-item label="确认密码" prop="confirmPassword">
                <el-input
                  v-model="registerForm.confirmPassword"
                  type="password"
                  show-password
                  placeholder="请再次输入密码"
                  autocomplete="new-password"
                  @keyup.enter="handleRegister" />
              </el-form-item>
              <el-button
                type="primary"
                size="large"
                class="submit-btn"
                :loading="registerLoading"
                @click="handleRegister">
                注册并开始使用
              </el-button>
            </el-form>
          </el-tab-pane>
        </el-tabs>

        <div class="switch-tip">
          <template v-if="activeTab === 'forgot'">
            <span>想起来密码了？</span>
            <button type="button" class="switch-link" @click="activeTab = 'login'">去登录</button>
          </template>
          <template v-else>
            <span>{{ activeTab === 'login' ? '还没有账号？' : '已经有账号？' }}</span>
            <button type="button" class="switch-link" @click="toggleTab">
              {{ activeTab === 'login' ? '去注册' : '去登录' }}
            </button>
          </template>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import {
  reqLogin,
  reqRegister,
  reqResetPassword,
  reqSendPasswordResetCode,
  reqSendRegisterCode
} from '@/api/auth'
import { initAuthState, setAuthUser, useAuthState } from '@/store/auth'

const route = useRoute()
const router = useRouter()
const authState = useAuthState()

const activeTab = ref(route.query.mode === 'register' ? 'register' : (route.query.mode === 'forgot' ? 'forgot' : 'login'))
const loginLoading = ref(false)
const registerLoading = ref(false)
const sendCodeLoading = ref(false)
const sendCodeCountdown = ref(0)
const resetCodeLoading = ref(false)
const resetCodeCountdown = ref(0)
const resetPasswordLoading = ref(false)
let sendCodeTimer = null
let resetCodeTimer = null
const loginFormRef = ref()
const registerFormRef = ref()
const forgotFormRef = ref()

const loginForm = reactive({
  username: '',
  password: ''
})

const registerForm = reactive({
  username: '',
  nickname: '',
  email: '',
  emailCode: '',
  password: '',
  confirmPassword: ''
})

const forgotForm = reactive({
  email: '',
  emailCode: '',
  password: '',
  confirmPassword: ''
})

const sendCodeDisabled = computed(() => sendCodeLoading.value || sendCodeCountdown.value > 0)
const resetCodeDisabled = computed(() => resetCodeLoading.value || resetCodeCountdown.value > 0)

const authTitle = computed(() => {
  if (activeTab.value === 'login') return '登录后继续规划'
  if (activeTab.value === 'forgot') return '找回密码'
  return '创建账号，立即开始'
})

const authSubtitle = computed(() => {
  if (activeTab.value === 'login') return '登录后即可继续生成行程、查看历史和收藏。'
  if (activeTab.value === 'forgot') return '通过绑定邮箱验证身份，重置后即可使用新密码登录。'
  return '注册成功后会自动登录，并跳转到你原本想去的页面。'
})

const redirectPath = computed(() => {
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
  return redirect && redirect !== '/auth' ? redirect : '/'
})

watch(
  () => route.query.mode,
  (mode) => {
    activeTab.value = mode === 'register' ? 'register' : (mode === 'forgot' ? 'forgot' : 'login')
  }
)

const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const validateConfirmPassword = (rule, value, callback) => {
  if (!value) {
    callback(new Error('请再次输入密码'))
    return
  }
  if (value !== registerForm.password) {
    callback(new Error('两次输入的密码不一致'))
    return
  }
  callback()
}

const validateResetConfirmPassword = (rule, value, callback) => {
  if (!value) {
    callback(new Error('请再次输入新密码'))
    return
  }
  if (value !== forgotForm.password) {
    callback(new Error('两次输入的新密码不一致'))
    return
  }
  callback()
}

const registerRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 4, max: 20, message: '用户名长度需为 4 到 20 位', trigger: 'blur' }
  ],
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { min: 2, max: 20, message: '昵称长度需为 2 到 20 位', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: ['blur', 'change'] }
  ],
  emailCode: [
    { required: true, message: '请输入邮箱验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '验证码需为 6 位数字', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度需为 6 到 20 位', trigger: 'blur' }
  ],
  confirmPassword: [{ validator: validateConfirmPassword, trigger: 'blur' }]
}

const forgotRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: ['blur', 'change'] }
  ],
  emailCode: [
    { required: true, message: '请输入邮箱验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '验证码需为 6 位数字', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度需为 6 到 20 位', trigger: 'blur' }
  ],
  confirmPassword: [{ validator: validateResetConfirmPassword, trigger: 'blur' }]
}

const resolveApiErrorMessage = (err, fallback) => {
  return err?.response?.data?.message || err?.message || fallback
}

const navigateAfterAuth = () => {
  router.replace(redirectPath.value)
}

const toggleTab = () => {
  activeTab.value = activeTab.value === 'login' ? 'register' : 'login'
}

const handleLogin = async () => {
  if (!loginFormRef.value) {
    return
  }

  const valid = await loginFormRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  loginLoading.value = true
  try {
    const user = await reqLogin(loginForm)
    setAuthUser(user)
    ElMessage.success('登录成功')
    navigateAfterAuth()
  } catch (err) {
    ElMessage.error(resolveApiErrorMessage(err, '登录失败，请检查账号和密码'))
  } finally {
    loginLoading.value = false
  }
}

const startSendCodeCountdown = () => {
  sendCodeCountdown.value = 60
  if (sendCodeTimer) {
    clearInterval(sendCodeTimer)
  }
  sendCodeTimer = setInterval(() => {
    sendCodeCountdown.value -= 1
    if (sendCodeCountdown.value <= 0) {
      clearInterval(sendCodeTimer)
      sendCodeTimer = null
      sendCodeCountdown.value = 0
    }
  }, 1000)
}

const startResetCodeCountdown = () => {
  resetCodeCountdown.value = 60
  if (resetCodeTimer) {
    clearInterval(resetCodeTimer)
  }
  resetCodeTimer = setInterval(() => {
    resetCodeCountdown.value -= 1
    if (resetCodeCountdown.value <= 0) {
      clearInterval(resetCodeTimer)
      resetCodeTimer = null
      resetCodeCountdown.value = 0
    }
  }, 1000)
}

const handleSendCode = async () => {
  if (!registerFormRef.value || sendCodeDisabled.value) {
    return
  }

  const validEmail = await registerFormRef.value.validateField('email').then(() => true).catch(() => false)
  if (!validEmail) {
    return
  }

  sendCodeLoading.value = true
  try {
    await reqSendRegisterCode(registerForm.email.trim())
    ElMessage.success('验证码已发送，请查收邮箱')
    startSendCodeCountdown()
  } catch (err) {
    ElMessage.error(resolveApiErrorMessage(err, '验证码发送失败，请稍后再试'))
  } finally {
    sendCodeLoading.value = false
  }
}

const handleSendResetCode = async () => {
  if (!forgotFormRef.value || resetCodeDisabled.value) {
    return
  }

  const validEmail = await forgotFormRef.value.validateField('email').then(() => true).catch(() => false)
  if (!validEmail) {
    return
  }

  resetCodeLoading.value = true
  try {
    await reqSendPasswordResetCode(forgotForm.email.trim())
    ElMessage.success('验证码已发送，请查收邮箱')
    startResetCodeCountdown()
  } catch (err) {
    ElMessage.error(resolveApiErrorMessage(err, '验证码发送失败，请稍后再试'))
  } finally {
    resetCodeLoading.value = false
  }
}

const handleRegister = async () => {
  if (!registerFormRef.value) {
    return
  }

  const valid = await registerFormRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  registerLoading.value = true
  try {
    const user = await reqRegister({
      username: registerForm.username,
      nickname: registerForm.nickname,
      email: registerForm.email.trim(),
      emailCode: registerForm.emailCode,
      password: registerForm.password
    })
    setAuthUser(user)
    ElMessage.success('注册成功，已自动登录')
    navigateAfterAuth()
  } catch (err) {
    ElMessage.error(resolveApiErrorMessage(err, '注册失败，请稍后再试'))
  } finally {
    registerLoading.value = false
  }
}

const handleResetPassword = async () => {
  if (!forgotFormRef.value) {
    return
  }

  const valid = await forgotFormRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  resetPasswordLoading.value = true
  try {
    await reqResetPassword({
      email: forgotForm.email.trim(),
      emailCode: forgotForm.emailCode,
      password: forgotForm.password
    })
    ElMessage.success('密码已重置，请使用新密码登录')
    loginForm.username = ''
    loginForm.password = ''
    activeTab.value = 'login'
  } catch (err) {
    ElMessage.error(resolveApiErrorMessage(err, '密码重置失败，请稍后再试'))
  } finally {
    resetPasswordLoading.value = false
  }
}

onMounted(async () => {
  await initAuthState()
  if (authState.user) {
    navigateAfterAuth()
  }
})

onBeforeUnmount(() => {
  if (sendCodeTimer) {
    clearInterval(sendCodeTimer)
    sendCodeTimer = null
  }
  if (resetCodeTimer) {
    clearInterval(resetCodeTimer)
    resetCodeTimer = null
  }
})
</script>

<style scoped>
.auth-page {
  min-height: calc(100vh - 64px);
  padding: 40px 24px 56px;
  background:
    radial-gradient(circle at top left, rgba(64, 158, 255, 0.12), transparent 34%),
    linear-gradient(180deg, #f6f9fe 0%, #f7f8fa 100%);
}

.auth-shell {
  max-width: 1160px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(360px, 460px);
  gap: 28px;
  align-items: stretch;
}

.auth-intro,
.auth-card {
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 20px 44px rgba(31, 45, 61, 0.08);
}

.auth-intro {
  padding: 40px;
  border: 1px solid rgba(217, 230, 247, 0.9);
}

.intro-badge,
.head-kicker {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding: 0 14px;
  border-radius: 999px;
  background: rgba(64, 158, 255, 0.12);
  color: #2d79c7;
  font-size: 13px;
  font-weight: 700;
}

.auth-intro h1 {
  margin: 22px 0 16px;
  font-size: 44px;
  line-height: 1.18;
  color: #1f2d3d;
}

.auth-intro p {
  margin: 0 0 28px;
  color: #647588;
  line-height: 1.8;
  font-size: 16px;
}

.intro-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 14px;
}

.intro-list li {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 18px 20px;
  border-radius: 18px;
  border: 1px solid rgba(223, 232, 244, 0.9);
  background: rgba(248, 251, 255, 0.88);
}

.intro-list strong {
  color: #1f2d3d;
  font-size: 16px;
}

.intro-list span {
  color: #647588;
  line-height: 1.7;
  font-size: 14px;
}

.auth-card {
  border: none;
  padding: 10px;
}

.card-head {
  padding: 12px 16px 0;
}

.card-head h2 {
  margin: 18px 0 10px;
  font-size: 30px;
  color: #1f2d3d;
}

.card-head p {
  margin: 0;
  color: #6b7c90;
  line-height: 1.7;
  font-size: 15px;
}

.auth-tabs {
  margin-top: 12px;
}

.auth-form {
  padding: 8px 14px 0;
}

.code-row {
  width: 100%;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 128px;
  gap: 10px;
}

.code-btn {
  width: 128px;
}

.form-link-row {
  display: flex;
  justify-content: flex-end;
  margin: -8px 0 8px;
}

.submit-btn {
  width: 100%;
  margin-top: 8px;
  height: 50px;
  border-radius: 999px;
  font-size: 17px;
  font-weight: 700;
}

.switch-tip {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 8px;
  padding: 18px 12px 10px;
  color: #7a8797;
  font-size: 14px;
}

.switch-link {
  border: none;
  background: transparent;
  color: #409eff;
  cursor: pointer;
  font-weight: 700;
}

.switch-link:hover {
  color: #1f83e6;
}

@media (max-width: 960px) {
  .auth-shell {
    grid-template-columns: 1fr;
  }

  .auth-intro {
    order: 2;
  }

  .auth-card {
    order: 1;
  }
}

@media (max-width: 640px) {
  .auth-page {
    padding: 16px 12px 32px;
  }

  .auth-intro,
  .auth-card {
    border-radius: 18px;
  }

  .auth-intro {
    padding: 24px 20px;
  }

  .auth-intro h1 {
    font-size: 32px;
  }

  .card-head h2 {
    font-size: 26px;
  }

  .code-row {
    grid-template-columns: 1fr;
  }

  .code-btn {
    width: 100%;
  }
}
</style>
