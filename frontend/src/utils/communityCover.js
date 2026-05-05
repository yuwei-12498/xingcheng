export const DEFAULT_COMMUNITY_COVER = '/community-covers/v2/cover-citywalk.svg'
export const LEGACY_COMMUNITY_COVER = '/community-cover.svg'
const LEGACY_PRESET_PREFIX = '/community-covers/'
const VERSIONED_PRESET_PREFIX = '/community-covers/v2/'

export const COMMUNITY_COVER_PRESETS = [
  '/community-covers/v2/cover-citywalk.svg',
  '/community-covers/v2/cover-food.svg',
  '/community-covers/v2/cover-night.svg',
  '/community-covers/v2/cover-nature.svg',
  '/community-covers/v2/cover-culture.svg',
  '/community-covers/v2/cover-shopping.svg'
]

const stableHash = value => {
  const seed = `${value || ''}`.trim()
  if (!seed) return 0
  let hash = 0
  for (let index = 0; index < seed.length; index += 1) {
    hash = ((hash << 5) - hash + seed.charCodeAt(index)) | 0
  }
  return Math.abs(hash)
}

export const pickCommunityCoverPreset = seed => {
  const index = stableHash(seed) % COMMUNITY_COVER_PRESETS.length
  return COMMUNITY_COVER_PRESETS[index] || DEFAULT_COMMUNITY_COVER
}

export const resolveCommunityCover = (value, seed) => {
  if (typeof value !== 'string') {
    return pickCommunityCoverPreset(seed)
  }
  const trimmed = value.trim()
  if (!trimmed || trimmed === LEGACY_COMMUNITY_COVER) {
    return pickCommunityCoverPreset(seed)
  }
  if (trimmed.startsWith(LEGACY_PRESET_PREFIX)
    && !trimmed.startsWith(VERSIONED_PRESET_PREFIX)
    && /^\/community-covers\/cover-[a-z-]+\.svg$/.test(trimmed)) {
    return trimmed.replace(LEGACY_PRESET_PREFIX, VERSIONED_PRESET_PREFIX)
  }
  return trimmed
}

export const applyCommunityCoverFallback = event => {
  const target = event?.currentTarget
  if (!target || target.src?.endsWith(DEFAULT_COMMUNITY_COVER)) {
    return
  }
  target.src = DEFAULT_COMMUNITY_COVER
}
