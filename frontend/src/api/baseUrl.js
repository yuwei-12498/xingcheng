const DEFAULT_API_BASE_URL = '/api'

function trimTrailingSlash(value) {
  return value.replace(/\/+$/, '')
}

export function normalizeApiBaseURL(rawValue = import.meta.env.VITE_API_BASE_URL) {
  const value = String(rawValue || DEFAULT_API_BASE_URL).trim()
  if (!value) {
    return DEFAULT_API_BASE_URL
  }

  if (value.startsWith('/')) {
    return trimTrailingSlash(value) || DEFAULT_API_BASE_URL
  }

  try {
    const parsed = new URL(value)
    if (parsed.protocol === 'https:') {
      return trimTrailingSlash(value)
    }
  } catch (err) {
    // Fall through to the safe same-origin default.
  }

  console.warn('Ignoring insecure or invalid VITE_API_BASE_URL; falling back to /api')
  return DEFAULT_API_BASE_URL
}

export const API_BASE_URL = normalizeApiBaseURL()

export function joinApiPath(path) {
  const normalizedPath = String(path || '').replace(/^\/+/, '')
  return normalizedPath ? `${API_BASE_URL}/${normalizedPath}` : API_BASE_URL
}
