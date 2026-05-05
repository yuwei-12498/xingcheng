import { describe, expect, it } from 'vitest'
import {
  buildRouteSignature,
  resolveActiveNodes,
  resolveActiveOption,
  resolveOptions,
  sanitizeDisplayText
} from '../resultOptions'

describe('result option normalization', () => {
  it('builds stable route signatures from poi ids', () => {
    expect(buildRouteSignature([{ poiId: 1 }, { poiId: null }, { poiId: 3 }])).toBe('1-3')
  })

  it('falls back to a single default option when backend options are absent', () => {
    const snapshot = {
      customTitle: 'My route',
      totalDuration: 120,
      nodes: [{ poiId: 1, travelTime: 10 }, { poiId: 2, travelTime: 15 }]
    }

    const options = resolveOptions(snapshot)

    expect(options).toHaveLength(1)
    expect(options[0].optionKey).toBe('default')
    expect(options[0].signature).toBe('1-2')
    expect(resolveActiveOption(snapshot).optionKey).toBe('default')
    expect(resolveActiveNodes(snapshot)).toEqual(snapshot.nodes)
  })

  it('sanitizes placeholder text before rendering user-facing labels', () => {
    expect(sanitizeDisplayText('CURRENT_LOCATION', '当前位置')).toBe('当前位置')
    expect(sanitizeDisplayText('  宽窄巷子  ', 'fallback')).toBe('宽窄巷子')
  })
})
