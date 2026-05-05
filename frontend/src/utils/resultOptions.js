// Shared itinerary option normalization helpers for the result page.
export const formatCurrency = value => {
  if (value === null || value === undefined || value === '') {
    return '--'
  }
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) {
    return '--'
  }
  return `¥${Math.round(numeric)}`
}

export const isUnknownPlaceholderText = value => {
  if (typeof value !== 'string') return true
  const trimmed = value.trim()
  if (!trimmed) return true
  if (trimmed === 'CURRENT_LOCATION') return true
  if (/^[?？]+$/.test(trimmed) || /[?？]{2,}/.test(trimmed)) return true
  if (['null', 'undefined', 'n/a', 'na', '--'].includes(trimmed.toLowerCase())) return true
  return false
}

export const sanitizeDisplayText = (value, fallback = '') => {
  if (isUnknownPlaceholderText(value)) {
    return fallback
  }
  return String(value).trim()
}

export const formatPoiName = (value, fallback = '未命名站点') => {
  return sanitizeDisplayText(value, fallback)
}

export const buildRouteSignature = nodes => {
  return (nodes || [])
    .map(node => node?.poiId)
    .filter(Boolean)
    .join('-')
}

export const normalizeOption = (option, index = 0) => {
  const nodes = Array.isArray(option?.nodes) ? option.nodes : []
  const totalTravelTime = option?.totalTravelTime ?? nodes.reduce((sum, node) => sum + Number(node?.travelTime || 0), 0)
  return {
    optionKey: option?.optionKey || `option-${index + 1}`,
    title: option?.title || `候选方案 ${index + 1}`,
    subtitle: option?.subtitle || '已结合时间窗与顺路程度优化',
    signature: option?.signature || buildRouteSignature(nodes),
    totalDuration: Number(option?.totalDuration || 0),
    totalCost: option?.totalCost ?? 0,
    stopCount: option?.stopCount ?? nodes.length,
    totalTravelTime,
    summary: option?.summary || option?.recommendReason || '',
    recommendReason: option?.recommendReason || '',
    notRecommendReason: option?.notRecommendReason || '',
    highlights: Array.isArray(option?.highlights) ? option.highlights : [],
    tradeoffs: Array.isArray(option?.tradeoffs) ? option.tradeoffs : [],
    alerts: Array.isArray(option?.alerts) ? option.alerts : [],
    nodes
  }
}

export const buildFallbackOption = snapshot => {
  const nodes = Array.isArray(snapshot?.nodes) ? snapshot.nodes : []
  return normalizeOption({
    optionKey: 'default',
    title: snapshot?.customTitle || '当前默认方案',
    subtitle: '当前保存的路线版本',
    signature: buildRouteSignature(nodes),
    totalDuration: snapshot?.totalDuration || 0,
    totalCost: snapshot?.totalCost || 0,
    stopCount: nodes.length,
    totalTravelTime: nodes.reduce((total, node) => total + Number(node?.travelTime || 0), 0),
    summary: snapshot?.recommendReason || snapshot?.tips || '',
    recommendReason: snapshot?.recommendReason || '',
    notRecommendReason: snapshot?.tips || '',
    alerts: Array.isArray(snapshot?.alerts) ? snapshot.alerts : [],
    nodes
  })
}

export const resolveOptions = snapshot => {
  if (Array.isArray(snapshot?.options) && snapshot.options.length) {
    return snapshot.options.map((option, index) => normalizeOption(option, index))
  }
  return snapshot ? [buildFallbackOption(snapshot)] : []
}

export const resolveActiveOption = snapshot => {
  const options = resolveOptions(snapshot)
  if (!options.length) {
    return null
  }
  return options.find(option => option.optionKey === snapshot?.selectedOptionKey) || options[0]
}

export const resolveActiveNodes = snapshot => {
  const option = resolveActiveOption(snapshot)
  return Array.isArray(option?.nodes) ? option.nodes : (snapshot?.nodes || [])
}

