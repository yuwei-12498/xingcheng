import fs from 'node:fs'
import path from 'node:path'

const DEFAULT_PORT = 8082

function normalizePort(rawPort) {
  if (!/^\d+$/.test(rawPort)) {
    throw new Error(`backend port is invalid: ${rawPort}`)
  }

  const port = Number(rawPort)
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error(`backend port is invalid: ${rawPort}`)
  }
  return port
}

function findPortInEnvText(envText = '') {
  const match = envText.match(/^\s*(?:SERVER_PORT|DEV_BACKEND_PORT)\s*=\s*([^\r\n#]+)\s*$/m)
  if (!match) {
    return null
  }
  return normalizePort(match[1].trim())
}

export function resolveProxyTargetFromEnvText(envText = '') {
  const port = findPortInEnvText(envText)
  return `http://127.0.0.1:${port ?? DEFAULT_PORT}`
}

export function resolveProxyTargetFromRepoRoot(repoRoot) {
  const directEnvCandidates = [process.env.SERVER_PORT, process.env.DEV_BACKEND_PORT]
    .filter(value => typeof value === 'string' && value.trim())
  if (directEnvCandidates.length > 0) {
    return `http://127.0.0.1:${normalizePort(directEnvCandidates[0].trim())}`
  }

  const rootEnvPath = path.resolve(repoRoot, '.env')
  if (fs.existsSync(rootEnvPath)) {
    const rootEnvPort = findPortInEnvText(fs.readFileSync(rootEnvPath, 'utf8'))
    if (rootEnvPort !== null) {
      return `http://127.0.0.1:${rootEnvPort}`
    }
  }

  return `http://127.0.0.1:${DEFAULT_PORT}`
}
