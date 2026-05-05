import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'

import { resolveProxyTargetFromEnvText, resolveProxyTargetFromRepoRoot } from '../resolveDevProxyTarget.js'

test('uses DEV_BACKEND_PORT from env text', () => {
  assert.equal(
    resolveProxyTargetFromEnvText('DEV_BACKEND_PORT=18080'),
    'http://127.0.0.1:18080'
  )
})

test('uses SERVER_PORT from env text', () => {
  assert.equal(
    resolveProxyTargetFromEnvText('SERVER_PORT=19090'),
    'http://127.0.0.1:19090'
  )
})

test('falls back to 8082 when backend port is missing', () => {
  assert.equal(
    resolveProxyTargetFromEnvText('HOST_HTTP_PORT=18080'),
    'http://127.0.0.1:8082'
  )
})

test('throws when backend port is invalid', () => {
  assert.throws(
    () => resolveProxyTargetFromEnvText('DEV_BACKEND_PORT=abc'),
    /backend port/i
  )
})

test('uses repo root .env as the single backend port source', () => {
  const tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'citytrip-port-'))
  fs.writeFileSync(path.join(tempRoot, '.env'), 'SERVER_PORT=8082\n', 'utf8')

  assert.equal(
    resolveProxyTargetFromRepoRoot(tempRoot),
    'http://127.0.0.1:8082'
  )
})
