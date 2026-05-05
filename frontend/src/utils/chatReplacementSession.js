const CLIENT_SESSION_KEY = 'citytrip/chat-replacement/client-session-id'
const VERSION_STATE_KEY = 'citytrip/chat-replacement/version-state'

const canUseSessionStorage = () => typeof window !== 'undefined' && typeof window.sessionStorage !== 'undefined'

const clone = value => JSON.parse(JSON.stringify(value))

const createToken = prefix => `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`

const readState = () => {
  if (!canUseSessionStorage()) {
    return { itineraryId: null, versions: [], activeVersionIndex: -1 }
  }
  try {
    const raw = window.sessionStorage.getItem(VERSION_STATE_KEY)
    if (!raw) {
      return { itineraryId: null, versions: [], activeVersionIndex: -1 }
    }
    const parsed = JSON.parse(raw)
    return {
      itineraryId: parsed?.itineraryId ?? null,
      versions: Array.isArray(parsed?.versions) ? parsed.versions : [],
      activeVersionIndex: Number.isInteger(parsed?.activeVersionIndex) ? parsed.activeVersionIndex : -1
    }
  } catch (err) {
    return { itineraryId: null, versions: [], activeVersionIndex: -1 }
  }
}

const writeState = state => {
  if (!canUseSessionStorage()) {
    return state
  }
  window.sessionStorage.setItem(VERSION_STATE_KEY, JSON.stringify(state))
  return state
}

export function getOrCreateChatReplacementSessionId() {
  if (!canUseSessionStorage()) {
    return createToken('chat')
  }
  const existing = window.sessionStorage.getItem(CLIENT_SESSION_KEY)
  if (existing) {
    return existing
  }
  const next = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : createToken('chat')
  window.sessionStorage.setItem(CLIENT_SESSION_KEY, next)
  return next
}

export function ensureReplacementVersionState(snapshot) {
  const state = readState()
  if (!snapshot || typeof snapshot !== 'object') {
    return state
  }
  if (!state.versions.length || state.itineraryId !== (snapshot.id ?? null)) {
    return writeState({
      itineraryId: snapshot.id ?? null,
      versions: [{ versionToken: createToken('v1'), snapshot: clone(snapshot) }],
      activeVersionIndex: 0
    })
  }
  return state
}

export function pushReplacementVersion(previousSnapshot, nextSnapshot) {
  const baseSnapshot = previousSnapshot || nextSnapshot
  const state = ensureReplacementVersionState(baseSnapshot)
  const versions = state.versions.slice(0, state.activeVersionIndex + 1)
  const entry = { versionToken: createToken('v'), snapshot: clone(nextSnapshot) }
  versions.push(entry)
  return writeState({
    itineraryId: nextSnapshot?.id ?? state.itineraryId,
    versions,
    activeVersionIndex: versions.length - 1
  })
}

export function getReplacementVersionState() {
  return readState()
}

export function getPreviousReplacementVersion() {
  const state = readState()
  if (state.activeVersionIndex <= 0) {
    return null
  }
  return state.versions[state.activeVersionIndex - 1] || null
}

export function getInitialReplacementVersion() {
  const state = readState()
  return state.versions[0] || null
}

export function markReplacementVersionActive(versionToken) {
  const state = readState()
  const index = state.versions.findIndex(item => item?.versionToken === versionToken)
  if (index < 0) {
    return state
  }
  return writeState({
    ...state,
    activeVersionIndex: index
  })
}

export function clearReplacementVersionState() {
  if (!canUseSessionStorage()) {
    return
  }
  window.sessionStorage.removeItem(VERSION_STATE_KEY)
}
