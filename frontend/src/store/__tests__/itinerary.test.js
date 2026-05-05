import { describe, expect, it, vi } from 'vitest'
import {
  loadItinerarySnapshot,
  localizeItineraryText,
  normalizeItinerarySnapshot,
  saveItinerarySnapshot
} from '../itinerary'

describe('localizeItineraryText', () => {
  it('removes or translates long english runtime hints', () => {
    const text = 'Data enriched on 2026-04-24; hotspot crowd penalty boosted to reduce homogeneous routes.'
    const localized = localizeItineraryText(text)
    expect(localized).not.toMatch(/[A-Za-z]{4,}/)
    expect(localized).toContain('2026-04-24')
  })

  it('removes multiline english suffix from runtime hint', () => {
    const text = 'Data enriched on\n2026-04-24; hotspot crowd penalty boosted to reduce homogeneous routes.'
    const localized = localizeItineraryText(text)
    expect(localized).not.toMatch(/[A-Za-z]{4,}/)
  })

  it('translates official-source opening-hour reminder to chinese', () => {
    const localized = localizeItineraryText('Data enriched on 2026-04-24; verify opening hours with official source.')
    expect(localized).not.toMatch(/[A-Za-z]{4,}/)
    expect(localized).toContain('2026-04-24')
  })

  it('normalizes option nodes statusNote to chinese', () => {
    const snapshot = normalizeItinerarySnapshot({
      selectedOptionKey: 'A',
      options: [{
        optionKey: 'A',
        nodes: [{
          statusNote: 'Data enriched on 2026-04-24; verify opening hours with official source.'
        }]
      }]
    })
    expect(snapshot.options[0].nodes[0].statusNote).toBeNull()
  })


  it('prefers backend selectedWarmTip and keeps warm tip candidates instead of overwriting with fixed templates', () => {
    const snapshot = normalizeItinerarySnapshot({
      nodes: [{
        poiId: 1,
        poiName: '????',
        category: '??',
        district: '??',
        statusNote: 'Data enriched on 2026-04-24; verify opening hours with official source.',
        selectedWarmTip: '???????????????',
        warmTipCandidates: [
          '???????????????',
          '???????????????'
        ]
      }]
    })

    expect(snapshot.nodes[0].statusNote).toBe('???????????????')
    expect(snapshot.nodes[0].selectedWarmTip).toBe('???????????????')
    expect(snapshot.nodes[0].warmTipCandidates).toEqual([
      '???????????????',
      '???????????????'
    ])
  })

  it('falls back to backend warmTipCandidates when statusNote is generic runtime text', () => {
    vi.spyOn(Math, 'random').mockReturnValue(0)

    const snapshot = normalizeItinerarySnapshot({
      nodes: [{
        poiId: 2,
        poiName: '?????',
        statusNote: 'Data enriched on 2026-04-24; verify opening hours with official source.',
        warmTipCandidates: [
          '????????????????',
          '?????????????????'
        ]
      }]
    })

    expect(snapshot.nodes[0].statusNote).toBe('????????????????')
    expect(snapshot.nodes[0].warmTipCandidates).toEqual([
      '????????????????',
      '?????????????????'
    ])

    vi.restoreAllMocks()
  })

  it('does not synthesize fake warm tips when backend did not return ai candidates', () => {
    const snapshot = normalizeItinerarySnapshot({
      nodes: [{
        poiId: 3,
        poiName: '人民公园',
        statusNote: 'Data enriched on 2026-04-24; verify opening hours with official source.'
      }]
    })

    expect(snapshot.nodes[0].statusNote).toBeNull()
    expect(snapshot.nodes[0].selectedWarmTip).toBeNull()
  })

})

describe('segmentRouteGuide snapshot persistence', () => {
  const createMemoryStorage = () => {
    const store = new Map()
    return {
      getItem: key => (store.has(key) ? store.get(key) : null),
      setItem: (key, value) => store.set(key, String(value)),
      removeItem: key => store.delete(key)
    }
  }

  it('keeps segmentRouteGuide facts when normalizing itinerary snapshots', () => {
    const snapshot = normalizeItinerarySnapshot({
      nodes: [{
        poiId: 1,
        poiName: '宽窄巷子',
        segmentRouteGuide: {
          summary: '步行 300 米 → 地铁 2 站 → 步行 450 米',
          transportMode: '地铁+步行',
          durationMinutes: 27,
          distanceKm: 8.4,
          detailAvailable: true,
          steps: [
            { stepOrder: 1, type: 'walk', instruction: '步行 300 米到天府广场地铁站 B 口', distanceMeters: 300, durationMinutes: 4, pathPoints: [{ latitude: 30.65, longitude: 104.06 }] },
            { stepOrder: 2, type: 'metro', instruction: '乘 1 号线往文殊院方向 2 站', lineName: '1号线', stopCount: 2, pathPoints: [{ latitude: 30.66, longitude: 104.05 }] }
          ],
          pathPoints: [{ latitude: 30.65, longitude: 104.06 }, { latitude: 30.66, longitude: 104.05 }],
          source: 'route-provider'
        }
      }]
    })

    expect(snapshot.nodes[0].segmentRouteGuide.summary).toBe('步行 300 米 → 地铁 2 站 → 步行 450 米')
    expect(snapshot.nodes[0].segmentRouteGuide.steps[1].lineName).toBe('1号线')
    expect(snapshot.nodes[0].segmentRouteGuide.pathPoints).toHaveLength(2)
  })

  it('preserves segmentRouteGuide through snapshot save + reload', () => {
    const previousWindow = globalThis.window
    globalThis.window = {
      localStorage: createMemoryStorage(),
      sessionStorage: createMemoryStorage()
    }

    try {
      const guide = {
        summary: '打车约 14 分钟，约 5.1 公里',
        transportMode: '打车',
        durationMinutes: 14,
        distanceKm: 5.1,
        detailAvailable: true,
        steps: [
          { stepOrder: 1, type: 'taxi', instruction: '上车后前往目的地', pathPoints: [{ latitude: 30.65, longitude: 104.06 }] }
        ],
        pathPoints: [{ latitude: 30.65, longitude: 104.06 }, { latitude: 30.66, longitude: 104.05 }],
        source: 'route-provider'
      }

      saveItinerarySnapshot({
        selectedOptionKey: 'A',
        nodes: [{ poiId: 1, poiName: '宽窄巷子', segmentRouteGuide: guide }]
      })

      const loaded = loadItinerarySnapshot()
      expect(loaded.nodes[0].segmentRouteGuide).toEqual(guide)
      expect(loaded.nodes[0].segmentRouteGuide.steps[0].pathPoints).toHaveLength(1)
      expect(loaded.nodes[0].segmentRouteGuide.pathPoints).toHaveLength(2)
    } finally {
      globalThis.window = previousWindow
    }
  })
})
