import { beforeEach, describe, expect, it, vi } from 'vitest'

const request = vi.fn()

vi.mock('../request', () => ({
  default: request
}))

describe('auth api', () => {
  beforeEach(() => {
    request.mockReset()
  })

  it('sends registration email code through the safe same-origin API path', async () => {
    const { reqSendRegisterCode } = await import('../auth')

    reqSendRegisterCode('user@example.com')

    expect(request).toHaveBeenCalledWith({
      url: '/users/send-code',
      method: 'post',
      data: { email: 'user@example.com' }
    })
  })
})
