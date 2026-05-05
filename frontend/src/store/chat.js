import { reactive } from 'vue'
import { reqStreamChat } from '@/api/chat'

const defaultAssistantMessage = '\u4f60\u597d\uff01\u6211\u662f\u4f60\u7684\u65c5\u884c\u5c0f\u52a9\u624b\u3002\u4f60\u53ef\u4ee5\u95ee\u6211\u9644\u8fd1\u6709\u4ec0\u4e48\u3001\u600e\u4e48\u53bb\u3001\u9002\u5408\u4ec0\u4e48\u4e3b\u9898\u8def\u7ebf\u7b49\u95ee\u9898\u3002'
const defaultTips = [
  '\u5bbd\u7a84\u5df7\u5b50\u6700\u4f73\u62cd\u7167\u70b9\u5728\u54ea\uff1f',
  '\u6625\u7199\u8def\u9644\u8fd1\u6709\u4ec0\u4e48\u503c\u5f97\u901b\u7684\uff1f',
  '\u96e8\u5929\u9002\u5408\u53bb\u54ea\u513f\uff1f',
  '\u5e26\u5c0f\u670b\u53cb\u53bb\u54ea\u91cc\u6bd4\u8f83\u597d\uff1f'
]
const fallbackTips = ['\u6210\u90fd\u6709\u54ea\u4e9b\u5fc5\u5403\u7f8e\u98df\uff1f', '\u6709\u4ec0\u4e48\u9002\u5408\u4e00\u65e5\u6e38\u7684\u8def\u7ebf\uff1f']
const reconnectTips = ['\u91cd\u65b0\u8fde\u63a5']
const storagePrefix = 'trip_chat_state_'
const maxRecentMessageCount = 12
const maxRecentMessageChars = 500
let activeStorageKey = ''

const createDefaultMessages = () => ([
  {
    role: 'assistant',
    content: defaultAssistantMessage,
    meta: null
  }
])

const createDefaultState = () => ({
  messages: createDefaultMessages(),
  currentTips: defaultTips.slice(0, 2),
  currentEvidence: [],
  currentSkillPayload: null,
  loading: false,
  streamTick: 0
})

const chatState = reactive(createDefaultState())

function canUseSessionStorage() {
  return typeof window !== 'undefined' && typeof window.sessionStorage !== 'undefined'
}

function buildStorageKey(user) {
  if (!user) {
    return ''
  }

  const identity = user.id ?? user.userId ?? user.username
  if (!identity) {
    return ''
  }

  return `${storagePrefix}${identity}`
}

function isSkillPayload(candidate) {
  return Boolean(candidate) && typeof candidate === 'object' && !Array.isArray(candidate)
}

function resolveTips(candidate) {
  const source = Array.isArray(candidate) && candidate.length > 0 ? candidate : fallbackTips
  return source.filter(Boolean).slice(0, 2)
}

function resolveEvidence(candidate) {
  return Array.isArray(candidate) ? candidate.filter(Boolean).slice(0, 8) : []
}

function resolveSkillPayload(candidate) {
  return isSkillPayload(candidate) ? candidate : null
}

function resolveActions(skillPayload) {
  const actions = skillPayload?.actions
  return Array.isArray(actions) ? actions.filter(Boolean) : []
}

function normalizeMessageMeta(meta) {
  if (!meta || typeof meta !== 'object') {
    return null
  }
  const skillPayload = resolveSkillPayload(meta.skillPayload)
  return {
    relatedTips: resolveTips(meta.relatedTips),
    evidence: resolveEvidence(meta.evidence),
    skillPayload,
    actions: resolveActions(skillPayload)
  }
}

function normalizeMessage(message) {
  if (!message || typeof message !== 'object') {
    return null
  }
  const role = message.role === 'user' ? 'user' : 'assistant'
  const content = typeof message.content === 'string' ? message.content : ''
  return {
    role,
    content,
    meta: normalizeMessageMeta(message.meta)
  }
}

function buildRecentMessages() {
  return chatState.messages
    .filter(message => message && (message.role === 'user' || message.role === 'assistant'))
    .map(message => ({
      role: message.role,
      content: String(message.content || '').trim().slice(0, maxRecentMessageChars)
    }))
    .filter(message => message.content)
    .slice(-maxRecentMessageCount)
}

function applyChatState(payload = {}) {
  const nextState = createDefaultState()

  if (Array.isArray(payload.messages) && payload.messages.length > 0) {
    nextState.messages = payload.messages.map(normalizeMessage).filter(Boolean)
  }

  if (Array.isArray(payload.currentTips) && payload.currentTips.length > 0) {
    nextState.currentTips = resolveTips(payload.currentTips)
  }

  if (Array.isArray(payload.currentEvidence) && payload.currentEvidence.length > 0) {
    nextState.currentEvidence = resolveEvidence(payload.currentEvidence)
  }

  if (isSkillPayload(payload.currentSkillPayload)) {
    nextState.currentSkillPayload = payload.currentSkillPayload
  }

  chatState.messages = nextState.messages
  chatState.currentTips = nextState.currentTips
  chatState.currentEvidence = nextState.currentEvidence
  chatState.currentSkillPayload = nextState.currentSkillPayload
  chatState.loading = false
  chatState.streamTick = 0
}

function persistChatState() {
  if (!activeStorageKey || !canUseSessionStorage()) {
    return
  }

  try {
    window.sessionStorage.setItem(activeStorageKey, JSON.stringify({
      messages: chatState.messages,
      currentTips: chatState.currentTips,
      currentEvidence: chatState.currentEvidence,
      currentSkillPayload: chatState.currentSkillPayload
    }))
  } catch (err) {
  }
}

