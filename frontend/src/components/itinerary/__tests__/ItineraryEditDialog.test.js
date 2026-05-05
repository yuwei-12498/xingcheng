import { describe, expect, it } from 'vitest'
import source from '../ItineraryEditDialog.vue?raw'

describe('ItineraryEditDialog source', () => {
  it('supports day window editing, node editing, custom poi insertion and version restore', () => {
    expect(source).toContain('每日时间')
    expect(source).toContain('现有节点')
    expect(source).toContain('新增地点')
    expect(source).toContain('版本回退')
    expect(source).toContain('reqApplyItineraryEdits')
    expect(source).toContain('reqRestoreItineraryEditVersion')
    expect(source).toContain('reqListCustomPois')
    expect(source).toContain('buildItineraryEditPayload')
  })
})
