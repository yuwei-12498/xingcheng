<template>
  <div class="home-ai-panel" @wheel.stop.prevent="handleWheelScroll">
    <div class="panel-header">
      <div class="ai-avatar">AI</div>
      <div class="ai-title-wrap">
        <h3 class="panel-title">行程助手</h3>
        <span class="panel-subtitle">
          {{ authState.user ? '试试问我附近酒店、景点推荐或路线调整' : '登录后即可启用智能问答与路线建议' }}
        </span>
      </div>
    </div>

    <div class="panel-body" ref="chatBodyRef">
      <div class="msg-list">
        <template v-for="(msg, index) in chatState.messages" :key="index">
          <div class="msg-block" :class="msg.role === 'user' ? 'msg-user-block' : 'msg-ai-block'">
            <div class="msg-item" :class="msg.role === 'user' ? 'msg-user' : 'msg-ai'">
              <div class="bubble">{{ msg.content }}</div>
            </div>
            <div v-if="msg.role === 'assistant' && msg.meta?.actions?.length" class="message-actions">
              <el-button
                v-for="(action, actionIndex) in msg.meta.actions"
                :key="`${index}-action-${action.key || actionIndex}`"
                size="small"
                round
                class="action-btn"
                :type="resolveActionButtonType(action.style)"
                :plain="action.style !== 'primary'"
                :loading="pendingActionKey === buildActionKey(action, index)"
                @click="handleMessageAction(msg, action, index)"
              >
                {{ action.label }}
              </el-button>
            </div>
          </div>
        </template>

        <div v-if="chatState.loading" class="msg-item msg-ai">
          <div class="bubble loading-dots">正在思考<span>.</span><span>.</span><span>.</span></div>
        </div>
      </div>
    </div>

    <div v-if="visibleSkillResults.length > 0" class="skill-results">
      <p class="tips-title">为你找到</p>
      <div
        v-for="(item, idx) in visibleSkillResults"
        :key="`${item.name || 'skill-result'}-${idx}`"
        class="skill-result-card"
      >
        <div class="skill-result-card__name">{{ item.name || '未命名地点' }}</div>
        <div class="skill-result-card__meta">
          {{ item.category || '未知分类' }}
          <span v-if="item.address"> · {{ item.address }}</span>
        </div>
      </div>
    </div>

    <div v-if="visibleTips.length > 0" class="panel-quick-tips">
      <p class="tips-title">快捷提问</p>
      <div class="tips-container">
        <el-tag
          v-for="(tip, idx) in visibleTips"
          :key="idx"
          class="tip-tag"
          size="small"
          effect="light"
          @click="sendQuestion(tip)"
        >
          {{ tip }}
        </el-tag>
      </div>
    </div>

    <div v-if="authState.user" class="panel-footer">
      <el-input
        v-model="inputVal"
        placeholder="输入你想了解的地点、酒店或路线"
        class="chat-input"
        clearable
        @keyup.enter="handleSend"
      >
        <template #append>
          <el-button type="primary" :disabled="!inputVal.trim() || chatState.loading" @click="handleSend">发送</el-button>
        </template>
      </el-input>
    </div>

    <div v-else class="panel-footer login-footer">
      <p class="login-copy">登录后可使用 AI 行程问答、附近推荐和路线建议。</p>
      <el-button type="primary" round class="login-btn" @click="goLogin">去登录</el-button>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthState } from '@/store/auth'
import { askChatQuestion, appendAssistantMessage, resetChatState, useChatState } from '@/store/chat'
import { buildSharedChatContext } from '@/utils/chatContext'
import { persistDepartureLocation, questionNeedsLocation, resolveCurrentLocation } from '@/utils/location'
import { handleChatWorkflowAction } from '@/utils/chatWorkflowActions'

const props = defineProps({
  currentForm: {
    type: Object,
    default: () => ({})
  }
})

const route = useRoute()
const router = useRouter()
const authState = useAuthState()
const chatState = useChatState()
const chatBodyRef = ref(null)
const inputVal = ref('')
const pendingActionKey = ref('')

const visibleTips = computed(() => chatState.currentTips.slice(0, 2))
const visibleSkillResults = computed(() => {
  const results = chatState.currentSkillPayload?.results
  return Array.isArray(results) ? results.slice(0, 3) : []
})

const goLogin = () => {
  router.push({
    path: '/auth',
    query: {
      redirect: route.fullPath
    }
  })
}

const ensureLogin = () => {
  if (authState.user) {
    return true
  }
  ElMessage.warning('请先登录后再使用 AI 行程助手')
  goLogin()
  return false
}

const buildContext = () => buildSharedChatContext({
  pageType: 'home',
  currentForm: props.currentForm,
  includeItinerary: false,
  includeStoredForm: false
})

const buildLocationEnhancedContext = (context, location) => {
  if (!location) {
    return context
  }
  return {
    ...context,
    userLat: Number(location.latitude),
    userLng: Number(location.longitude),
    originalReq: {
      ...(context?.originalReq || {}),
      departureLatitude: Number(location.latitude),
      departureLongitude: Number(location.longitude),
      departurePlaceName: context?.originalReq?.departurePlaceName || 'CURRENT_LOCATION'
    }
  }
}

const ensureLocationAwareContext = async (question) => {
  const context = buildContext()
  if (Number.isFinite(Number(context?.userLat)) && Number.isFinite(Number(context?.userLng))) {
    return context
  }
  if (!questionNeedsLocation(question)) {
    return context
  }

  const location = await resolveCurrentLocation()
  if (!location) {
    ElMessage.info('未获取到定位权限，附近推荐和距离判断可能不准确')
    return context
  }

  persistDepartureLocation(location, context?.originalReq?.departurePlaceName || 'CURRENT_LOCATION')
  return buildLocationEnhancedContext(buildContext(), location)
}

