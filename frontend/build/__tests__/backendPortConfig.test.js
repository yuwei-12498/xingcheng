import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const testDir = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(testDir, '..', '..', '..')
const backendConfigPath = path.join(repoRoot, 'backend', 'src', 'main', 'resources', 'application.yml')

test('backend application.yml prefers SERVER_PORT before 8082 fallback', () => {
  const source = fs.readFileSync(backendConfigPath, 'utf8')
  assert.match(source, /port:\s*\$\{SERVER_PORT:8082\}/)
})
