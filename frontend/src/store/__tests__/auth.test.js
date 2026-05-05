import { beforeEach, describe, expect, it, vi } from 'vitest'

const loadAuthStore = async () => {
  vi.resetModules()

  const reqCurrentUser = vi.fn()
  const reqLogout = vi.fn()
  const clearActiveChatState = vi.fn()
  const restoreChatState = vi.fn()

  vi.doMock('@/api/auth', () => ({
    reqCurrentUser,
    reqLogout
  }))

  vi.doMock('@/store/chat', () => ({
    clearActiveChatState,
    restoreChatState
  }))

  const authModule = await import('../auth')
  return {
    ...authModule,
    reqCurrentUser,
    reqLogout,
    clearActiveChatState,
    restoreChatState
  }
}

describe('auth store initialization', () => {
  beforeEach(() => {
    window.localStorage.clear()
    vi.clearAllMocks()
    vi.doUnmock('@/api/auth')
    vi.doUnmock('@/store/chat')
  })

  it('shares an in-flight current-user request so route guards wait for the admin role', async () => {
    const {
      initAuthState,
      reqCurrentUser,
      useAuthState
    } = await loadAuthStore()

    window.localStorage.setItem('jwt_token', 'admin-token')

    let resolveCurrentUser
    reqCurrentUser.mockImplementation(() => new Promise(resolve => {
      resolveCurrentUser = resolve
    }))

    const firstInit = initAuthState()
    const secondInit = initAuthState()

    expect(reqCurrentUser).toHaveBeenCalledTimes(1)

    let secondSettled = false
    secondInit.then(() => {
      secondSettled = true
    })
    await Promise.resolve()

    expect(secondSettled).toBe(false)

    resolveCurrentUser({
      id: 1,
      username: 'admin',
      nickname: '系统管理员',
      role: 1,
      token: 'fresh-admin-token'
    })

    await expect(firstInit).resolves.toMatchObject({ role: 1 })
    await expect(secondInit).resolves.toMatchObject({ role: 1 })
    expect(useAuthState().user).toMatchObject({ username: 'admin', role: 1 })
    expect(window.localStorage.getItem('jwt_token')).toBe('fresh-admin-token')
  })
})
