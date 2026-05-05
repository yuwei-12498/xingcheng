const STORAGE_KEY = 'citytrip/current-itinerary-v2'

const WEEKDAY_MAP = {
  Monday: '\u5468\u4E00',
  Tuesday: '\u5468\u4E8C',
  Wednesday: '\u5468\u4E09',
  Thursday: '\u5468\u56DB',
  Friday: '\u5468\u4E94',
  Saturday: '\u5468\u516D',
  Sunday: '\u5468\u65E5'
}

function canUseStorage(type) {
  return typeof window !== 'undefined' && typeof window[type] !== 'undefined'
}

function emitItineraryUpdate(snapshot) {
  if (typeof window === 'undefined' || typeof window.dispatchEvent !== 'function') {
    return
  }
  try {
    window.dispatchEvent(new CustomEvent('citytrip:itinerary-updated', { detail: snapshot || null }))
  } catch (err) {
  }
}

function stripDeprecatedBusinessHints(value) {
  if (typeof value !== 'string' || !value.trim()) {
    return value
  }

  const cleaned = value
    .replace(/(?:部分点位)?营业状态更新时间较久，?(?:建议出发前|出发前建议|建议)?再次确认。?/g, '')
    .replace(/当前场馆状态超过\s*14\s*天未核验，请出发前再次确认。?/g, '')
    .replace(/(?:部分点位)?缺少完整营业时间信息，建议临行前核验。?/g, '')
    .replace(/营业时间信息不完整。?/g, '')
    .trim()

  return cleaned || null
}

function shouldKeepAlert(value) {
  if (typeof value !== 'string' || !value.trim()) {
    return false
  }

  return !/营业状态更新时间较久|当前场馆状态超过\s*14\s*天未核验|缺少完整营业时间信息|营业时间信息不完整/.test(value)
}


function hashString(value) {
  const source = String(value || '')
  let hash = 0
  for (let i = 0; i < source.length; i += 1) {
    hash = (hash << 5) - hash + source.charCodeAt(i)
    hash |= 0
  }
  return Math.abs(hash)
}

function isGenericAiStatusNote(value) {
  if (typeof value !== 'string' || !value.trim()) {
    return false
  }
  const markers = [
    'Data enriched on',
    'verify opening hours with official source',
    'hotspot crowd penalty boosted',
    '\u6570\u636E\u5DF2\u66F4\u65B0\u81F3',
    '\u70ED\u95E8\u70B9\u4F4D\u62E5\u6324\u5EA6\u8C03\u8282',
    '\u51CF\u5C11\u8DEF\u7EBF\u540C\u8D28\u5316'
  ]
  return markers.some(marker => value.includes(marker))
}

function buildFixedPoiTips(node) {
  const poiName = node?.poiName || '\u8BE5\u666F\u70B9'
  const category = node?.category || '\u5F53\u5730\u7279\u8272'
  const district = node?.district || '\u5468\u8FB9\u7247\u533A'

  const templates = [
    `${poiName}\u9002\u5408\u5148\u901B\u6838\u5FC3\u533A\u57DF\uFF0C\u518D\u8865\u62CD\u4EBA\u5C11\u89D2\u5EA6\uFF0C\u4F53\u9A8C\u4F1A\u66F4\u5B8C\u6574\u3002`,
    `${poiName}\u5EFA\u8BAE\u9884\u7559\u4E00\u70B9\u673A\u52A8\u65F6\u95F4\uFF0C\u70ED\u95E8\u65F6\u6BB5\u6392\u961F\u4F1A\u7565\u6709\u6CE2\u52A8\u3002`,
    `${poiName}\u8FD9\u7AD9\u4EE5${category}\u4F53\u9A8C\u4E3A\u4E3B\uFF0C\u5EFA\u8BAE\u5148\u770B\u4E3B\u8DEF\u7EBF\u518D\u81EA\u7531\u63A2\u7D22\u3002`,
    `\u5728${district}\u8FD9\u7247\u53EF\u4EE5\u628A${poiName}\u4F5C\u4E3A\u951A\u70B9\uFF0C\u5468\u8FB9\u987A\u8DEF\u88651-2\u4E2A\u5C0F\u70B9\u4F4D\u3002`,
    `${poiName}\u62CD\u7167\u5EFA\u8BAE\u907F\u5F00\u6574\u70B9\u4EBA\u6D41\u5CF0\u503C\uFF0C\u89C2\u611F\u548C\u51FA\u7247\u7387\u4F1A\u66F4\u7A33\u3002`,
    `${poiName}\u8FD9\u4E00\u7AD9\u5EFA\u8BAE\u8F7B\u88C5\u6162\u901B\uFF0C\u91CD\u70B9\u770B\u4EE3\u8868\u6027\u573A\u666F\u5373\u53EF\u3002`
  ]

  const seed = hashString(`${node?.poiId || ''}-${poiName}-${category}-${district}`)
  const tipCount = 3
  const picked = []
  const used = new Set()
  for (let i = 0; i < templates.length && picked.length < tipCount; i += 1) {
    const index = (seed + i * 5 + 3) % templates.length
    if (!used.has(index)) {
      used.add(index)
      picked.push(templates[index])
    }
  }
  return picked.length ? picked : templates.slice(0, tipCount)
}

