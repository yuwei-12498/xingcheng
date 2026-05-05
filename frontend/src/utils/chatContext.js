import { loadItinerarySnapshot } from '@/store/itinerary'

const RECENT_POI_STORAGE_KEY = 'citytrip/chat/recent-pois'
const MAX_RECENT_POI_COUNT = 5
const MAX_ROUTE_NODE_COUNT = 12

const canUseSessionStorage = () => typeof window !== 'undefined' && typeof window.sessionStorage !== 'undefined'

const safeString = (value) => (typeof value === 'string' ? value.trim() : '')

const numberOrNull = (value) => {
  if (value === null || value === undefined) {
    return null
  }
  if (typeof value === 'string' && !value.trim()) {
    return null
  }
  const n = Number(value)
  return Number.isFinite(n) ? n : null
}

const intOrNull = (value) => {
  const n = Number(value)
  return Number.isInteger(n) ? n : null
}

const boolOrDefault = (value, fallback = false) => {
  if (value === null || value === undefined) {
    return fallback
  }
  return Boolean(value)
}

const toStringArray = (value) => {
  if (!Array.isArray(value)) {
    return []
  }
  return value.map(item => safeString(item)).filter(Boolean)
}

const normalizeGenerateRequest = (form) => {
  const source = form && typeof form === 'object' ? form : {}
  return {
    cityCode: safeString(source.cityCode),
    cityName: safeString(source.cityName),
    tripDays: numberOrNull(source.tripDays),
    tripDate: safeString(source.tripDate),
    totalBudget: numberOrNull(source.totalBudget),
    startTime: safeString(source.startTime),
    endTime: safeString(source.endTime),
    budgetLevel: safeString(source.budgetLevel),
    themes: toStringArray(source.themes),
    isRainy: source.isRainy === undefined ? null : boolOrDefault(source.isRainy, false),
    isNight: source.isNight === undefined ? null : boolOrDefault(source.isNight, false),
    walkingLevel: safeString(source.walkingLevel),
    companionType: safeString(source.companionType),
    mustVisitPoiNames: toStringArray(source.mustVisitPoiNames),
    departurePlaceName: safeString(source.departurePlaceName),
    departureLatitude: numberOrNull(source.departureLatitude),
    departureLongitude: numberOrNull(source.departureLongitude)
  }
}

const readOriginalReqForm = () => {
  if (!canUseSessionStorage()) {
    return null
  }
  try {
    const raw = window.sessionStorage.getItem('original_req_form')
    if (!raw) {
      return null
    }
    const parsed = JSON.parse(raw)
    return parsed && typeof parsed === 'object' ? parsed : null
  } catch (err) {
    return null
  }
}

const resolveBaseForm = (currentForm, itinerarySnapshot, { includeStoredForm = true } = {}) => {
  if (!includeStoredForm) {
    return {
      ...(currentForm || {})
    }
  }

  const fromSession = readOriginalReqForm()
  const fromSnapshot = itinerarySnapshot?.originalReq && typeof itinerarySnapshot.originalReq === 'object'
    ? itinerarySnapshot.originalReq
    : null

  return {
    ...(fromSnapshot || {}),
    ...(fromSession || {}),
    ...(currentForm || {})
  }
}

const resolveActiveOption = (snapshot) => {
  const options = Array.isArray(snapshot?.options) ? snapshot.options : []
  if (!options.length) {
    return null
  }
  return options.find(option => option?.optionKey === snapshot?.selectedOptionKey) || options[0]
}

const normalizeRouteNode = (node) => {
  if (!node || typeof node !== 'object') {
    return null
  }
  const poiName = safeString(node.poiName || node.name)
  if (!poiName) {
    return null
  }
  return {
    nodeKey: safeString(node.nodeKey),
    dayNo: intOrNull(node.dayNo),
    stepOrder: intOrNull(node.stepOrder),
    poiId: intOrNull(node.poiId || node.id),
    poiName,
    category: safeString(node.category),
    district: safeString(node.district),
    startTime: safeString(node.startTime),
    endTime: safeString(node.endTime),
    stayDuration: intOrNull(node.stayDuration),
    travelTime: intOrNull(node.travelTime),
    travelTransportMode: safeString(node.travelTransportMode || node.departureTransportMode),
    travelDistanceKm: numberOrNull(node.travelDistanceKm || node.departureDistanceKm),
    departureTravelTime: intOrNull(node.departureTravelTime),
    departureTransportMode: safeString(node.departureTransportMode),
    departureDistanceKm: numberOrNull(node.departureDistanceKm),
    latitude: numberOrNull(node.latitude),
    longitude: numberOrNull(node.longitude),
    sourceType: safeString(node.sourceType)
  }
}

