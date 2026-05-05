const DEFAULT_AMAP_JS_API_URL = 'https://webapi.amap.com/maps'
const DEFAULT_AMAP_VERSION = '2.0'
const DEFAULT_AMAP_STYLE = 'amap://styles/f77ed9b76b4ac41027493d6a2c4427fb'
const AMAP_SCRIPT_ID = 'citytrip-amap-jsapi-v2'
const LEGACY_GREY_MAP_STYLES = new Set([
  'amap://styles/whitesmoke',
  'whitesmoke'
])

let amapLoaderPromise = null

const readEnvValue = name => {
  if (typeof import.meta === 'undefined' || !import.meta.env) {
    return ''
  }
  return String(import.meta.env[name] || '').trim()
}

export const resolveAmapJsConfig = (overrides = {}) => {
  const apiKey = overrides.apiKey ?? overrides.key ?? readEnvValue('VITE_AMAP_JS_API_KEY')
  const securityJsCode = overrides.securityJsCode ?? readEnvValue('VITE_AMAP_SECURITY_JS_CODE')
  const mapStyle = overrides.mapStyle ?? readEnvValue('VITE_AMAP_MAP_STYLE')
  const apiUrl = overrides.apiUrl ?? readEnvValue('VITE_AMAP_JS_API_URL')
  const version = overrides.version ?? readEnvValue('VITE_AMAP_JS_API_VERSION')

  return {
    apiKey: String(apiKey || '').trim(),
    securityJsCode: String(securityJsCode || '').trim(),
    mapStyle: normalizeAmapMapStyle(mapStyle),
    apiUrl: String(apiUrl || DEFAULT_AMAP_JS_API_URL).trim(),
    version: String(version || DEFAULT_AMAP_VERSION).trim()
  }
}

const normalizeAmapMapStyle = value => {
  const normalized = String(value || '').trim()
  if (!normalized) {
    return DEFAULT_AMAP_STYLE
  }
  if (LEGACY_GREY_MAP_STYLES.has(normalized.toLowerCase())) {
    return DEFAULT_AMAP_STYLE
  }
  return normalized
}

export const isAmapJsConfigured = (config = resolveAmapJsConfig()) => {
  return Boolean(config?.apiKey && config?.securityJsCode)
}

export const getAmapMapStyle = (config = resolveAmapJsConfig()) => {
  return normalizeAmapMapStyle(config?.mapStyle)
}

export const configureAmapSecurity = (config = resolveAmapJsConfig(), targetWindow = typeof window !== 'undefined' ? window : null) => {
  if (!targetWindow || !config?.securityJsCode) {
    return false
  }
  targetWindow._AMapSecurityConfig = {
    ...(targetWindow._AMapSecurityConfig || {}),
    securityJsCode: config.securityJsCode
  }
  return true
}

export const buildAmapJsApiUrl = ({ config = resolveAmapJsConfig(), plugins = [] } = {}) => {
  if (!config?.apiKey) {
    throw new Error('VITE_AMAP_JS_API_KEY is required to load AMap JS API v2')
  }
  const url = new URL(config.apiUrl || DEFAULT_AMAP_JS_API_URL)
  url.searchParams.set('v', config.version || DEFAULT_AMAP_VERSION)
  url.searchParams.set('key', config.apiKey)
  const normalizedPlugins = plugins.map(plugin => String(plugin || '').trim()).filter(Boolean)
  if (normalizedPlugins.length) {
    url.searchParams.set('plugin', normalizedPlugins.join(','))
  }
  return url.toString()
}

const waitForExistingScript = script => {
  if (window.AMap) {
    return Promise.resolve(window.AMap)
  }
  return new Promise((resolve, reject) => {
    script.addEventListener('load', () => resolve(window.AMap), { once: true })
    script.addEventListener('error', () => reject(new Error('Failed to load existing AMap JS API script')), { once: true })
  })
}

export const ensureAmapJsApiLoaded = ({ plugins = [] } = {}) => {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return Promise.reject(new Error('AMap JS API can only be loaded in browser runtime'))
  }
  if (window.AMap) {
    return Promise.resolve(window.AMap)
  }
  const config = resolveAmapJsConfig()
  if (!isAmapJsConfigured(config)) {
    return Promise.reject(new Error('VITE_AMAP_JS_API_KEY and VITE_AMAP_SECURITY_JS_CODE are required'))
  }
  configureAmapSecurity(config, window)

  const existing = document.getElementById(AMAP_SCRIPT_ID)
  if (existing) {
    return amapLoaderPromise || waitForExistingScript(existing)
  }
  if (amapLoaderPromise) {
    return amapLoaderPromise
  }

  amapLoaderPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.id = AMAP_SCRIPT_ID
    script.async = true
    script.defer = true
    script.src = buildAmapJsApiUrl({ config, plugins })
    script.onload = () => resolve(window.AMap)
    script.onerror = () => {
      amapLoaderPromise = null
      reject(new Error('Failed to load AMap JS API v2'))
    }
    document.head.appendChild(script)
  })
  return amapLoaderPromise
}
