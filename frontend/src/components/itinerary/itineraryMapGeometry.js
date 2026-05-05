// Shared pure helpers for ItineraryMapCard route geometry and display text.
export const isUnknownPlaceholderText = value => {
  if (typeof value !== 'string') {
    return true
  }
  const trimmed = value.trim()
  if (!trimmed) {
    return true
  }
  if (trimmed === 'CURRENT_LOCATION') {
    return true
  }
  if (/^[?？]+$/.test(trimmed) || /[?？]{2,}/.test(trimmed)) {
    return true
  }
  if (['null', 'undefined', 'n/a', 'na', '--'].includes(trimmed.toLowerCase())) {
    return true
  }
  return false
}

export const toDisplayText = (value, fallback = '') => {
  if (isUnknownPlaceholderText(value)) {
    return fallback
  }
  return String(value).trim()
}

export const getNodeName = (node, fallback = '') => {
  return toDisplayText(node?.poiName, fallback)
}

export const toFiniteNumber = value => {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

export const isValidCoordinate = (latitude, longitude) => {
  return Number.isFinite(latitude)
    && Number.isFinite(longitude)
    && Math.abs(latitude) <= 90
    && Math.abs(longitude) <= 180
}

export const toLatLng = point => {
  if (!point) return null
  const latitude = toFiniteNumber(point.latitude ?? point.lat)
  const longitude = toFiniteNumber(point.longitude ?? point.lng)
  if (!isValidCoordinate(latitude, longitude)) {
    return null
  }
  return [latitude, longitude]
}


export const normalizeRoutePathPoints = points => {
  if (!Array.isArray(points)) {
    return []
  }
  return points
    .map(point => toLatLng(point))
    .filter(Boolean)
    .filter((point, index, list) => {
      if (index === 0) return true
      const previous = list[index - 1]
      return previous[0] !== point[0] || previous[1] !== point[1]
    })
}

export const measurePointDistanceKm = (fromPoint, toPoint) => {
  if (!fromPoint || !toPoint) {
    return Number.POSITIVE_INFINITY
  }
  const [lat1, lng1] = fromPoint
  const [lat2, lng2] = toPoint
  const toRadians = value => value * Math.PI / 180
  const deltaLat = toRadians(lat2 - lat1)
  const deltaLng = toRadians(lng2 - lng1)
  const a = Math.sin(deltaLat / 2) ** 2
    + Math.cos(toRadians(lat1)) * Math.cos(toRadians(lat2)) * Math.sin(deltaLng / 2) ** 2
  return 6371 * 2 * Math.asin(Math.sqrt(a))
}

export const buildCurvedConnectionPath = (fromPoint, toPoint, index = 0) => {
  if (!fromPoint || !toPoint) {
    return []
  }
  const [fromLat, fromLng] = fromPoint
  const [toLat, toLng] = toPoint
  const deltaLat = toLat - fromLat
  const deltaLng = toLng - fromLng
  const vectorLength = Math.sqrt(deltaLat ** 2 + deltaLng ** 2)
  if (!vectorLength) {
    return [fromPoint]
  }

  const perpendicularLat = -deltaLng / vectorLength
  const perpendicularLng = deltaLat / vectorLength
  const curveBase = Math.max(0.0012, Math.min(0.014, measurePointDistanceKm(fromPoint, toPoint) * 0.0032))
  const signedCurve = index % 2 === 0 ? curveBase : -curveBase
  const buildIntermediatePoint = (ratio, curveOffset) => ([
    Number((fromLat + deltaLat * ratio + perpendicularLat * curveOffset).toFixed(6)),
    Number((fromLng + deltaLng * ratio + perpendicularLng * curveOffset).toFixed(6))
  ])

  return [
    fromPoint,
    buildIntermediatePoint(0.32, signedCurve),
    buildIntermediatePoint(0.72, -signedCurve * 0.28),
    toPoint
  ]
}


export const pathAnchorsMatchSegment = (pathPoints, fromPoint, toPoint) => {
  if (!Array.isArray(pathPoints) || pathPoints.length < 2 || !fromPoint || !toPoint) {
    return false
  }
  const startDistance = measurePointDistanceKm(pathPoints[0], fromPoint)
  const endDistance = measurePointDistanceKm(pathPoints[pathPoints.length - 1], toPoint)
  return startDistance <= 1.2 && endDistance <= 1.2
}


