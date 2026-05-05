const normalizeTime = value => (typeof value === 'string' && value.trim() ? value.trim() : null)

export function deriveEditableDayWindows(snapshot) {
  const source = Array.isArray(snapshot?.dayWindows) && snapshot.dayWindows.length
    ? snapshot.dayWindows
    : buildFallbackDayWindows(snapshot)

  return source.map(item => ({
    dayNo: Number(item?.dayNo) || 1,
    startTime: normalizeTime(item?.startTime) || '09:00',
    endTime: normalizeTime(item?.endTime) || '18:00'
  }))
}

export function deriveEditableStops(snapshot) {
  const nodes = Array.isArray(snapshot?.nodes) ? snapshot.nodes : []
  return nodes.map((node, index) => ({
    nodeKey: node?.nodeKey || `node-${index + 1}`,
    poiName: node?.poiName || `节点 ${index + 1}`,
    dayNo: Number(node?.dayNo) || 1,
    order: Number(node?.stepOrder) || index + 1,
    stayDuration: Number(node?.stayDuration) || 0,
    removed: false
  }))
}

export function buildItineraryEditPayload({ originalDayWindows, dayWindows, originalStops, stops, customRows }) {
  const operations = []

  const originalStopMap = new Map((originalStops || []).map(item => [item.nodeKey, item]))
  const remainingStops = []
  for (const stop of stops || []) {
    const original = originalStopMap.get(stop.nodeKey)
    if (!original) continue
    if (stop.removed) {
      operations.push({ type: 'remove_node', nodeKey: stop.nodeKey })
      continue
    }
    if (Number(stop.stayDuration) !== Number(original.stayDuration)) {
      operations.push({
        type: 'update_stay',
        nodeKey: stop.nodeKey,
        stayDuration: Number(stop.stayDuration) || 0
      })
    }
    remainingStops.push({
      ...stop,
      dayNo: Number(stop.dayNo) || 1,
      order: Number(stop.order) || 1
    })
  }

  const originalWindowMap = new Map((originalDayWindows || []).map(item => [Number(item.dayNo), item]))
  for (const window of dayWindows || []) {
    const current = originalWindowMap.get(Number(window.dayNo))
    const startTime = normalizeTime(window.startTime)
    const endTime = normalizeTime(window.endTime)
    if (!current || startTime !== normalizeTime(current.startTime) || endTime !== normalizeTime(current.endTime)) {
      operations.push({
        type: 'update_day_window',
        dayNo: Number(window.dayNo) || 1,
        startTime,
        endTime
      })
    }
  }

  const orderedStops = remainingStops
    .slice()
    .sort((left, right) => {
      if (left.dayNo !== right.dayNo) return left.dayNo - right.dayNo
      if (left.order !== right.order) return left.order - right.order
      return left.poiName.localeCompare(right.poiName)
    })

  orderedStops.forEach((stop, index) => {
    const original = originalStopMap.get(stop.nodeKey)
    const targetIndex = (orderedStops.filter(item => item.dayNo === stop.dayNo).findIndex(item => item.nodeKey === stop.nodeKey) + 1) || 1
    if (!original || Number(original.dayNo) !== stop.dayNo || Number(original.order) !== targetIndex) {
      operations.push({
        type: 'move_node',
        nodeKey: stop.nodeKey,
        targetDayNo: stop.dayNo,
        targetIndex
      })
    }
  })

  const inlineRows = (customRows || [])
    .filter(item => item && Number(item.dayNo) > 0 && Number(item.order) > 0)
    .slice()
    .sort((left, right) => {
      if (left.dayNo !== right.dayNo) return left.dayNo - right.dayNo
      return left.order - right.order
    })

  inlineRows.forEach(row => {
    if (row.mode === 'existing' && row.customPoiId) {
      operations.push({
        type: 'insert_saved_custom_poi',
        dayNo: Number(row.dayNo),
        targetIndex: Number(row.order),
        stayDuration: Number(row.stayDuration) || 0,
        customPoiId: row.customPoiId
      })
      return
    }
    operations.push({
      type: 'insert_inline_custom_poi',
      dayNo: Number(row.dayNo),
      targetIndex: Number(row.order),
      stayDuration: Number(row.stayDuration) || 0,
      customPoiDraft: {
        name: row.name,
        roughLocation: row.roughLocation,
        category: row.category,
        reason: row.reason
      }
    })
  })

  return { operations }
}

function buildFallbackDayWindows(snapshot) {
  const nodes = Array.isArray(snapshot?.nodes) ? snapshot.nodes : []
  const grouped = new Map()
  for (const node of nodes) {
    const dayNo = Number(node?.dayNo) || 1
    if (!grouped.has(dayNo)) {
      grouped.set(dayNo, [])
    }
    grouped.get(dayNo).push(node)
  }

  if (!grouped.size) {
    return [{
      dayNo: 1,
      startTime: normalizeTime(snapshot?.originalReq?.startTime) || '09:00',
      endTime: normalizeTime(snapshot?.originalReq?.endTime) || '18:00'
    }]
  }

  return Array.from(grouped.entries())
    .sort((left, right) => left[0] - right[0])
    .map(([dayNo, items]) => ({
      dayNo,
      startTime: normalizeTime(items[0]?.startTime) || normalizeTime(snapshot?.originalReq?.startTime) || '09:00',
      endTime: normalizeTime(items[items.length - 1]?.endTime) || normalizeTime(snapshot?.originalReq?.endTime) || '18:00'
    }))
}
