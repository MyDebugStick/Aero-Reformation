/**
 * Stress probe: measure the snapshot engine at a realistic workspace size.
 *
 * The engine probe proves correctness on 4 files. This one answers the
 * questions that only show up at scale:
 *   - how long a baseline scan takes over ~2000 files;
 *   - whether the mtime fast path actually avoids re-reading an unchanged tree;
 *   - what a turn costs when 1 file changes vs 200 files change;
 *   - whether the chunk layer keeps a large-file edit cheap;
 *   - what gc costs and reclaims;
 *   - whether rollback still returns exact bytes at this size.
 *
 * Everything runs in the system temp dir; the real workspace is untouched.
 *
 * Run: node tests/stress.probe.mjs [fileCount]
 */
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import assert from 'node:assert/strict'
import crypto from 'node:crypto'
import { SnapshotEngine } from '../lib/index.js'

const FILE_COUNT = Number(process.argv[2] ?? 2000)
const sandbox = fs.mkdtempSync(path.join(os.tmpdir(), 'dsh-snap-stress-'))
const workspace = path.join(sandbox, 'workspace')
const snapRoot = path.join(sandbox, '.snap-root')
fs.mkdirSync(workspace, { recursive: true })

const ms = (start) => Number(process.hrtime.bigint() - start) / 1e6
const now = () => process.hrtime.bigint()
const fmt = (n) => `${n.toFixed(0)} ms`

function pass(label, detail = '') {
  process.stdout.write(`  PASS  ${label}${detail ? ` — ${detail}` : ''}\n`)
}

// ---- build a realistic tree -------------------------------------------------
// Deep-ish package structure with mixed file sizes, plus build output that must
// be excluded so it never enters the index.
process.stdout.write(`Building a ${FILE_COUNT}-file workspace...\n`)
const t0 = now()
for (let i = 0; i < FILE_COUNT; i += 1) {
  const dir = path.join(workspace, 'src', `pkg${i % 40}`, `sub${i % 7}`)
  fs.mkdirSync(dir, { recursive: true })
  // ~1-3 KB of plausible source text.
  const body = `package pkg${i % 40};\n\npublic class C${i} {\n${Array.from({ length: 30 }, (_, k) => `  void m${k}() { int v = ${k}; }`).join('\n')}\n}\n`
  fs.writeFileSync(path.join(dir, `C${i}.java`), body)
}
// Excluded noise that must NOT be indexed.
fs.mkdirSync(path.join(workspace, 'build', 'classes'), { recursive: true })
for (let i = 0; i < 300; i += 1) fs.writeFileSync(path.join(workspace, 'build', 'classes', `x${i}.class`), 'JUNK'.repeat(500))
fs.mkdirSync(path.join(workspace, 'node_modules', 'dep'), { recursive: true })
for (let i = 0; i < 300; i += 1) fs.writeFileSync(path.join(workspace, 'node_modules', 'dep', `m${i}.js`), 'module.exports = 1\n')
process.stdout.write(`  built in ${fmt(ms(t0))}\n\n`)

const engine = new SnapshotEngine({ root: workspace, snapRoot, log: () => {} })
const repo = engine.openRepo(workspace)
const objectBytes = () => engine.countObjects(repo).bytes
const objectCount = () => engine.countObjects(repo).total

// ---- 1. cold baseline -------------------------------------------------------
let t = now()
await engine.beginTurn(workspace)
const coldScan = ms(t)
const indexed = Object.keys(repo.state.files).length
assert.equal(indexed, FILE_COUNT, `only source files should be indexed (got ${indexed}, expected ${FILE_COUNT})`)
pass('cold baseline scan', `${indexed} files in ${fmt(coldScan)} — build/ and node_modules/ excluded`)
const baselineBytes = objectBytes()

// ---- 2. warm baseline (mtime fast path) ------------------------------------
t = now()
await engine.beginTurn(workspace)
const warmScan = ms(t)
pass('warm baseline scan (nothing changed)', fmt(warmScan))
assert.ok(
  warmScan < coldScan,
  `an unchanged tree must scan faster than the cold pass (cold ${fmt(coldScan)}, warm ${fmt(warmScan)})`,
)

// ---- 3. a one-file turn -----------------------------------------------------
t = now()
fs.writeFileSync(path.join(workspace, 'src', 'pkg0', 'sub0', 'C0.java'), 'package pkg0;\n\npublic class C0 { int changed; }\n')
const note1 = await engine.endTurn(workspace, { turn: 1 })
const oneFileTurn = ms(t)
assert.ok(note1 !== null && note1.changedCount === 1, `one edited file must yield one file note (got ${note1?.changedCount})`)
pass('one-file turn', `${fmt(oneFileTurn)}, +${objectBytes() - baselineBytes} B stored`)

// ---- 4. a 200-file turn -----------------------------------------------------
// NOTE the ordering: the baseline must be captured BEFORE the edits. Calling
// beginTurn after mutating the tree makes the new content the baseline, and the
// turn legitimately reports no change — that mistake looked exactly like a
// missing-note bug while writing this probe.
const before200 = objectBytes()
const before200Objects = objectCount()
await engine.beginTurn(workspace)
t = now()
for (let i = 0; i < 200; i += 1) {
  const dir = path.join(workspace, 'src', `pkg${i % 40}`, `sub${i % 7}`)
  fs.appendFileSync(path.join(dir, `C${i}.java`), `// touched ${Date.now()}\n`)
}
const note2 = await engine.endTurn(workspace, { turn: 2 })
const twoHundredTurn = ms(t)
assert.ok(note2 !== null, 'a 200-file turn must yield a note')
assert.equal(note2.changedCount, 200, `expected 200 changed files, got ${note2.changedCount}`)
const grew = objectBytes() - before200
pass(
  '200-file turn',
  `${fmt(twoHundredTurn)}, +${grew} B, +${objectCount() - before200Objects} objects`,
)

