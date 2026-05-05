import { describe, expect, it } from 'vitest'
import authSource from '../Auth.vue?raw'
import authApiSource from '../../api/auth.js?raw'

describe('Auth password reset UI', () => {
  it('exposes forgot password flow backed by email verification APIs', () => {
    expect(authSource).toContain('找回密码')
    expect(authSource).toContain('forgotForm.email')
    expect(authSource).toContain('handleSendResetCode')
    expect(authSource).toContain('handleResetPassword')
    expect(authSource).toContain('reqSendPasswordResetCode')
    expect(authSource).toContain('reqResetPassword')
    expect(authApiSource).toContain('/auth/password/send-code')
    expect(authApiSource).toContain('/auth/password/reset')
  })
})
