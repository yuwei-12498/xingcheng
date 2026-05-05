import { describe, expect, it } from 'vitest'
import {
  buildDayPlans,
  buildResultActionGroups,
  buildResultHeroContent,
  buildResultStatItems,
  buildSegmentGuideTitle,
  estimateTotalBudget,
  estimateTransportCost,
  formatNodeTravelLabel,
  formatSegmentGuideSummary,
  formatTravelDistance,
  formatTravelMode
} from '../resultUi'

describe('buildResultStatItems', () => {
  it('builds timing-only summary metrics for the result page', () => {
    const items = buildResultStatItems({
      activeOption: {
        totalDuration: 315,
        totalCost: 128,
        totalTravelTime: 96
      },
      activeNodes: [{ poiId: 1 }, { poiId: 2 }, { poiId: 3 }, { poiId: 4 }]
    })

    expect(items).toEqual([
      { label: '总时长', value: '5 小时 15 分钟', tone: 'primary' },
      { label: '路上耗时', value: '1 小时 36 分钟', tone: 'neutral' },
      { label: '花费预算', value: '¥128', tone: 'neutral' }
    ])
  })

  it('falls back to node travel time when option travel time is missing', () => {
    const items = buildResultStatItems({
      activeOption: {
        totalDuration: 180,
        totalCost: 88
      },
      activeNodes: [
        { poiId: 1, travelTime: 18 },
        { poiId: 2, travelTime: 25 },
        { poiId: 3, travelTime: 17 }
      ]
    })

    expect(items[1]).toEqual({ label: '路上耗时', value: '1 小时', tone: 'neutral' })
    expect(items[2]).toEqual({ label: '花费预算', value: '¥106', tone: 'neutral' })
    expect(items).toHaveLength(3)
  })
})

describe('budget estimation helpers', () => {
  it('estimates transport legs by mode and distance', () => {
    const transportCost = estimateTransportCost([
      {
        stepOrder: 1,
        departureTransportMode: '地铁',
        departureDistanceKm: 8.2,
        departureTravelTime: 26
      },
      {
        stepOrder: 2,
        travelTransportMode: '打车',
        travelDistanceKm: 5.6,
        travelTime: 18
      },
      {
        stepOrder: 3,
        travelTransportMode: '步行',
        travelDistanceKm: 0.8,
        travelTime: 12
      }
    ])

    expect(transportCost).toBeGreaterThan(20)
    expect(transportCost).toBeLessThan(30)
  })

  it('adds transport cost on top of base budget', () => {
    const totalBudget = estimateTotalBudget({
      baseCost: 150,
      activeNodes: [
        { stepOrder: 1, departureTransportMode: '地铁', departureTravelTime: 20 },
        { stepOrder: 2, travelTransportMode: '公交', travelTime: 24 }
      ]
    })

    expect(totalBudget).toBeGreaterThan(150)
  })
})

describe('buildResultActionGroups', () => {
  it('keeps only replan as the primary action on the result page', () => {
    const groups = buildResultActionGroups({
      isLoggedIn: true,
      isPublic: false
    })

    expect(groups.primary).toEqual(['replan'])
    expect(groups.secondary).toEqual(['favorite', 'publish', 'poster'])
    expect(groups.tertiary).toEqual(['home', 'community', 'history'])
  })

  it('keeps both publish control and community entry when a route is already public', () => {
    const groups = buildResultActionGroups({
      isLoggedIn: true,
      isPublic: true
    })

    expect(groups.secondary).toEqual(['favorite', 'communityPost', 'publish', 'poster'])
  })

  it('hides history for guests and exposes login entry', () => {
    const groups = buildResultActionGroups({
      isLoggedIn: false,
      isPublic: true
    })

    expect(groups.primary).toEqual(['replan'])
    expect(groups.secondary).toEqual(['poster'])
    expect(groups.tertiary).toEqual(['home', 'community', 'login'])
  })
})

describe('buildDayPlans', () => {
  it('splits nodes by explicit dayNo first', () => {
    const plans = buildDayPlans([
      { poiId: 1, dayNo: 1, startTime: '09:00' },
      { poiId: 2, dayNo: 1, startTime: '11:00' },
      { poiId: 3, dayNo: 2, startTime: '09:30' }
    ])

    expect(plans).toHaveLength(2)
    expect(plans[0].label).toBe('第 1 天')
    expect(plans[0].nodes).toHaveLength(2)
    expect(plans[1].label).toBe('第 2 天')
    expect(plans[1].nodes[0].poiId).toBe(3)
  })

  it('falls back to clock reset splitting when dayNo is missing', () => {
    const plans = buildDayPlans([
      { poiId: 1, startTime: '09:00' },
      { poiId: 2, startTime: '11:30' },
      { poiId: 3, startTime: '08:45' }
    ])

    expect(plans).toHaveLength(2)
    expect(plans[1].nodes[0].poiId).toBe(3)
  })
})

