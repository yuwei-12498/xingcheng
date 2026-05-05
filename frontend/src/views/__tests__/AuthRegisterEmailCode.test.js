import { describe, expect, it } from 'vitest'
import authSource from '../Auth.vue?raw'

describe('Auth register email verification UI', () => {
  it('requires email verification code and provides a 60 second send-code cooldown', () => {
    expect(authSource).toContain('reqSendRegisterCode')
    expect(authSource).toContain('registerForm.email')
    expect(authSource).toContain('registerForm.emailCode')
    expect(authSource).toContain('handleSendCode')
    expect(authSource).toContain('sendCodeCountdown')
    expect(authSource).toContain('sendCodeDisabled')
    expect(authSource).toContain('60')
    expect(authSource).toContain('emailCode: registerForm.emailCode')
  })
})
