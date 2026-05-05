const LOCATION_REQUIRED_KEYWORDS = [
  '附近',
  '周边',
  '离我',
  '离这里',
  '最近',
  '就近',
  '定位',
  '当前',
  '我在哪',
  '我现在',
  '怎么去',
  '怎么到',
  '多远',
  '步行',
  '打车',
  '公交',
  '地铁'
]

const canUseSessionStorage = () => typeof window !== 'undefined' && typeof window.sessionStorage !== 'undefined'

const numberOrNull = (value) => {
  const n = Number(value)
  return Number.isFinite(n) ? n : null
}

export const questionNeedsLocation = (question) => {
  const text = typeof question === 'string' ? question.trim() : ''
  if (!text) {
    return false
  }
  return LOCATION_REQUIRED_KEYWORDS.some(keyword => text.includes(keyword))
}

export const persistDepartureLocation = (location, departurePlaceName = 'CURRENT_LOCATION') => {
  if (!canUseSessionStorage() || !location || typeof location !== 'object') {
    return
  }

  const latitude = numberOrNull(location.latitude)
  const longitude = numberOrNull(location.longitude)
  if (latitude === null || longitude === null) {
    return
  }

  let current = {}
  try {
    const raw = window.sessionStorage.getItem('original_req_form')
    if (raw) {
      const parsed = JSON.parse(raw)
      if (parsed && typeof parsed === 'object') {
        current = parsed
      }
    }
  } catch (err) {
  }

  const next = {
    ...current,
    departureLatitude: latitude,
    departureLongitude: longitude,
    departurePlaceName: departurePlaceName || current.departurePlaceName || 'CURRENT_LOCATION'
  }

  try {
    window.sessionStorage.setItem('original_req_form', JSON.stringify(next))
  } catch (err) {
  }
}

export const resolveCurrentLocation = () => {
  if (typeof window === 'undefined' || !navigator?.geolocation) {
    return Promise.resolve(null)
  }

  const requestPosition = options => new Promise((resolve) => {
    navigator.geolocation.getCurrentPosition(
      position => {
        resolve({
          latitude: Number(position.coords.latitude),
          longitude: Number(position.coords.longitude)
        })
      },
      () => resolve(null),
      options
    )
  })

  return requestPosition({ enableHighAccuracy: true, timeout: 8000, maximumAge: 30000 })
    .then(result => {
      if (result) {
        return result
      }
      return requestPosition({ enableHighAccuracy: false, timeout: 5000, maximumAge: 180000 })
    })
}
