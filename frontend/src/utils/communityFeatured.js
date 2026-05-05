export function buildFeaturedPosts(source = {}, limit = 5) {
  const pinned = Array.isArray(source.pinnedRecords) ? source.pinnedRecords : []
  const records = Array.isArray(source.records) ? source.records : []
  const seen = new Set()
  const result = []

  const pushItem = (item, fallbackLabel) => {
    if (!item?.id || seen.has(item.id) || result.length >= limit) {
      return
    }

    seen.add(item.id)
    result.push({
      ...item,
      featuredLabel: item.featuredLabel || (item.globalPinned ? '管理员精选' : fallbackLabel)
    })
  }

  pinned.forEach(item => pushItem(item, '管理员精选'))
  records.forEach(item => pushItem(item, '社区推荐'))

  return result
}