const extractItineraryContext = (snapshot) => {
  if (!snapshot || typeof snapshot !== 'object') {
    return null
  }

  const activeOption = resolveActiveOption(snapshot)
  const candidateNodes = Array.isArray(activeOption?.nodes) && activeOption.nodes.length
    ? activeOption.nodes
    : (Array.isArray(snapshot.nodes) ? snapshot.nodes : [])

  const nodes = candidateNodes
    .map(normalizeRouteNode)
    .filter(Boolean)
    .slice(0, MAX_ROUTE_NODE_COUNT)

  if (!nodes.length) {
    return null
  }

  return {
    itineraryId: intOrNull(snapshot.id),
    selectedOptionKey: safeString(activeOption?.optionKey || snapshot?.selectedOptionKey),
    summary: safeString(activeOption?.summary || snapshot?.recommendReason),
    totalDuration: intOrNull(activeOption?.totalDuration || snapshot?.totalDuration),
    totalCost: numberOrNull(activeOption?.totalCost || snapshot?.totalCost),
    nodes
  }
}

const normalizeRecentPoi = (poi) => {
  if (!poi || typeof poi !== 'object') {
    return null
  }
  const poiName = safeString(poi.poiName || poi.name)
  if (!poiName) {
    return null
  }
  return {
    poiId: intOrNull(poi.poiId || poi.id),
    poiName,
    category: safeString(poi.category),
    district: safeString(poi.district)
  }
}

export const readRecentPoiContext = () => {
  if (!canUseSessionStorage()) {
    return []
  }
  try {
    const raw = window.sessionStorage.getItem(RECENT_POI_STORAGE_KEY)
    if (!raw) {
      return []
    }
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) {
      return []
    }
    return parsed
      .map(normalizeRecentPoi)
      .filter(Boolean)
      .slice(0, MAX_RECENT_POI_COUNT)
  } catch (err) {
    return []
  }
}

const persistRecentPois = (list) => {
  if (!canUseSessionStorage()) {
    return
  }
  try {
    window.sessionStorage.setItem(RECENT_POI_STORAGE_KEY, JSON.stringify(list))
  } catch (err) {
  }
}

export const pushRecentPoiContext = (poi) => {
  const normalized = normalizeRecentPoi(poi)
  if (!normalized) {
    return
  }
  const current = readRecentPoiContext()
  const deduped = current.filter(item => {
    if (normalized.poiId && item.poiId) {
      return item.poiId !== normalized.poiId
    }
    return item.poiName !== normalized.poiName
  })
  const next = [normalized, ...deduped].slice(0, MAX_RECENT_POI_COUNT)
  persistRecentPois(next)
}

export const buildSharedChatContext = ({
  pageType = 'page',
  currentForm = null,
  includeItinerary = true,
  includeStoredForm = true
} = {}) => {
  const shouldLoadStoredSnapshot = includeItinerary || includeStoredForm
  const itinerarySnapshot = shouldLoadStoredSnapshot ? loadItinerarySnapshot() : null
  const baseForm = resolveBaseForm(currentForm, itinerarySnapshot, { includeStoredForm })
  const itinerary = includeItinerary ? extractItineraryContext(itinerarySnapshot) : null
  const recentPois = readRecentPoiContext()

  return {
    pageType: safeString(pageType) || 'page',
    preferences: toStringArray(baseForm.themes),
    rainy: boolOrDefault(baseForm.isRainy, false),
    nightMode: boolOrDefault(baseForm.isNight, false),
    companionType: safeString(baseForm.companionType),
    cityCode: safeString(baseForm.cityCode),
    cityName: safeString(baseForm.cityName),
    userLat: numberOrNull(baseForm.departureLatitude),
    userLng: numberOrNull(baseForm.departureLongitude),
    originalReq: normalizeGenerateRequest(baseForm),
    itinerary,
    recentPois
  }
}
