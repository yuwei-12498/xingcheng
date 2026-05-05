import { beforeEach, describe, expect, it, vi } from 'vitest'

import { persistDepartureLocation, questionNeedsLocation, resolveCurrentLocation } from '@/utils/location'

describe('location utils', () => {
  beforeEach(() => {
    window.sessionStorage.clear()
    vi.restoreAllMocks()
  })

  it('detects when a chat question needs live location', () => {
    expect(questionNeedsLocation('我附近有什么适合拍照的地方？')).toBe(true)
    expect(questionNeedsLocation('离我最近的咖啡店在哪')).toBe(true)
    expect(questionNeedsLocation('帮我规划一条成都美食路线')).toBe(false)
  })

  it('persists departure coordinates into original_req_form', () => {
    window.sessionStorage.setItem('original_req_form', JSON.stringify({
      cityCode: 'CD',
      cityName: '成都'
    }))

    persistDepartureLocation({
      latitude: 30.67,
      longitude: 104.07
    })

    expect(JSON.parse(window.sessionStorage.getItem('original_req_form'))).toEqual({
      cityCode: 'CD',
      cityName: '成都',
      departureLatitude: 30.67,
      departureLongitude: 104.07,
      departurePlaceName: 'CURRENT_LOCATION'
    })
  })

  it('falls back to a lower accuracy geolocation request', async () => {
    const getCurrentPosition = vi.fn()
      .mockImplementationOnce((success, error) => error(new Error('high accuracy failed')))
      .mockImplementationOnce((success) => success({
        coords: {
          latitude: 30.66,
          longitude: 104.05
        }
      }))

    Object.defineProperty(window.navigator, 'geolocation', {
      configurable: true,
      value: { getCurrentPosition }
    })

    await expect(resolveCurrentLocation()).resolves.toEqual({
      latitude: 30.66,
      longitude: 104.05
    })

    expect(getCurrentPosition).toHaveBeenCalledTimes(2)
    expect(getCurrentPosition.mock.calls[0][2]).toMatchObject({
      enableHighAccuracy: true
    })
    expect(getCurrentPosition.mock.calls[1][2]).toMatchObject({
      enableHighAccuracy: false
    })
  })
})
