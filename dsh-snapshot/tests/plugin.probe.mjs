/**
 * Integration probe for the PLUGIN surface (not just the engine).
 *
 * The engine probe tests `SnapshotEngine` directly, which never exercises the
 * cordis wiring: `apply(ctx)`, the `session/event` switch, the `fs/observed`
 * listener, and the async turn lifecycle. A deployed host reported zero notes
 * despite a busy session, and only this level can reproduce that.
 *
 * A minimal fake Context is used: `on` records listeners, `inject` runs its
 * callback immediately with a stubbed webserver, `effect` records a disposer.
 *
 * Run: node tests/plugin.probe.mjs
 */
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import assert from 'node:assert/strict'
import { apply } from '../lib/index.js'

const sandbox = fs.mkdtempSync(path.join(os.tmpdir(), 'dsh-snap-plugin-'))
const workspace = path.join(sandbox, 'workspace')
const snapRoot = path.join(sandbox, '.snap-root')
fs.mkdirSync(workspace, { recursive: true })
process.env.DSH_SNAPSHOT_ROOT = snapRoot

const target = path.join(workspace, 'Main.java')
fs.writeFileSync(target, 'class Main {}\n')

const listeners = new Map()
const logs = []
const registeredRoutes = []

const ctx = {
  logger: {
    info: (m) => logs.push(`INFO ${m}`),
    warn: (m) => logs.push(`WARN ${m}`),
    error: (m) => logs.push(`ERROR ${m}`),
  },
  on(name, handler) {
    if (!listeners.has(name)) listeners.set(name, [])
    listeners.get(name).push(handler)
    return () => {}
  },
  inject(names, callback) {
    callback({
      webServer: {
        register(route) {
          registeredRoutes.push(route.path)
          return () => {}
        },
      },
    })
  },
  effect(callback) {
    callback()
  },
  get(name) {
    if (name === 'sandboxPolicy') return { resolve: () => ({ workspaceRoot: workspace }) }
    return undefined
  },
}

function emit(name, ...args) {
  for (const handler of listeners.get(name) ?? []) handler(...args)
}

function pass(label, detail = '') {
  process.stdout.write(`  PASS  ${label}${detail ? ` — ${detail}` : ''}\n`)
}

apply(ctx)
pass('apply() registered listeners', [...listeners.keys()].join(', '))
pass('apply() registered HTTP routes', registeredRoutes.length > 0 ? `${registeredRoutes.length} routes` : 'NONE')

// The host resolves the workspace root through the sandbox policy; emulate a
// real session object with an id so the turn map keys the same way.
const session = { id: 'probe-session' }

emit('session/event', session, { type: 'turn/start', data: { turn: 1 } })
// `beginTurn` is async and fire-and-forget from the listener; let it settle.
await new Promise((r) => setTimeout(r, 400))

fs.writeFileSync(target, 'class Main { int x; }\n')
emit('fs/observed', { displayPath: target }, { kind: 'present', version: 'v2' }, { name: 'write' })

emit('session/event', session, {
  type: 'tool/call',
  data: { turn: 1, step: 1, callId: 'c1', name: 'write', arguments: JSON.stringify({ file_path: 'Main.java' }) },
})
emit('session/event', session, {
  type: 'assistant/message',
  data: { turn: 1, step: 1, message: { content: [{ type: 'text', text: 'wrote Main.java' }] } },
})
emit('session/event', session, { type: 'turn/end', data: { turn: 1, reason: { kind: 'completed' } } })
await new Promise((r) => setTimeout(r, 600))

const repoDirs = fs.existsSync(snapRoot) ? fs.readdirSync(snapRoot) : []
process.stdout.write(`\n  snap root: ${snapRoot}\n  repos: ${JSON.stringify(repoDirs)}\n`)

let noteCount = 0
let manifestCount = 0
for (const id of repoDirs) {
  const notesDir = path.join(snapRoot, id, 'notes')
  const manifestsDir = path.join(snapRoot, id, 'manifests')
  if (fs.existsSync(notesDir)) noteCount += fs.readdirSync(notesDir).filter((f) => f.endsWith('.json')).length
  if (fs.existsSync(manifestsDir)) manifestCount += fs.readdirSync(manifestsDir).length
}

process.stdout.write(`  notes: ${noteCount}   manifests: ${manifestCount}\n`)
process.stdout.write(`  logs:\n${logs.map((l) => `    ${l}`).join('\n')}\n\n`)

assert.ok(registeredRoutes.length > 0, 'HTTP routes must register')
assert.ok(noteCount > 0, `a completed turn that changed a file must produce a note (got ${noteCount})`)
assert.ok(manifestCount > 0, `a completed turn must produce a manifest (got ${manifestCount})`)
pass('plugin surface produces a note end-to-end')

fs.rmSync(sandbox, { recursive: true, force: true })
