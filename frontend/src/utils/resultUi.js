const formatDuration = minutes => {
  const safeMinutes = Number(minutes || 0)
  const hour = Math.floor(safeMinutes / 60)
  const minute = safeMinutes % 60

  if (!hour) return `${minute} 分钟`
  if (!minute) return `${hour} 小时`
  return `${hour} 小时 ${minute} 分钟`
}

const formatCurrency = value => {
  if (value === null || value === undefined || value === '') return '--'
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) return '--'
  return `¥${Math.round(numeric)}`
}

const formatDistanceNumber = value => {
  const safeValue = Number(value)
  if (!Number.isFinite(safeValue) || safeValue <= 0) {
    return null
  }
  return safeValue.toFixed(1)
}

const parseClockMinutes = value => {
  if (typeof value !== 'string' || !value.includes(':')) {
    return null
  }
  const [hourRaw, minuteRaw] = value.split(':')
  const hour = Number(hourRaw)
  const minute = Number(minuteRaw)
  if (!Number.isFinite(hour) || !Number.isFinite(minute)) {
    return null
  }
  return hour * 60 + minute
}

const dedupeStrings = items => {
  return [...new Set((items || []).filter(item => typeof item === 'string' && item.trim()).map(item => item.trim()))]
}

const getDayNo = node => {
  const dayNo = Number(node?.dayNo)
  return Number.isInteger(dayNo) && dayNo > 0 ? dayNo : null
}

const resolveTransportModeCostProfile = mode => {
  const normalized = typeof mode === 'string' ? mode.trim().toLowerCase() : ''
  if (!normalized) {
    return {
      base: 2,
      perKm: 0.7,
      perMinute: 0.2
    }
  }
  if (normalized.includes('步行')) {
    return {
      base: 0,
      perKm: 0,
      perMinute: 0
    }
  }
  if (normalized.includes('公交') || normalized.includes('地铁') || normalized.includes('tram')) {
    return {
      base: 2,
      perKm: 0.25,
      perMinute: 0.12
    }
  }
  if (normalized.includes('骑行') || normalized.includes('单车')) {
    return {
      base: 1,
      perKm: 0.2,
      perMinute: 0.08
    }
  }
  if (normalized.includes('打车') || normalized.includes('网约车') || normalized.includes('出租')) {
    return {
      base: 8,
      perKm: 1.9,
      perMinute: 0.45
    }
  }
  if (normalized.includes('自驾')) {
    return {
      base: 5,
      perKm: 0.9,
      perMinute: 0.22
    }
  }
  return {
    base: 3,
    perKm: 0.65,
    perMinute: 0.2
  }
}

const toPositiveNumber = value => {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : 0
}

const estimateLegTransportCost = ({ mode, distanceKm, durationMinutes }) => {
  const safeDistance = toPositiveNumber(distanceKm)
  const safeDuration = toPositiveNumber(durationMinutes)
  const profile = resolveTransportModeCostProfile(mode)

  if (!safeDistance && !safeDuration) {
    return 0
  }
  if (profile.base === 0 && profile.perKm === 0 && profile.perMinute === 0) {
    return 0
  }

  const distanceCost = safeDistance * profile.perKm
  const minuteCost = safeDuration * profile.perMinute
  return profile.base + Math.max(distanceCost, minuteCost)
}

export const estimateTransportCost = activeNodes => {
  const nodes = Array.isArray(activeNodes) ? activeNodes : []
  if (!nodes.length) return 0
  const total = nodes.reduce((sum, node, index) => {
    const isFirstLeg = index === 0 || Number(node?.stepOrder) === 1
    const departureDistance = toPositiveNumber(node?.departureDistanceKm)
    const departureDuration = toPositiveNumber(node?.departureTravelTime)
    const cost = estimateLegTransportCost({
      mode: isFirstLeg ? (node?.departureTransportMode || node?.travelTransportMode) : node?.travelTransportMode,
      distanceKm: isFirstLeg
        ? (departureDistance > 0 ? departureDistance : node?.travelDistanceKm)
        : node?.travelDistanceKm,
      durationMinutes: isFirstLeg
        ? (departureDuration > 0 ? departureDuration : node?.travelTime)
        : node?.travelTime
    })
    return sum + cost
  }, 0)
  return Math.round(total)
}

export const estimateTotalBudget = ({ baseCost, activeNodes }) => {
  const safeBaseCost = Number(baseCost)
  const base = Number.isFinite(safeBaseCost) ? safeBaseCost : 0
  return Math.max(0, Math.round(base + estimateTransportCost(activeNodes)))
}

export const buildResultStatItems = ({ activeOption, activeNodes }) => {
  const nodes = Array.isArray(activeNodes) ? activeNodes : []
  const totalTravelTime = Number(
    activeOption?.totalTravelTime ?? nodes.reduce((sum, node) => sum + Number(node?.travelTime || 0), 0)
  )
  const poiBudget = Number.isFinite(Number(activeOption?.totalCost))
    ? Number(activeOption.totalCost)
    : nodes.reduce((sum, node) => sum + Number(node?.cost || 0), 0)
  const totalBudget = estimateTotalBudget({
    baseCost: poiBudget,
    activeNodes: nodes
  })

  return [
    {
      label: '总时长',
      value: formatDuration(activeOption?.totalDuration || 0),
      tone: 'primary'
    },
    {
      label: '路上耗时',
      value: formatDuration(totalTravelTime),
      tone: 'neutral'
    },
    {
      label: '花费预算',
      value: formatCurrency(totalBudget),
      tone: 'neutral'
    }
  ]
}