function touchStream() {
  chatState.streamTick += 1
}

function buildChatErrorMessage(err) {
  const raw = err?.response?.data?.message || err?.message || ''
  const message = typeof raw === 'string' ? raw.trim() : ''
  if (
    message.includes('Model request failed') ||
    message.includes('OpenAI message content is empty') ||
    message.includes('endpoint=') ||
    message.includes('OPENAI_')
  ) {
    return '刚才模型没有返回有效内容。你可以直接换个说法继续，我会按当前路线帮你改。'
  }
  return message || '暂时无法连接到聊天服务，请稍后再试。'
}

function buildAssistantMeta({ relatedTips, evidence, skillPayload }) {
  return normalizeMessageMeta({ relatedTips, evidence, skillPayload })
}

function applyAssistantMeta(message, meta) {
  if (!message) {
    return
  }
  message.meta = normalizeMessageMeta(meta)
}

function normalizeAction(action) {
  if (!action || typeof action !== 'object') {
    return null
  }
  const type = typeof action.type === 'string' ? action.type.trim() : ''
  if (!type) {
    return null
  }
  return {
    ...action,
    type
  }
}

export function useChatState() {
  return chatState
}

export function restoreChatState(user) {
  activeStorageKey = buildStorageKey(user)

  if (!activeStorageKey || !canUseSessionStorage()) {
    applyChatState()
    return
  }

  try {
    const raw = window.sessionStorage.getItem(activeStorageKey)
    if (raw) {
      applyChatState(JSON.parse(raw))
      return
    }
  } catch (err) {
  }

  applyChatState()
}

export function clearActiveChatState() {
  activeStorageKey = ''
  applyChatState()
}

export function resetChatState() {
  applyChatState()
  persistChatState()
}

export function appendAssistantMessage(content, meta = null) {
  const text = typeof content === 'string' ? content.trim() : ''
  if (!text) {
    return
  }
  chatState.messages.push({
    role: 'assistant',
    content: text,
    meta: normalizeMessageMeta(meta)
  })
  touchStream()
  persistChatState()
}

export async function askChatQuestion(question, context, action = null) {
  const value = typeof question === 'string' ? question.trim() : ''
  const normalizedAction = normalizeAction(action)
  if ((!value && !normalizedAction) || chatState.loading) {
    return false
  }

  if (value) {
    chatState.messages.push({ role: 'user', content: value, meta: null })
  }
  chatState.currentTips = []
  chatState.currentEvidence = []
  chatState.currentSkillPayload = null
  chatState.loading = true
  touchStream()
  persistChatState()

  try {
    let assistantMessage = null
    let pendingMeta = null

    const result = await reqStreamChat(
      {
        question: value,
        context,
        recentMessages: buildRecentMessages(),
        ...(normalizedAction ? { action: normalizedAction } : {})
      },
      {
        onToken: (token) => {
          if (!token) {
            return
          }

          if (!assistantMessage) {
            assistantMessage = {
              role: 'assistant',
              content: '',
              meta: null
            }
            chatState.messages.push(assistantMessage)
          }

          assistantMessage.content += token
          if (pendingMeta) {
            applyAssistantMeta(assistantMessage, pendingMeta)
          }
          touchStream()
        },
        onMeta: ({ relatedTips, evidence, skillPayload }) => {
          chatState.currentTips = resolveTips(relatedTips)
          chatState.currentEvidence = resolveEvidence(evidence)
          chatState.currentSkillPayload = resolveSkillPayload(skillPayload)
          pendingMeta = buildAssistantMeta({ relatedTips, evidence, skillPayload })
          if (assistantMessage) {
            applyAssistantMeta(assistantMessage, pendingMeta)
          }
          touchStream()
        }
      }
    )

    if (!assistantMessage) {
      assistantMessage = {
        role: 'assistant',
        content: result.answer || '',
        meta: pendingMeta || buildAssistantMeta(result)
      }
      chatState.messages.push(assistantMessage)
    } else if (!assistantMessage.content.trim() && result.answer) {
      assistantMessage.content = result.answer
    }

    if (!assistantMessage.content.trim()) {
      assistantMessage.content = '\u6682\u65f6\u6ca1\u6709\u751f\u6210\u6709\u6548\u56de\u590d\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5\u3002'
    }

    if (!assistantMessage.meta) {
      assistantMessage.meta = buildAssistantMeta(result)
    }

    if (!chatState.currentTips.length) {
      chatState.currentTips = resolveTips(result.relatedTips)
    }
    if (!chatState.currentEvidence.length) {
      chatState.currentEvidence = resolveEvidence(result.evidence)
    }
    const finalSkillPayload = resolveSkillPayload(result.skillPayload)
    if (finalSkillPayload) {
      chatState.currentSkillPayload = finalSkillPayload
      if (!assistantMessage.meta) {
        assistantMessage.meta = buildAssistantMeta(result)
      }
    }

    touchStream()
    persistChatState()
    return true
  } catch (err) {
    if (!err || err.code !== 401) {
      chatState.messages.push({
        role: 'assistant',
        content: buildChatErrorMessage(err),
        meta: null
      })
      chatState.currentTips = reconnectTips.slice(0, 2)
      chatState.currentEvidence = []
      chatState.currentSkillPayload = null
      touchStream()
      persistChatState()
    }
    throw err
  } finally {
    chatState.loading = false
    touchStream()
    persistChatState()
  }
}