function pickRandomTip(tips) {
  if (!Array.isArray(tips) || !tips.length) {
    return null
  }
  const index = Math.floor(Math.random() * tips.length)
  return tips[index]
}

export function getDefaultTripDate() {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function localizeItineraryText(value) {
  if (typeof value !== 'string' || !value.trim()) {
    return value
  }

  let text = value.trim()

  const exactMap = {
    'A more efficient route has been generated.': '\u5DF2\u4E3A\u4F60\u751F\u6210\u4E00\u7248\u66F4\u987A\u8DEF\u7684\u8DEF\u7EBF\u3002',
    'Current route is already close to optimal.': '\u5F53\u524D\u8DEF\u7EBF\u5DF2\u7ECF\u63A5\u8FD1\u6700\u4F18\u3002',
    'No valid route found for the current time window.': '\u5728\u5F53\u524D\u65F6\u95F4\u7A97\u53E3\u5185\u6CA1\u6709\u627E\u5230\u53EF\u6267\u884C\u7684\u8DEF\u7EBF\u3002',
    'Route reordered using travel time and opening-hour constraints.': '\u5DF2\u6839\u636E\u4EA4\u901A\u987A\u5E8F\u548C\u8425\u4E1A\u65F6\u95F4\u91CD\u65B0\u6574\u7406\u8DEF\u7EBF\u3002',
    'Route recalculated after replacing the selected poi.': '\u5DF2\u6839\u636E\u65B0\u70B9\u4F4D\u91CD\u7B97\u8DEF\u7EBF\u3002',
    'No executable route was found.': '\u6CA1\u6709\u627E\u5230\u53EF\u6267\u884C\u7684\u8DEF\u7EBF\u3002',
    'Try a wider time window or a different trip date.': '\u8BF7\u5C1D\u8BD5\u653E\u5BBD\u65F6\u95F4\u7A97\u53E3\u6216\u66F4\u6362\u51FA\u884C\u65E5\u671F\u3002',
    'Closed or unavailable venues were filtered out.': '\u7CFB\u7EDF\u5DF2\u4F18\u5148\u8FC7\u6EE4\u95ED\u9986\u6216\u6682\u4E0D\u53EF\u7528\u7684\u573A\u9986\u3002',
    'Business hours need confirmation.': '\u8425\u4E1A\u65F6\u95F4\u4FE1\u606F\u4E0D\u5B8C\u6574\uFF0C\u8BF7\u51FA\u53D1\u524D\u518D\u6B21\u786E\u8BA4\u3002',
    'Status is stale and should be rechecked.': '营业信息可能存在变动，建议临行前再快速确认一下。',
    'Temporarily closed.': '\u666F\u70B9\u5F53\u524D\u5904\u4E8E\u4E34\u65F6\u5173\u95ED\u72B6\u6001\u3002',
    'No itinerary to replan.': '\u5F53\u524D\u6CA1\u6709\u53EF\u91CD\u6392\u7684\u884C\u7A0B\u3002',
    'Route updated.': '\u5DF2\u66F4\u65B0\u4E3A\u65B0\u7684\u8DEF\u7EBF\u3002',
    'Route kept': '\u4FDD\u7559\u5F53\u524D\u8DEF\u7EBF',
    'Replan failed.': '\u91CD\u6392\u5931\u8D25\uFF0C\u8BF7\u7A0D\u540E\u91CD\u8BD5\u3002'
  }

  if (exactMap[text]) {
    return exactMap[text]
  }

  text = text.replace(/^Trip date:\s*([0-9-]+)/, '\u51FA\u884C\u65E5\u671F\uFF1A$1')
  text = text.replace(
    /^The route starts at (.+) and finishes at (.+), prioritizing stronger POIs with lower backtracking cost\.$/,
    '\u8DEF\u7EBF\u4ECE $1 \u5F00\u59CB\uFF0C\u5728 $2 \u6536\u675F\uFF0C\u4F18\u5148\u4E32\u8054\u4E86\u66F4\u9AD8\u5206\u4E14\u66F4\u987A\u8DEF\u7684\u70B9\u4F4D\u3002'
  )
  text = text.replace(/^theme match:\s*/i, '\u4E3B\u9898\u5339\u914D\uFF1A')
  text = text.replace(/\bfits companion type\b/gi, '\u9002\u5408\u540C\u884C\u7C7B\u578B')
  text = text.replace(/\bbetter score and lower detour cost\b/gi, '\u7EFC\u5408\u5F97\u5206\u66F4\u9AD8\u4E14\u6298\u8FD4\u6210\u672C\u66F4\u4F4E')
  text = text.replace(/Closed or unavailable venues were filtered out\./g, '\u7CFB\u7EDF\u5DF2\u4F18\u5148\u8FC7\u6EE4\u95ED\u9986\u6216\u6682\u4E0D\u53EF\u7528\u7684\u573A\u9986\u3002')
  text = text.replace(/Business hours need confirmation\./g, '\u8425\u4E1A\u65F6\u95F4\u4FE1\u606F\u4E0D\u5B8C\u6574\uFF0C\u8BF7\u51FA\u53D1\u524D\u518D\u6B21\u786E\u8BA4\u3002')
  text = text.replace(/Status is stale and should be rechecked\./g, '营业信息可能存在变动，建议临行前再快速确认一下。')
  text = text.replace(/Temporarily closed\./g, '\u666F\u70B9\u5F53\u524D\u5904\u4E8E\u4E34\u65F6\u5173\u95ED\u72B6\u6001\u3002')
  text = text.replace(/Wait about (\d+) minutes for opening time\./g, '\u5230\u8FBE\u540E\u9700\u7B49\u5F85\u7EA6 $1 \u5206\u949F\u5F00\u95E8\u3002')
  text = text.replace(/Closed on ([A-Za-z]+)\./g, (_, day) => `\u8BE5\u666F\u70B9\u5728${WEEKDAY_MAP[day] || day}\u4E0D\u5F00\u653E\u3002`)
  text = text.replace(/Replaced (.+) with a better nearby alternative\./g, '\u5DF2\u5C06 $1 \u66FF\u6362\u4E3A\u66F4\u5408\u9002\u5F53\u524D\u8DEF\u7EBF\u7684\u76F8\u8FD1\u70B9\u4F4D\u3002')
  text = text.replace(/The best feasible ordering did not change under the current constraints\./g, '\u5728\u5F53\u524D\u7EA6\u675F\u4E0B\uFF0C\u6700\u4F18\u7684\u7EC4\u5408\u548C\u987A\u5E8F\u57FA\u672C\u6CA1\u6709\u53D8\u5316\u3002')
  text = text.replace(/The new route reduces backtracking and rechecks venue availability\./g, '\u65B0\u8DEF\u7EBF\u964D\u4F4E\u4E86\u6298\u8FD4\u6210\u672C\uFF0C\u5E76\u91CD\u65B0\u6821\u9A8C\u4E86\u573A\u9986\u72B6\u6001\u3002')
  text = text.replace(/No better route was found under the current constraints\./g, '\u5728\u5F53\u524D\u6761\u4EF6\u4E0B\u6CA1\u6709\u66F4\u4F18\u8DEF\u7EBF\u4E86\u3002')
  text = text.replace(/No itinerary snapshot is available\. Generate or restore one first\./g, '\u6CA1\u6709\u53EF\u7528\u7684\u884C\u7A0B\u5FEB\u7167\uFF0C\u8BF7\u5148\u751F\u6210\u6216\u6062\u590D\u884C\u7A0B\u3002')
  text = text.replace(/Failed to load poi detail\./g, '\u52A0\u8F7D\u70B9\u4F4D\u8BE6\u60C5\u5931\u8D25\u3002')
  text = text.replace(/Stop replaced and route refreshed\./g, '\u70B9\u4F4D\u5DF2\u66FF\u6362\uFF0C\u884C\u7A0B\u4E5F\u540C\u6B65\u5237\u65B0\u4E86\u3002')
  text = text.replace(/Data enriched on\s*([0-9]{4}-[0-9]{2}-[0-9]{2})/gi, '\u6570\u636E\u5DF2\u66F4\u65B0\u81F3 $1')
  text = text.replace(
    /(?:[;；]\s*)?verify opening hours with official source\.?/gi,
    '\uFF1B\u51FA\u53D1\u524D\u8BF7\u4EE5\u5B98\u65B9\u4FE1\u606F\u4E3A\u51C6\u518D\u6B21\u786E\u8BA4\u8425\u4E1A\u65F6\u95F4\u3002'
  )
  text = text.replace(
    /Data enriched on\s*([0-9]{4}-[0-9]{2}-[0-9]{2})\s*[;；]\s*verify opening hours with official source\.?/gi,
    '\u6570\u636E\u5DF2\u66F4\u65B0\u81F3 $1\uFF1B\u51FA\u53D1\u524D\u8BF7\u4EE5\u5B98\u65B9\u4FE1\u606F\u4E3A\u51C6\u518D\u6B21\u786E\u8BA4\u8425\u4E1A\u65F6\u95F4\u3002'
  )
  text = text.replace(/hotspot crowd penalty boosted to reduce homogeneous routes\.?/gi, '\u7CFB\u7EDF\u5DF2\u542F\u7528\u70ED\u95E8\u70B9\u4F4D\u62E5\u6324\u5EA6\u8C03\u8282\uFF0C\u51CF\u5C11\u8DEF\u7EBF\u540C\u8D28\u5316\u3002')
  text = text.replace(/[；;]?\s*Data enriched on[\s\S]*$/i, '')
  text = text.replace(/([；;]\s*)?[A-Za-z][A-Za-z0-9 ,:'\-()\n]{10,}(?=[；;。.!?]|$)/g, '')
  text = text.replace(/\s{2,}/g, ' ').trim()
  text = text.replace(/[；;]\s*[；;]/g, '；')
  text = text.replace(/[；;]\s*$/g, '')
  text = text.replace(/;\s*/g, '\uFF1B')

  return text
}

export function normalizePoiDetail(detail) {
  if (!detail || typeof detail !== 'object') {
    return detail
  }

  return {
    ...detail,
    availabilityNote: localizeItineraryText(detail.availabilityNote),
    statusNote: stripDeprecatedBusinessHints(localizeItineraryText(detail.statusNote)),
    description: localizeItineraryText(detail.description)
  }
}

export function normalizeItinerarySnapshot(snapshot) {
  if (!snapshot || typeof snapshot !== 'object') {
    return snapshot
  }

  const normalizeNode = node => {
    const localizedStatusNote = stripDeprecatedBusinessHints(localizeItineraryText(node?.statusNote))
    const localizedSelectedWarmTip = localizeItineraryText(node?.selectedWarmTip)
    const localizedWarmTipCandidates = Array.isArray(node?.warmTipCandidates)
      ? node.warmTipCandidates.map(localizeItineraryText).filter(Boolean)
      : []

    let finalStatusNote = localizedStatusNote
    if (localizedSelectedWarmTip) {
      finalStatusNote = localizedSelectedWarmTip
    } else if ((!localizedStatusNote || isGenericAiStatusNote(localizedStatusNote)) && localizedWarmTipCandidates.length) {
      finalStatusNote = pickRandomTip(localizedWarmTipCandidates)
    } else if (isGenericAiStatusNote(localizedStatusNote)) {
      finalStatusNote = null
    }

    const selectedWarmTip = localizedSelectedWarmTip || (localizedWarmTipCandidates.includes(finalStatusNote) ? finalStatusNote : null)

    return {
      ...node,
      sysReason: localizeItineraryText(node?.sysReason),
      warmTipCandidates: localizedWarmTipCandidates,
      selectedWarmTip,
      statusNote: finalStatusNote
    }
  }

  return {
    ...snapshot,
    recommendReason: localizeItineraryText(snapshot.recommendReason),
    tips: localizeItineraryText(snapshot.tips),
    alerts: Array.isArray(snapshot.alerts)
      ? snapshot.alerts.map(localizeItineraryText).filter(shouldKeepAlert)
      : snapshot.alerts,
    options: Array.isArray(snapshot.options)
      ? snapshot.options.map(option => ({
          ...option,
          summary: localizeItineraryText(option?.summary),
          recommendReason: localizeItineraryText(option?.recommendReason),
          notRecommendReason: localizeItineraryText(option?.notRecommendReason),
          nodes: Array.isArray(option?.nodes) ? option.nodes.map(normalizeNode) : option?.nodes
        }))
      : snapshot.options,
    nodes: Array.isArray(snapshot.nodes)
      ? snapshot.nodes.map(normalizeNode)
      : snapshot.nodes
  }
}

export function saveItinerarySnapshot(snapshot) {
  if (!snapshot) {
    return
  }

  const normalized = normalizeItinerarySnapshot(snapshot)

  if (canUseStorage('localStorage')) {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(normalized))
  }
  if (canUseStorage('sessionStorage')) {
    window.sessionStorage.setItem('current_itinerary', JSON.stringify(normalized))
    if (normalized.originalReq) {
      window.sessionStorage.setItem('original_req_form', JSON.stringify(normalized.originalReq))
    }
  }

  emitItineraryUpdate(normalized)
}

export function loadItinerarySnapshot() {
  if (canUseStorage('localStorage')) {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (raw) {
      try {
        const parsed = normalizeItinerarySnapshot(JSON.parse(raw))
        saveItinerarySnapshot(parsed)
        return parsed
      } catch (err) {
        window.localStorage.removeItem(STORAGE_KEY)
      }
    }
  }

  if (canUseStorage('sessionStorage')) {
    const itineraryRaw = window.sessionStorage.getItem('current_itinerary')
    if (!itineraryRaw) {
      return null
    }
    try {
      const itinerary = normalizeItinerarySnapshot(JSON.parse(itineraryRaw))
      const reqRaw = window.sessionStorage.getItem('original_req_form')
      if (reqRaw && !itinerary.originalReq) {
        itinerary.originalReq = JSON.parse(reqRaw)
      }
      saveItinerarySnapshot(itinerary)
      return itinerary
    } catch (err) {
      return null
    }
  }

  return null
}

export function clearItinerarySnapshot() {
  if (canUseStorage('localStorage')) {
    window.localStorage.removeItem(STORAGE_KEY)
  }
  if (canUseStorage('sessionStorage')) {
    window.sessionStorage.removeItem('current_itinerary')
    window.sessionStorage.removeItem('original_req_form')
  }

  emitItineraryUpdate(null)
}