export const buildResultActionGroups = ({ isLoggedIn, isPublic }) => {
  const secondary = ['poster']
  const tertiary = ['home', 'community']

  if (isLoggedIn) {
    secondary.unshift('favorite', 'publish')
    if (isPublic) {
      secondary.splice(1, 0, 'communityPost')
    }
    tertiary.push('history')
  } else {
    tertiary.push('login')
  }

  return {
    primary: ['replan'],
    secondary,
    tertiary
  }
}

export const buildDayPlans = nodes => {
  const list = Array.isArray(nodes) ? nodes.filter(Boolean) : []
  if (!list.length) {
    return []
  }

  const uniqueDayNos = dedupeStrings(list.map(node => {
    const dayNo = getDayNo(node)
    return dayNo ? String(dayNo) : ''
  }))

  if (uniqueDayNos.length > 1) {
    const groupMap = new Map()
    list.forEach(node => {
      const dayNo = getDayNo(node) || 1
      if (!groupMap.has(dayNo)) {
        groupMap.set(dayNo, [])
      }
      groupMap.get(dayNo).push(node)
    })

    return [...groupMap.entries()]
      .sort((a, b) => a[0] - b[0])
      .map(([day, dayNodes], index) => ({
        day,
        dayIndex: index,
        label: `第 ${day} 天`,
        nodes: dayNodes
      }))
  }

  const groups = []
  let current = []
  let previousStart = null

  list.forEach(node => {
    const currentStart = parseClockMinutes(node?.startTime)
    if (
      current.length &&
      Number.isFinite(previousStart) &&
      Number.isFinite(currentStart) &&
      currentStart < previousStart
    ) {
      groups.push(current)
      current = []
    }

    current.push(node)
    if (Number.isFinite(currentStart)) {
      previousStart = currentStart
    }
  })

  if (current.length) {
    groups.push(current)
  }

  return groups.map((groupNodes, index) => ({
    day: index + 1,
    dayIndex: index,
    label: `第 ${index + 1} 天`,
    nodes: groupNodes
  }))
}

export const formatTravelMode = node => {
  if (!node || typeof node !== 'object') {
    return ''
  }
  if (Number(node.stepOrder) === 1 && node.departureTransportMode) {
    return String(node.departureTransportMode).trim()
  }
  if (node.travelTransportMode) {
    return String(node.travelTransportMode).trim()
  }
  return ''
}

export const formatTravelDistance = node => {
  if (!node || typeof node !== 'object') {
    return ''
  }
  if (Number(node.stepOrder) === 1 && node.departureDistanceKm != null) {
    return formatDistanceNumber(node.departureDistanceKm) || ''
  }
  return formatDistanceNumber(node.travelDistanceKm) || ''
}

export const formatNodeTravelLabel = node => {
  if (!node || typeof node !== 'object') {
    return '等待路线信息'
  }

  const departureTravelTime = Number(node.departureTravelTime)
  if (Number(node.stepOrder) === 1 && Number.isFinite(departureTravelTime) && departureTravelTime > 0) {
    return `从出发地前往约 ${departureTravelTime} 分钟`
  }

  const travelTime = Number(node.travelTime)
  if (Number.isFinite(travelTime) && travelTime > 0) {
    return `上一站前往约 ${travelTime} 分钟`
  }

  if (Number(node.stepOrder) === 1) {
    return '从出发地直达第一站'
  }

  return '与上一站顺路衔接'
}

export const buildResultHeroContent = ({
  itinerary,
  activeOption,
  displayNodes,
  dayPlans,
  displayOptions,
  isLoggedIn
}) => {
  const originalReq = itinerary?.originalReq || {}
  const nodes = Array.isArray(displayNodes) ? displayNodes : []
  const plans = Array.isArray(dayPlans) ? dayPlans : []
  const options = Array.isArray(displayOptions) ? displayOptions : []
  const firstNode = nodes[0] || null

  const summaryParts = [
    originalReq.tripDate,
    activeOption?.title,
    nodes.length ? `${nodes.length} 个停靠点` : ''
  ].filter(Boolean)

  const departureMode = formatTravelMode({
    stepOrder: 1,
    departureTransportMode: firstNode?.departureTransportMode
  })
  const departureDistance = formatTravelDistance({
    stepOrder: 1,
    departureDistanceKm: firstNode?.departureDistanceKm
  })
  const departureTravelTime = Number(firstNode?.departureTravelTime)

  let departureSummary = ''
  if (firstNode && (Number.isFinite(departureTravelTime) || departureMode || departureDistance)) {
    departureSummary = `首段前往 ${firstNode.poiName || '第一站'}`
  }

  const pills = dedupeStrings([
    plans.length > 1 ? `${plans.length}天节奏` : '',
    ...(Array.isArray(originalReq.themes) ? originalReq.themes.slice(0, 2) : []),
    originalReq.companionType ? `${originalReq.companionType}同行` : '',
    originalReq.isRainy ? '雨天友好' : '',
    originalReq.isNight ? '夜游可用' : '',
    ...(Array.isArray(activeOption?.highlights) ? activeOption.highlights.slice(0, 2) : [])
  ]).slice(0, 6)

  return {
    summary: summaryParts.join(' · ') || '这条路线已经准备好，可以直接查看节奏、地图和替换建议。',
    departureSummary,
    pills,
    recommendation: activeOption?.recommendReason || itinerary?.recommendReason || '当前路线已经兼顾顺路程度、主题一致性与可执行性。',
    footnote: itinerary?.tips || (isLoggedIn
      ? '你可以继续收藏、发布路线帖或导出海报，当前切换的视图会同步影响分享结果。'
      : '先看路线，喜欢再登录；登录后可以收藏、保存历史，并把路线发布到社区。')
  }
}