const buildActionKey = (action, index) => `${index}:${action?.key || action?.type || action?.label || 'action'}`

const resolveActionButtonType = style => {
  if (style === 'primary') {
    return 'primary'
  }
  if (style === 'danger') {
    return 'danger'
  }
  return 'default'
}

const sendQuestion = question => {
  if (!ensureLogin()) return
  inputVal.value = question
  handleSend()
}

const handleSend = async () => {
  if (!ensureLogin()) return

  const question = inputVal.value.trim()
  if (!question || chatState.loading) return

  inputVal.value = ''

  try {
    const context = await ensureLocationAwareContext(question)
    await askChatQuestion(question, context)
  } catch (err) {
    if (err?.code === 401) {
      ElMessage.warning('登录状态已失效，请重新登录')
      goLogin()
    }
  } finally {
    scrollToBottom()
  }
}

const handleMessageAction = async (message, action, index) => {
  if (!ensureLogin()) return

  const actionKey = buildActionKey(action, index)
  pendingActionKey.value = actionKey
  try {
    const handled = await handleChatWorkflowAction({
      action,
      message,
      buildContext
    })
    if (!handled) {
      appendAssistantMessage('这个操作暂时还不支持，你可以换个说法继续告诉我。')
    }
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '操作失败，请稍后重试')
  } finally {
    pendingActionKey.value = ''
    scrollToBottom()
  }
}

const scrollToBottom = () => {
  nextTick(() => {
    if (chatBodyRef.value) {
      chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight
    }
  })
}

const handleWheelScroll = event => {
  const container = chatBodyRef.value
  if (!container) return

  const maxScrollTop = container.scrollHeight - container.clientHeight
  if (maxScrollTop <= 0) return

  const nextScrollTop = Math.max(0, Math.min(maxScrollTop, container.scrollTop + event.deltaY))
  container.scrollTop = nextScrollTop
}

watch(() => chatState.messages.length, () => {
  scrollToBottom()
})

watch(() => chatState.streamTick, () => {
  scrollToBottom()
})

watch(() => chatState.loading, () => {
  scrollToBottom()
})

watch(() => authState.user, () => {
  resetChatState()
}, {
  immediate: true
})
</script>

<style scoped>
.home-ai-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: linear-gradient(to bottom, #f8faff, #ffffff);
  border-radius: 18px;
  border: 1px solid #e4e7ed;
  overflow: hidden;
  box-shadow: 0 16px 32px rgba(31, 45, 61, 0.06);
}

.panel-header {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  background: #ffffff;
  border-bottom: 1px solid #ebeef5;
}

.ai-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, #409eff, #66b1ff);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 16px;
  margin-right: 12px;
  box-shadow: 0 4px 10px rgba(64, 158, 255, 0.3);
}

.ai-title-wrap {
  display: flex;
  flex-direction: column;
}

.panel-title {
  margin: 0;
  font-size: 15px;
  color: #303133;
  font-weight: 600;
}

.panel-subtitle {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
}

.panel-body {
  flex: 1;
  min-height: 0;
  padding: 12px 14px;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}

.msg-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.msg-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.msg-item {
  display: flex;
  max-width: 85%;
}

.msg-user-block {
  align-items: flex-end;
}

.msg-ai-block {
  align-items: flex-start;
}

.msg-user {
  align-self: flex-end;
  justify-content: flex-end;
}

.msg-ai {
  align-self: flex-start;
  justify-content: flex-start;
}

.bubble {
  padding: 8px 12px;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.55;
  word-break: break-word;
}

.msg-user .bubble {
  background-color: #409eff;
  color: white;
  border-bottom-right-radius: 4px;
}

.msg-ai .bubble {
  background-color: #ffffff;
  color: #303133;
  border: 1px solid #ebeef5;
  border-bottom-left-radius: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
}

.message-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.action-btn {
  min-height: 30px;
}

.skill-results,
.panel-quick-tips {
  padding: 8px 14px;
  background: #fafbfc;
  border-top: 1px solid #ebeef5;
}

.skill-result-card + .skill-result-card {
  margin-top: 8px;
}

.skill-result-card__name {
  font-size: 13px;
  font-weight: 600;
  color: #1f2d3d;
}

.skill-result-card__meta {
  margin-top: 2px;
  font-size: 12px;
  color: #7c8da5;
}

.tips-title {
  margin: 0 0 6px;
  font-size: 11px;
  color: #909399;
}

.tips-container {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tip-tag {
  cursor: pointer;
  border-radius: 14px;
  padding: 0 12px;
  border-color: #d9ecff;
  color: #409eff;
  transition: all 0.2s;
}

.tip-tag:hover {
  background-color: #409eff;
  color: #ffffff;
}

.panel-footer {
  padding: 10px 14px;
  background: #ffffff;
  border-top: 1px solid #ebeef5;
}

.login-footer {
  display: flex;
  flex-direction: column;
  gap: 14px;
  align-items: flex-start;
}

.login-copy {
  margin: 0;
  color: #6a7a8d;
  line-height: 1.6;
  font-size: 13px;
}

.login-btn {
  box-shadow: 0 8px 18px rgba(64, 158, 255, 0.16);
}

@media (max-width: 991px) {
  .home-ai-panel {
    min-height: 420px;
  }
}

.loading-dots span {
  animation: typing 1.4s infinite ease-in-out both;
}

.loading-dots span:nth-child(1) {
  animation-delay: -0.32s;
}

.loading-dots span:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes typing {
  0%,
  80%,
  100% {
    transform: scale(0);
    opacity: 0;
  }

  40% {
    transform: scale(1);
    opacity: 1;
  }
}
</style>
