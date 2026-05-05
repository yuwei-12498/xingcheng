import { describe, expect, it } from 'vitest'
import homeCoreSectionSource from '../HomeCoreSection.vue?raw'

describe('HomeCoreSection smart fill', () => {
  it('uses backend AI smart-fill API instead of local rule-based parser', () => {
    expect(homeCoreSectionSource).toContain('reqSmartFill')
    expect(homeCoreSectionSource).not.toContain('const parseSmartInput =')
    expect(homeCoreSectionSource).toContain('mustVisitPoiNames')
  })
})
