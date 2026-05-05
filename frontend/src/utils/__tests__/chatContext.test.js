import { beforeEach, describe, expect, it } from 'vitest'
import { buildSharedChatContext, pushRecentPoiContext, readRecentPoiContext } from '../chatContext'

const ITINERARY_STORAGE_KEY = 'citytrip/current-itinerary-v2'
const RECENT_POI_STORAGE_KEY = 'citytrip/chat/recent-pois'

describe('chatContext utils', () => {
  beforeEach(() => {
    window.localStorage.clear()
    window.sessionStorage.clear()
  })

  it('buildSharedChatContext should include itinerary snapshot and recent pois', () => {
    window.localStorage.setItem(ITINERARY_STORAGE_KEY, JSON.stringify({
      id: 9,
      selectedOptionKey: 'B',
      recommendReason: '路线整体更顺',
      originalReq: {
        cityCode: 'CD',
        cityName: '成都',
        themes: ['文化'],
        isRainy: true,
        companionType: '朋友',
        departureLatitude: 30.66,
        departureLongitude: 104.06
      },
      options: [
        {
          optionKey: 'A',
          nodes: [{ poiId: 100, poiName: '杜甫草堂' }]
        },
        {
          optionKey: 'B',
          summary: '主打城区文化线',
          totalDuration: 480,
          totalCost: 298,
          nodes: [
            {
              nodeKey: 'node-1',
              dayNo: 1,
              stepOrder: 1,
              poiId: 1,
              poiName: '宽窄巷子',
              category: '历史街区',
              district: '青羊',
              startTime: '09:00',
              endTime: '10:30',
              stayDuration: 90,
              travelTime: 18,
              travelTransportMode: '地铁',
              travelDistanceKm: 4.3,
              departureTravelTime: 20,
              departureTransportMode: '打车',
              departureDistanceKm: 5.1,
              latitude: 30.67,
              longitude: 104.04
            }
          ]
        }
      ]
    }))
    window.sessionStorage.setItem(RECENT_POI_STORAGE_KEY, JSON.stringify([
      { poiId: 3, poiName: 'IFS国际金融中心', category: '商圈', district: '锦江' }
    ]))

    const context = buildSharedChatContext({
      pageType: 'home',
      currentForm: {
        themes: ['美食', '夜游'],
        isNight: true
      }
    })

    expect(context.pageType).toBe('home')
    expect(context.preferences).toEqual(['美食', '夜游'])
    expect(context.rainy).toBe(true)
    expect(context.nightMode).toBe(true)
    expect(context.cityCode).toBe('CD')
    expect(context.cityName).toBe('成都')
    expect(context.itinerary?.selectedOptionKey).toBe('B')
    expect(context.itinerary?.summary).toBe('主打城区文化线')
    expect(context.itinerary?.nodes?.[0]?.poiName).toBe('宽窄巷子')
    expect(context.itinerary?.nodes?.[0]?.nodeKey).toBe('node-1')
    expect(context.itinerary?.nodes?.[0]?.dayNo).toBe(1)
    expect(context.itinerary?.nodes?.[0]?.stepOrder).toBe(1)
    expect(context.itinerary?.nodes?.[0]?.stayDuration).toBe(90)
    expect(context.itinerary?.nodes?.[0]?.departureTransportMode).toBe('打车')
    expect(context.itinerary?.nodes?.[0]?.departureTravelTime).toBe(20)
    expect(context.recentPois).toHaveLength(1)
    expect(context.recentPois[0].poiName).toBe('IFS国际金融中心')
  })

  it('pushRecentPoiContext should dedupe and keep newest records', () => {
    pushRecentPoiContext({ poiId: 1, name: '杜甫草堂', category: '博物馆' })
    pushRecentPoiContext({ poiId: 2, name: '武侯祠', category: '历史' })
    pushRecentPoiContext({ poiId: 1, name: '杜甫草堂', category: '博物馆' })

    const list = readRecentPoiContext()
    expect(list).toHaveLength(2)
    expect(list[0].poiName).toBe('杜甫草堂')
    expect(list[1].poiName).toBe('武侯祠')
  })

  it('buildSharedChatContext should omit stored itinerary and request context when disabled', () => {
    window.localStorage.setItem(ITINERARY_STORAGE_KEY, JSON.stringify({
      id: 12,
      originalReq: {
        cityCode: 'CD',
        cityName: '成都',
        themes: ['文化']
      },
      options: [
        {
          optionKey: 'A',
          nodes: [{ poiId: 1, poiName: '宽窄巷子' }]
        }
      ]
    }))
    window.sessionStorage.setItem('original_req_form', JSON.stringify({
      cityCode: 'CD',
      cityName: '成都',
      themes: ['文化']
    }))

    const context = buildSharedChatContext({
      pageType: 'home',
      currentForm: {},
      includeItinerary: false,
      includeStoredForm: false
    })

    expect(context.cityCode).toBe('')
    expect(context.cityName).toBe('')
    expect(context.preferences).toEqual([])
    expect(context.itinerary).toBeNull()
  })
  it('includes originalReq for result-page chat workflows', () => {
    window.localStorage.setItem('citytrip/current-itinerary-v2', JSON.stringify({
      id: 77,
      originalReq: {
        cityCode: 'CD',
        cityName: '成都',
        tripDays: '   ',
        tripDate: '2026-05-02',
        totalBudget: 288,
        startTime: '09:00',
        endTime: '18:00',
        budgetLevel: '中',
        themes: ['文化'],
        isRainy: false,
        isNight: true,
        walkingLevel: '中',
        companionType: '朋友',
        mustVisitPoiNames: ['杜甫草堂'],
        departurePlaceName: '春熙路',
        departureLatitude: null
      },
      nodes: [{ poiName: '杜甫草堂' }]
    }))

    const context = buildSharedChatContext({ pageType: 'result' })

    expect(context.originalReq).toMatchObject({
      cityCode: 'CD',
      cityName: '成都',
      tripDate: '2026-05-02',
      totalBudget: 288,
      startTime: '09:00',
      endTime: '18:00',
      walkingLevel: '中'
    })
    expect(context.originalReq.themes).toEqual(['文化'])
    expect(context.originalReq.mustVisitPoiNames).toEqual(['杜甫草堂'])
    expect(context.originalReq.tripDays).toBeNull()
    expect(context.originalReq.departureLatitude).toBeNull()
    expect(context.originalReq.departureLongitude).toBeNull()
  })
})
