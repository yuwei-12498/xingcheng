const SMALL_VIEWPORT_MAX = 991
const LOW_MEMORY_MAX = 4
const LOW_CORES_MAX = 4
const SLOW_CONNECTION_TYPES = new Set(['slow-2g', '2g', '3g'])

const toFiniteNumber = (value) => {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
}

export const getHeroRuntimeProfile = (env = {}) => {
  const viewportWidth = toFiniteNumber(env.viewportWidth)
  const deviceMemory = toFiniteNumber(env.deviceMemory)
  const hardwareConcurrency = toFiniteNumber(env.hardwareConcurrency)
  const effectiveType = String(env.connection?.effectiveType || '').toLowerCase()
  const saveData = Boolean(env.connection?.saveData)
  const prefersReducedMotion = Boolean(env.prefersReducedMotion)

  const isSmallViewport = viewportWidth !== null && viewportWidth <= SMALL_VIEWPORT_MAX
  const isLowMemory = deviceMemory !== null && deviceMemory <= LOW_MEMORY_MAX
  const isLowConcurrency = hardwareConcurrency !== null && hardwareConcurrency <= LOW_CORES_MAX
  const isSlowConnection = SLOW_CONNECTION_TYPES.has(effectiveType)

  const posterOnly = Boolean(
    prefersReducedMotion ||
    saveData ||
    isSlowConnection ||
    isSmallViewport ||
    isLowMemory ||
    isLowConcurrency
  )

  return {
    viewportWidth,
    deviceMemory,
    hardwareConcurrency,
    effectiveType,
    saveData,
    prefersReducedMotion,
    isSmallViewport,
    isLowMemory,
    isLowConcurrency,
    isSlowConnection,
    posterOnly,
    allowLottie: !posterOnly,
    allowSmoothScroll: !posterOnly,
  }
}

export const readHeroRuntimeProfile = (
  win = typeof window !== 'undefined' ? window : undefined,
  nav = typeof navigator !== 'undefined' ? navigator : undefined
) => {
  const prefersReducedMotion = Boolean(
    win?.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches
  )

  return getHeroRuntimeProfile({
    viewportWidth: win?.innerWidth,
    deviceMemory: nav?.deviceMemory,
    hardwareConcurrency: nav?.hardwareConcurrency,
    connection: nav?.connection,
    prefersReducedMotion,
  })
}

export const scheduleIdleTask = (callback, options = {}) => {
  const {
    timeout = 1200,
    target = typeof window !== 'undefined' ? window : globalThis,
  } = options

  if (typeof target?.requestIdleCallback === 'function') {
    const idleId = target.requestIdleCallback(() => {
      callback({
        didTimeout: false,
        timeRemaining: () => 0,
      })
    }, { timeout })

    return () => {
      if (typeof target?.cancelIdleCallback === 'function') {
        target.cancelIdleCallback(idleId)
      }
    }
  }

  const timerId = setTimeout(() => {
    callback({
      didTimeout: true,
      timeRemaining: () => 0,
    })
  }, Math.max(1, Number(timeout) || 1))

  return () => clearTimeout(timerId)
}

export const HERO_PERFORMANCE_THRESHOLDS = {
  SMALL_VIEWPORT_MAX,
  LOW_MEMORY_MAX,
  LOW_CORES_MAX,
}
