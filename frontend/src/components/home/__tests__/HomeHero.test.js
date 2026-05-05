import { describe, expect, it } from 'vitest'
import homeHeroSource from '../HomeHero.vue?raw'

describe('HomeHero planning preview', () => {
  it('uses real planning capability copy instead of mock-model visual placeholders', () => {
    expect(homeHeroSource).toContain('route-preview-card')
    expect(homeHeroSource).toContain('真实路网 + 算法 + AI Critic')
    expect(homeHeroSource).not.toContain('mock-route-card')
    expect(homeHeroSource).not.toContain('视觉化模拟行程卡片')
    expect(homeHeroSource).not.toContain('核心假窗体')
  })
})
