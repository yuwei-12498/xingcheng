<template>
  <div v-if="showWidget" class="chat-widget">
    <div v-if="!isOpen" class="chat-fab" @click="toggleChat">
      <el-icon :size="24" color="#fff"><Service /></el-icon>
      <span class="fab-text">行程助手</span>
    </div>

    <transition name="chat-slide">
      <div v-if="isOpen" class="chat-panel" @wheel.stop.prevent="handleWheelScroll">
        <div class="chat-header">
          <div class="header-info">
            <el-icon :size="20"><LocationInformation /></el-icon>
            <span class="chat-title">行程助手</span>
          </div>
          <el-icon class="close-icon" @click="toggleChat"><Close /></el-icon>
        </div>

        <div class="chat-body" ref="chatBodyRef">
          <div class="msg-container">
            <template v-for="(msg, index) in chatState.messages" :key="index">
              <div v-if="msg.role === 'assistant'" class="msg-block msg-left">
                <div class="msg-row">
                  <div class="avatar bg-blue">AI</div>
                  <div class="msg-bubble assistant-bubble">{{ msg.content }}</div>
                </div>
                <div v-if="msg.meta?.actions?.length" class="message-actions">
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

              <div v-else class="msg-row msg-right">
                <div class="msg-bubble user-bubble">{{ msg.content }}</div>
                <div class="avatar bg-gray">我</div>
              </div>
            </template>

            <div v-if="chatState.loading" class="msg-row msg-left">
              <div class="avatar bg-blue">AI</div>
              <div class="msg-bubble assistant-bubble loading-dots">正在思考<span>.</span><span>.</span><span>.</span></div>
            </div>
          </div>
        </div>

        <div v-if="visibleSkillResults.length > 0" class="skill-results">
          <div class="skill-results__title">
            为你找到
          </div>
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
            <div v-if="hasUsableDistance(item.distanceMeters)" class="skill-result-card__meta">
              距离 {{ Math.round(Number(item.distanceMeters)) }} 米
            </div>
          </div>
        </div>

        <div v-if="visibleTips.length > 0" class="quick-tips">
          <el-tag
            v-for="(tip, idx) in visibleTips"
            :key="idx"
            size="small"
            class="tip-tag"
            effect="plain"
            @click="sendQuestion(tip)"
          >
            {{ tip }}
          </el-tag>
        </div>

        <div class="chat-footer">
          <el-input
            v-model="inputVal"
            placeholder="输入你想了解的地点、酒店或路线..."
            class="chat-input"
            @keyup.enter="handleSend"
          >
            <template #append>
              <el-button
                icon="Position"
                type="primary"
                :disabled="!inputVal.trim() || chatState.loading"
                @click="handleSend"
              />
            </template>
          </el-input>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthState } from '@/store/auth'
import { askChatQuestion, appendAssistantMessage, useChatState } from '@/store/chat'
import { buildSharedChatContext } from '@/utils/chatContext'
import { persistDepartureLocation, questionNeedsLocation, resolveCurrentLocation } from '@/utils/location'
import { handleChatWorkflowAction } from '@/utils/chatWorkflowActions'

const route = useRoute()
const router = useRouter()
const authState = useAuthState()
const chatState = useChatState()
const showWidget = computed(() => route.path !== '/' && !route.meta.hideGlobalChat)
const isOpen = ref(false)
const inputVal = ref('')
const chatBodyRef = ref(null)
const pendingActionKey = ref('')

const visibleTips = computed(() => chatState.currentTips.slice(0, 2))
const visibleSkillResults = computed(() => {
  const results = chatState.currentSkillPayload?.results
  if (!Array.isArray(results)) {
    return []
  }
  const payload = chatState.currentSkillPayload || {}
  const isRouteContext = payload.skillName === 'route_context'
    || payload.intent === 'route_context'
    || results.some(item => item?.source === 'itinerary-route')
  return isRouteContext ? results.slice(0, 12) : results.slice(0, 3)
})

const hasUsableDistance = distanceMeters => {
  if (distanceMeters === null || distanceMeters === undefined || distanceMeters === '') {
    return false
  }
  return Number.isFinite(Number(distanceMeters))
}

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
  pageType: route.name ? String(route.name).toLowerCase() : 'page'
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

