import { describe, expect, it } from 'vitest'
import {
  COMMUNITY_COVER_PRESETS,
  pickCommunityCoverPreset,
  resolveCommunityCover
} from '../communityCover'

describe('community cover helpers', () => {
  it('keeps uploaded or selected cover image', () => {
    expect(resolveCommunityCover('data:image/png;base64,abc', 1)).toBe('data:image/png;base64,abc')
  })

  it('migrates old preset cover paths to cache-busting v2 paths', () => {
    expect(resolveCommunityCover('/community-covers/cover-food.svg', 1)).toBe('/community-covers/v2/cover-food.svg')
  })

  it('picks a stable preset when cover image is empty', () => {
    const first = resolveCommunityCover('', 'route-86')
    const second = resolveCommunityCover(null, 'route-86')

    expect(COMMUNITY_COVER_PRESETS).toContain(first)
    expect(second).toBe(first)
  })

  it('migrates legacy default cover to a text-free preset', () => {
    const resolved = resolveCommunityCover('/community-cover.svg', 'route-legacy')

    expect(COMMUNITY_COVER_PRESETS).toContain(resolved)
    expect(resolved).not.toBe('/community-cover.svg')
  })

  it('returns different preset candidates for different seeds', () => {
    expect(pickCommunityCoverPreset('青城山')).toMatch(/^\/community-covers\/v2\/cover-.+\.svg$/)
    expect(pickCommunityCoverPreset('成都动物园')).toMatch(/^\/community-covers\/v2\/cover-.+\.svg$/)
  })
})
