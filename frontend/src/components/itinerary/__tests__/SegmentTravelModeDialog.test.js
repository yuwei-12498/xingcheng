import { describe, expect, it } from 'vitest'
import dialogSource from '../SegmentTravelModeDialog.vue?raw'

describe('SegmentTravelModeDialog', () => {
  it('renders four fixed mode options and keeps confirm disabled until a mode is chosen', () => {
    expect(dialogSource).toContain("{ code: 'walk', label: '步行' }")
    expect(dialogSource).toContain("{ code: 'bike', label: '骑行' }")
    expect(dialogSource).toContain("{ code: 'transit', label: '公交' }")
    expect(dialogSource).toContain("{ code: 'taxi', label: '打车' }")
    expect(dialogSource).toContain('按所选方式重算')
    expect(dialogSource).toContain(':disabled="!selectedMode"')
  })

  it('emits select, confirm and cancel events for the current one-off segment selection', () => {
    expect(dialogSource).toContain("emit('select', item.code)")
    expect(dialogSource).toContain("emit('confirm')")
    expect(dialogSource).toContain("emit('cancel')")
    expect(dialogSource).toContain('手动选择只影响当前这一段、当前这一次。')
  })
})
