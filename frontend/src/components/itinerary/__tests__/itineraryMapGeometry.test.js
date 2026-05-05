import { describe, expect, it } from 'vitest'
import {
  buildCurvedConnectionPath,
  getNodeName,
  normalizeRoutePathPoints,
  pathAnchorsMatchSegment,
  toLatLng
} from '../itineraryMapGeometry'

describe('itinerary map geometry helpers', () => {
  it('normalizes route path points and removes consecutive duplicates', () => {
    expect(normalizeRoutePathPoints([
      { latitude: 30.1, longitude: 104.1 },
      { latitude: 30.1, longitude: 104.1 },
      { lat: 30.2, lng: 104.2 },
      { latitude: 999, longitude: 104.3 }
    ])).toEqual([
      [30.1, 104.1],
      [30.2, 104.2]
    ])
  })

  it('builds readable node names and valid lat-lng tuples', () => {
    expect(getNodeName({ poiName: '  太古里  ' }, 'fallback')).toBe('太古里')
    expect(getNodeName({ poiName: 'CURRENT_LOCATION' }, '当前位置')).toBe('当前位置')
    expect(toLatLng({ latitude: 30.66, longitude: 104.08 })).toEqual([30.66, 104.08])
    expect(toLatLng({ latitude: 130, longitude: 104.08 })).toBeNull()
  })

  it('builds fallback curves that still anchor to the segment endpoints', () => {
    const from = [30.66, 104.08]
    const to = [30.67, 104.09]
    const curve = buildCurvedConnectionPath(from, to, 0)

    expect(curve[0]).toEqual(from)
    expect(curve[curve.length - 1]).toEqual(to)
    expect(pathAnchorsMatchSegment(curve, from, to)).toBe(true)
  })
})