// ---- 4b. same-size, same-tick rewrite --------------------------------------
// The nastiest fast-path case: identical length, mtime possibly identical.
await engine.beginTurn(workspace)
const sameSizePath = path.join(workspace, 'src', 'pkg0', 'sub0', 'C0.java')
const beforeBody = fs.readFileSync(sameSizePath, 'utf8')
fs.writeFileSync(sameSizePath, beforeBody.replace('int v', 'int w'))
const noteSameSize = await engine.endTurn(workspace, { turn: '2b' })
assert.ok(noteSameSize !== null, 'a same-size rewrite must still be detected')
assert.equal(noteSameSize.changedCount, 1, 'exactly the rewritten file must be noted')
pass('same-size rewrite detected', 'content hash, not just mtime+size')

// ---- 5. large-file chunk increment -----------------------------------------
// A 1 MiB file edited in its first 64 KiB chunk must not cost 1 MiB.
const bigPath = path.join(workspace, 'src', 'pkg0', 'Big.java')
fs.writeFileSync(bigPath, 'A'.repeat(64 * 1024) + 'B'.repeat(15 * 64 * 1024))
await engine.beginTurn(workspace)
await engine.endTurn(workspace, { turn: 3 })
const beforeBig = objectBytes()
await engine.beginTurn(workspace)
t = now()
fs.writeFileSync(bigPath, 'Z'.repeat(64 * 1024) + 'B'.repeat(15 * 64 * 1024))
const note4 = await engine.endTurn(workspace, { turn: 4 })
const bigTurn = ms(t)
const bigGrew = objectBytes() - beforeBig
const bigEntry = note4?.files.find((f) => f.path.endsWith('Big.java'))
assert.ok(bigEntry?.chunked === true, 'the 1 MiB file must use the chunk layer')
assert.ok(bigGrew < 200 * 1024, `editing one chunk of a 1 MiB file must stay cheap, grew ${bigGrew} B`)
pass('1 MiB file, one 64 KiB chunk edited', `${fmt(bigTurn)}, +${bigGrew} B (vs ${1024 * 1024} B whole-file)`)

// ---- 6. notes volume --------------------------------------------------------
const notes = engine.listNotes(workspace, 100)
pass('notes recorded', `${notes.length} notes, latest changedCount=${notes[0]?.changedCount}`)

// ---- 7. gc ------------------------------------------------------------------
const beforeGc = objectCount()
t = now()
const gc = engine.gc(workspace)
const gcMs = ms(t)
assert.equal(gc.ok, true)
pass('gc', `${fmt(gcMs)}, kept ${gc.kept}, removed ${gc.removed} (of ${beforeGc})`)

// ---- 8. rollback still exact after gc --------------------------------------
// Careful with the expectation: note 2 IS the 200-file turn, so its own state
// still carries the `touched` line. The right assertion is byte equality with
// what the note's manifest resolves to — not "the touched marker is gone".
const target = path.join(workspace, 'src', 'pkg0', 'sub0', 'C0.java')
const note2Body = (() => {
  const d = engine.getManifestEntries(repo, note2.manifestHash).entries['src\\pkg0\\sub0\\C0.java']
  const buf = d === undefined || d === null ? null : engine.getBuffer(repo, d.hash)
  return buf === null ? null : buf.toString('utf8')
})()
// Guarantee the tree has diverged from note 2 before rolling back.
fs.appendFileSync(target, '// diverged\n')
t = now()
const toNote2 = engine.revert({ root: workspace, id: note2.id })
const revertMs = ms(t)
assert.equal(toNote2.ok, true, `rollback must succeed after gc: ${JSON.stringify(toNote2.failed)}`)
const afterRollback = fs.readFileSync(target, 'utf8')
assert.equal(afterRollback, note2Body, 'the file must return to exactly what note 2 recorded')
assert.ok(!afterRollback.includes('diverged'), 'the later divergence must be rolled back')
pass('rollback after gc', `${fmt(revertMs)}, restored ${toNote2.restored.length} paths, bytes exact`)

// ---- 9. storage efficiency --------------------------------------------------
const total = objectBytes()
const sourceBytes = (() => {
  let sum = 0
  const walk = (dir) => {
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
      const p = path.join(dir, e.name)
      if (e.isDirectory()) walk(p)
      else if (e.name.endsWith('.java')) sum += fs.statSync(p).size
    }
  }
  walk(path.join(workspace, 'src'))
  return sum
})()
process.stdout.write(
  `\n  source tree: ${(sourceBytes / 1024).toFixed(0)} KiB across ${FILE_COUNT} files\n` +
    `  repo total : ${(total / 1024 / 1024).toFixed(2)} MiB in ${objectCount()} objects\n` +
    `  latest manifest chain length: ${(engine.getManifestEntries(repo, repo.state.head).chainLength)}\n\n`,
)
pass('storage accounting reported')

fs.rmSync(sandbox, { recursive: true, force: true })
process.stdout.write(`Stress probe complete (${FILE_COUNT} files).\n`)
