import { reactive } from 'vue'
import { reqCurrentUser, reqLogout } from '@/api/auth'
import { clearActiveChatState, restoreChatState } from '@/store/chat'

const authState = reactive({
  user: null,
  initialized: false,
  loading: false
})

let initPromise = null

export function useAuthState() {
  return authState
}

export async function initAuthState() {
  if (authState.initialized) {
    return authState.user
  }

  if (authState.loading && initPromise) {
    return initPromise
  }

  authState.loading = true
  initPromise = (async () => {
    try {
      const token = localStorage.getItem('jwt_token')
      if (!token) {
        authState.user = null
        clearActiveChatState()
        return null
      }

      const user = await reqCurrentUser()
      authState.user = user
      if (user?.token) {
        localStorage.setItem('jwt_token', user.token)
      }
      restoreChatState(user)
      return user
    } catch (err) {
      authState.user = null
      localStorage.removeItem('jwt_token')
      clearActiveChatState()
      return null
    } finally {
      authState.loading = false
      authState.initialized = true
      initPromise = null
    }
  })()

  return initPromise
}

export function setAuthUser(user) {
  authState.user = user
  if (user && user.token) {
    localStorage.setItem('jwt_token', user.token)
  }
  authState.initialized = true
  authState.loading = false
  initPromise = null
  restoreChatState(user)
}

export async function clearAuthUser() {
  let success = true
  try {
    await reqLogout()
  } catch (err) {
    success = false
  } finally {
    authState.user = null
    localStorage.removeItem('jwt_token')
    authState.initialized = true
    authState.loading = false
    initPromise = null
    clearActiveChatState()
  }
  return success
}