const formatSegmentGuideDistanceValue = value => {
  const safeValue = Number(value)
  if (!Number.isFinite(safeValue) || safeValue <= 0) {
    return null
  }
  return safeValue.toFixed(1)
}

export const buildSegmentGuideTitle = ({ stepOrder, fromName, toName } = {}) => {
  const safeFrom = typeof fromName === 'string' && fromName.trim()
    ? fromName.trim()
    : (Number(stepOrder) === 1 ? '\u5f53\u524d\u4f4d\u7f6e' : '\u4e0a\u4e00\u7ad9')
  const safeTo = typeof toName === 'string' && toName.trim() ? toName.trim() : '\u5f53\u524d\u7ad9'
  return `${safeFrom} \u2192 ${safeTo}`
}

const buildConciseSegmentGuideSummary = guide => {
  const transportMode = typeof guide?.transportMode === 'string' && guide.transportMode.trim()
    ? guide.transportMode.trim()
    : ''
  const durationMinutes = Number(guide?.durationMinutes)
  const hasDuration = Number.isFinite(durationMinutes) && durationMinutes >= 0
  const distanceKm = formatSegmentGuideDistanceValue(guide?.distanceKm)
  const roads = extractSegmentGuideRoads(guide?.steps)

  const parts = []
  if (transportMode && hasDuration) {
    parts.push(`${transportMode}\u7EA6${durationMinutes}\u5206\u949F`)
  } else if (hasDuration) {
    parts.push(`\u7EA6${durationMinutes}\u5206\u949F`)
  } else if (transportMode) {
    parts.push(transportMode)
  }

  if (roads.length) {
    parts.push(`\u9014\u7ECF${roads.join('\u3001')}`)
  } else if (distanceKm) {
    parts.push(`\u7EA6${distanceKm}\u516C\u91CC`)
  }

  return parts.join('\uFF0C') || ''
}

const extractSegmentGuideRoads = steps => {
  if (!Array.isArray(steps) || !steps.length) return []
  const roads = []
  steps.forEach(step => {
    const text = String(step?.instruction || step?.lineName || '').trim()
    if (!text) return
    text
      .replace(/[\uFF0C,\u3002\uFF1B;\uFF08\uFF09()]/g, ' ')
      .split(/\s+/)
      .forEach(token => {
        let candidate = token
          .replace(/^(\u6CBF|\u8FDB\u5165|\u7ECF\u7531|\u9014\u7ECF|\u5411\u4E1C|\u5411\u897F|\u5411\u5357|\u5411\u5317|\u5F80)/, '')
          .replace(/(\u5411.*|\u884C\u9A76.*|\u6B65\u884C.*|\u76F4\u884C.*|\u5DE6\u8F6C.*|\u53F3\u8F6C.*|\u9760.*|\u5230\u8FBE.*|\u8FDB\u5165.*)$/, '')
          .trim()
        if (
          candidate &&
          /(?:\u8DEF|\u8857|\u5DF7|\u5927\u9053|\u5927\u8857|\u6865|\u96A7\u9053|\u5FEB\u901F\u8DEF|\u7ACB\u4EA4|\u8F85\u8DEF)$/.test(candidate) &&
          candidate.length >= 2 &&
          candidate.length <= 12 &&
          !roads.includes(candidate)
        ) {
          roads.push(candidate)
        }
      })
  })
  return roads.slice(0, 2)
}

export const formatSegmentGuideSummary = guide => {
  if (!guide || typeof guide !== 'object') {
    return '\u672C\u6BB5\u8DEF\u7EBF\u4FE1\u606F\u6574\u7406\u4E2D'
  }

  const concise = buildConciseSegmentGuideSummary(guide)
  if (concise) {
    return concise
  }

  if (typeof guide.summary === 'string' && guide.summary.trim()) {
    return guide.summary.trim()
  }

  return '\u672C\u6BB5\u8DEF\u7EBF\u4FE1\u606F\u6574\u7406\u4E2D'
}