describe('hero and travel helpers', () => {
  it('builds hero content from route, departure and option data', () => {
    const hero = buildResultHeroContent({
      itinerary: {
        tips: '午后热门点位可能排队，建议把拍照站放在前半段。',
        originalReq: {
          tripDate: '2026-04-25',
          themes: ['美食', '夜游'],
          companionType: '朋友',
          isRainy: true
        }
      },
      activeOption: {
        title: '经典慢逛线',
        totalDuration: 480,
        totalCost: 268,
        recommendReason: '热门点位密度合适，顺路程度更高。',
        highlights: ['宽窄巷子', '太古里']
      },
      displayNodes: [
        {
          poiId: 1,
          poiName: '宽窄巷子',
          departureTravelTime: 20,
          departureTransportMode: '打车',
          departureDistanceKm: 5.1
        },
        { poiId: 2, poiName: '太古里' }
      ],
      dayPlans: [
        { day: 1, dayIndex: 0, label: '第 1 天', nodes: [{ poiId: 1 }] },
        { day: 2, dayIndex: 1, label: '第 2 天', nodes: [{ poiId: 2 }] }
      ],
      displayOptions: [{ optionKey: 'A' }, { optionKey: 'B' }],
      isLoggedIn: true
    })

    expect(hero.summary).toContain('2026-04-25')
    expect(hero.summary).toContain('经典慢逛线')
    expect(hero.summary).not.toContain('总时长')
    expect(hero.summary).not.toContain('预算')
    expect(hero.departureSummary).toContain('首段')
    expect(hero.departureSummary).not.toContain('20 分钟')
    expect(hero.pills).not.toContain('多方案对比')
    expect(hero.pills).toContain('2天节奏')
    expect(hero.pills).toContain('雨天友好')
    expect(hero.pills).not.toContain('首段打车')
    expect(hero.recommendation).toBe('热门点位密度合适，顺路程度更高。')
    expect(hero.footnote).toBe('午后热门点位可能排队，建议把拍照站放在前半段。')
  })

  it('formats travel labels, mode and distance for first leg and inner route legs', () => {
    expect(formatNodeTravelLabel({
      stepOrder: 1,
      departureTravelTime: 22
    })).toBe('从出发地前往约 22 分钟')

    expect(formatTravelMode({
      stepOrder: 1,
      departureTransportMode: '地铁'
    })).toBe('地铁')

    expect(formatTravelDistance({
      stepOrder: 1,
      departureDistanceKm: 4.26
    })).toBe('4.3')

    expect(formatNodeTravelLabel({
      stepOrder: 2,
      travelTime: 18
    })).toBe('上一站前往约 18 分钟')

    expect(formatTravelMode({
      stepOrder: 2,
      travelTransportMode: '步行'
    })).toBe('步行')

    expect(formatTravelDistance({
      stepOrder: 2,
      travelDistanceKm: 1.04
    })).toBe('1.0')
  })
})

describe('segment route guide helpers', () => {
  it('builds guide titles and summary fallbacks without inventing structure', () => {
    expect(buildSegmentGuideTitle({
      stepOrder: 1,
      fromName: '当前位置',
      toName: '宽窄巷子'
    })).toBe('当前位置 → 宽窄巷子')

    expect(formatSegmentGuideSummary({
      summary: '',
      transportMode: '打车',
      durationMinutes: 14,
      distanceKm: 5.1
    })).toBe('打车约14分钟，约5.1公里')
  })

  it('prefers the backend summary when it exists', () => {
    expect(formatSegmentGuideSummary({
      summary: '步行 300 米 → 地铁 2 站 → 步行 450 米',
      source: 'amap',
      transportMode: '地铁+步行',
      durationMinutes: 27,
      distanceKm: 8.4
    })).toBe('地铁+步行约27分钟，约8.4公里')
  })

  it('uses product-facing copy for local route references', () => {
    expect(formatSegmentGuideSummary({
      summary: '打车约 14 分钟，约 5.1 公里',
      detailAvailable: false,
      incompleteReason: '暂未获取高德导航详情，当前仅展示估算通行信息'
    })).toBe('打车约 14 分钟，约 5.1 公里')
  })
})