const toggleChat = () => {
  if (!authState.user) {
    ensureLogin()
    return
  }
  isOpen.value = !isOpen.value
  if (isOpen.value) {
    scrollToBottom()
  }
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
</script>

<style scoped>
.chat-widget {
  position: fixed;
  right: 32px;
  bottom: 32px;
  z-index: 1000;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.chat-fab {
  background: linear-gradient(135deg, #409eff, #3a8ee6);
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.4);
  color: white;
  height: 54px;
  border-radius: 27px;
  display: flex;
  align-items: center;
  padding: 0 20px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.chat-fab:hover {
  transform: translateY(-3px);
  box-shadow: 0 6px 20px rgba(64, 158, 255, 0.6);
}

.fab-text {
  margin-left: 8px;
  font-weight: 600;
  font-size: 15px;
}

.chat-panel {
  width: 380px;
  height: 550px;
  background: #ffffff;
  border-radius: 16px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid #ebeef5;
}

.chat-header {
  height: 56px;
  background: #f7f8fa;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
}

.header-info {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #1f2d3d;
}

.chat-title {
  font-weight: 600;
  font-size: 15px;
}

.close-icon {
  cursor: pointer;
  font-size: 20px;
  color: #909399;
  transition: color 0.2s;
}

.close-icon:hover {
  color: #f56c6c;
}

.chat-body {
  flex: 1;
  min-height: 0;
  padding: 16px;
  overflow-y: auto;
  background: #fdfdfd;
  overscroll-behavior: contain;
}

.msg-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.msg-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.msg-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.msg-left {
  justify-content: flex-start;
}

.msg-right {
  justify-content: flex-end;
}

.avatar {
  min-width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: bold;
  color: white;
}

.bg-blue {
  background: #409eff;
}

.bg-gray {
  background: #e6a23c;
}

.msg-bubble {
  max-width: 240px;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.5;
  word-wrap: break-word;
}

.assistant-bubble {
  background: #f4f4f5;
  color: #303133;
  border-top-left-radius: 2px;
}

.user-bubble {
  background: #409eff;
  color: #ffffff;
  border-top-right-radius: 2px;
}

.message-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding-left: 46px;
}

.action-btn {
  min-height: 30px;
}

.quick-tips {
  padding: 10px 16px;
  background: #fdfdfd;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  border-top: 1px solid #f0f2f5;
}

.tip-tag {
  cursor: pointer;
  border-radius: 12px;
  transition: all 0.2s;
}

.tip-tag:hover {
  background: #ecf5ff;
  border-color: #b3d8ff;
}

.skill-results {
  margin: 0 16px 10px;
  padding: 10px 12px;
  border-radius: 12px;
  background: #f6f8fb;
  border: 1px solid #e6edf7;
}

.skill-results__title {
  margin-bottom: 8px;
  font-size: 12px;
  color: #607089;
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

.chat-footer {
  padding: 12px 16px;
  background: #ffffff;
  border-top: 1px solid #ebeef5;
}

.chat-slide-enter-active,
.chat-slide-leave-active {
  transition: transform 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275), opacity 0.3s;
}

.chat-slide-enter-from,
.chat-slide-leave-to {
  transform: translateY(20px) scale(0.95);
  opacity: 0;
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

@media (max-width: 768px) {
  .chat-widget {
    right: 12px;
    bottom: 12px;
  }

  .chat-fab {
    height: 48px;
    padding: 0 16px;
    border-radius: 24px;
  }

  .fab-text {
    font-size: 14px;
  }

  .chat-panel {
    position: fixed;
    left: 12px;
    right: 12px;
    bottom: 72px;
    width: auto;
    max-width: none;
    height: min(70vh, 560px);
    border-radius: 20px;
  }

  .chat-header {
    height: 52px;
    padding: 0 14px;
  }

  .chat-body {
    padding: 12px;
  }

  .msg-container {
    gap: 12px;
  }

  .avatar {
    min-width: 30px;
    height: 30px;
    font-size: 12px;
  }

  .msg-row {
    gap: 8px;
  }

  .msg-bubble {
    max-width: calc(100vw - 112px);
    padding: 9px 12px;
    font-size: 13px;
  }

  .message-actions {
    padding-left: 38px;
  }

  .quick-tips {
    padding: 8px 12px;
    overflow-x: auto;
    flex-wrap: nowrap;
  }

  .tip-tag {
    flex: 0 0 auto;
  }

  .skill-results {
    margin: 0 12px 8px;
  }

  .chat-footer {
    padding: 10px 12px;
  }
}
</style>
