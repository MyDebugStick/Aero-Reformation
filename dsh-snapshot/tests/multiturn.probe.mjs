/**
 * Multi-turn rollback probe.
 *
 * The engine probe (engine.probe.mjs) only ever rolls back ONCE per scenario.
 * Real use is chained: roll back, look around, roll back further, then move
 * forward again. That exercises paths a single rollback cannot:
 *
 *   - rolling back past turns that CREATED files (they must disappear)
 *   - rolling back past turns that DELETED files (they must come back)
 *   - rolling back past directory creation (the tree shape must revert)
 *   - rolling back, then editing, then rolling back again
 *   - rolling back to the newest note and then to the oldest
 *   - the manifest chain staying consistent across all of it
 *
 * Run: node tests/multiturn.probe.mjs
 */
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import assert from 'node:assert/strict'
import { SnapshotEngine } from '../lib/index.js'

const sandbox = fs.mkdtempSync(path.join(os.tmpdir(), 'dsh-snap-multi-'))
const workspace = path.join(sandbox, 'workspace')
fs.mkdirSync(workspace, { recursive: true })
const engine = new SnapshotEngine({ root: workspace, snapRoot: path.join(sandbox, '.snap-root'), log: () => {} })

const p = (rel) => path.join(workspace, rel)
const read = (rel) => (fs.existsSync(p(rel)) ? fs.readFileSync(p(rel), 'utf8') : null)
const exists = (rel) => fs.existsSync(p(rel))

function pass(label, detail = '') {
  process.stdout.write(`  PASS  ${label}${detail ? ` — ${detail}` : ''}\n`)
}

const notes = []
async function turn(n, mutate, label) {
  await engine.beginTurn(workspace)
  mutate()
  const note = await engine.endTurn(workspace, { turn: n, reason: { kind: 'completed' } })
  notes.push({ n, note, label })
  return note
}

// ---------------------------------------------------------------------------
// build a 10-turn history
// ---------------------------------------------------------------------------
fs.writeFileSync(p('seed.txt'), 'ORIGINAL\n')
fs.mkdirSync(p('src'), { recursive: true })
fs.writeFileSync(p('src/keep.java'), 'KEEP v1\n')

await turn(1, () => {
  fs.writeFileSync(p('src/keep.java'), 'KEEP v2\n')
}, 'modify keep.java')

await turn(2, () => {
  fs.writeFileSync(p('src/created-a.txt'), 'A\n')
  fs.writeFileSync(p('src/created-b.txt'), 'B\n')
}, 'create two files')

await turn(3, () => {
  fs.mkdirSync(p('pkg/deep/nested'), { recursive: true })
  fs.writeFileSync(p('pkg/deep/nested/c.txt'), 'C\n')
}, 'create a nested directory')

await turn(4, () => {
  fs.unlinkSync(p('seed.txt'))
}, 'delete seed.txt')

await turn(5, () => {
  fs.writeFileSync(p('src/created-a.txt'), 'A-EDITED\n')
  fs.mkdirSync(p('extra'), { recursive: true })
  fs.writeFileSync(p('extra/e.txt'), 'E\n')
}, 'edit created-a and add extra/')

await turn(6, () => {
  fs.writeFileSync(p('one.txt'), '1\n')
  fs.writeFileSync(p('two.txt'), '2\n')
  fs.writeFileSync(p('three.txt'), '3\n')
}, 'create three root files')

await turn(7, () => {
  fs.writeFileSync(p('src/keep.java'), 'KEEP v7\n')
}, 'modify keep.java again')

await turn(8, () => {
  fs.renameSync(p('src/created-b.txt'), p('src/renamed-b.txt'))
}, 'rename created-b -> renamed-b')

await turn(9, () => {
  fs.writeFileSync(p('src/created-a.txt'), 'A-EDITED-9\n')
  fs.unlinkSync(p('three.txt'))
}, 'edit created-a, delete three.txt')

await turn(10, () => {
  fs.writeFileSync(p('final.txt'), 'FINAL\n')
}, 'create final.txt')

const made = notes.filter((x) => x.note !== null).length
process.stdout.write(`\n  built ${made} notes across 10 turns\n`)
assert.equal(made, 10, 'every turn should produce a note')
pass('10-turn history built', `${made} notes`)

// ---------------------------------------------------------------------------
// MULTI-STEP ROLLBACK: walk backwards through the chain
// ---------------------------------------------------------------------------
const byTurn = (n) => notes.find((x) => x.n === n).note

// back to turn 10: everything present
let r = engine.revert({ root: workspace, id: byTurn(10).id })
assert.equal(r.ok, true, `revert to 10 failed: ${JSON.stringify(r.failed)}`)
assert.equal(read('final.txt'), 'FINAL\n')
assert.equal(read('src/renamed-b.txt'), 'B\n', 'the turn-8 rename must be in effect at turn 10')
assert.equal(exists('src/created-b.txt'), false, 'created-b no longer exists after the turn-8 rename')
pass('rollback -> turn 10', `restored ${r.restored.length}, removed ${r.removed.length}`)

// back to turn 9: final.txt must vanish
const repo = engine.openRepo(workspace)
r = engine.revert({ root: workspace, id: byTurn(9).id })
assert.equal(r.ok, true, `revert to 9 failed: ${JSON.stringify(r.failed)}`)
assert.equal(exists('final.txt'), false, 'final.txt was created at turn 10 and must be gone')
assert.equal(read('src/created-a.txt'), 'A-EDITED-9\n')
assert.equal(exists('three.txt'), false, 'three.txt was deleted at turn 9 and must stay gone')
pass('rollback -> turn 9 removed the turn-10 creation', `removed ${r.removed.length}`)

// back to turn 7: everything from 8..10 must be undone
r = engine.revert({ root: workspace, id: byTurn(7).id })
assert.equal(r.ok, true, `revert to 7 failed: ${JSON.stringify(r.failed)}`)
assert.equal(read('src/keep.java'), 'KEEP v7\n', 'keep.java must be the turn-7 revision')
assert.equal(exists('final.txt'), false, 'final.txt (turn 10) must be gone')
assert.equal(exists('src/created-a.txt'), true, 'created-a existed at turn 7')
assert.equal(read('src/created-a.txt'), 'A-EDITED\n', 'created-a must be the turn-5 revision, not the turn-9 one')
assert.equal(exists('src/created-b.txt'), true, 'created-b was renamed only at turn 8, so at turn 7 it exists')
assert.equal(exists('src/renamed-b.txt'), false, 'renamed-b must not exist at turn 7')
assert.equal(exists('three.txt'), true, 'three.txt existed at turn 7')
pass('rollback -> turn 7 undid the rename and later edits', `restored ${r.restored.length}, removed ${r.removed.length}`)

// the hard one: back to turn 3, past two creation-heavy turns and a deletion
const repo3 = engine.openRepo(workspace)
for (const t of [1, 2, 3, 4]) {
  const n = byTurn(t)
  const m = engine.readManifest(repo3, n.manifestHash)
  process.stdout.write(`    turn ${t}: ${JSON.stringify(Object.keys(m?.entries ?? {}))}  files=${JSON.stringify((n.files ?? []).map((f) => f.path))}\n`)
}
const m3 = engine.getManifestEntries(repo3, byTurn(3).manifestHash)
r = engine.revert({ root: workspace, id: byTurn(3).id })
assert.equal(r.ok, true, `revert to 3 failed: ${JSON.stringify(r.failed)}`)
assert.equal(exists('src/created-a.txt'), true, 'created-a was made at turn 2, so it exists at turn 3')
assert.equal(exists('src/created-b.txt'), true, 'created-b was made at turn 2, so it exists at turn 3')
assert.equal(exists('one.txt'), false, 'one.txt (turn 6) must be gone')
assert.equal(exists('extra/e.txt'), false, 'extra/e.txt (turn 5) must be gone')
assert.equal(read('seed.txt'), 'ORIGINAL\n', 'seed.txt was deleted at turn 4, so at turn 3 it exists')
assert.equal(read('src/keep.java'), 'KEEP v2\n', 'keep.java must be the turn-1 revision at turn 3')
assert.equal(read('pkg/deep/nested/c.txt'), 'C\n', 'the nested file created at turn 3 must be present')
pass('rollback -> turn 3 resurrected the deletion and dropped later creations')

// the earliest state that still has notes: turn 1
r = engine.revert({ root: workspace, id: byTurn(1).id })
assert.equal(r.ok, true, `revert to 1 failed: ${JSON.stringify(r.failed)}`)
assert.equal(read('src/keep.java'), 'KEEP v2\n')
assert.equal(exists('pkg'), false, 'the pkg/ tree was created at turn 3 and must be gone at turn 1')
assert.equal(read('seed.txt'), 'ORIGINAL\n')
pass('rollback -> turn 1 removed the whole later directory tree')

// ---------------------------------------------------------------------------
// FORWARD AGAIN: jump back to a late note after all that
// ---------------------------------------------------------------------------
r = engine.revert({ root: workspace, id: byTurn(10).id })
assert.equal(r.ok, true, `forward revert to 10 failed: ${JSON.stringify(r.failed)}`)
assert.equal(read('final.txt'), 'FINAL\n', 'turn 10 state must be reproducible after walking backwards')
assert.equal(read('src/created-a.txt'), 'A-EDITED-9\n')
assert.equal(exists('three.txt'), false)
assert.equal(exists('src/renamed-b.txt'), true)
assert.equal(exists('src/created-b.txt'), false)
pass('rollback forward to turn 10 reproduces it exactly', `restored ${r.restored.length}`)

// ---------------------------------------------------------------------------
// ROLLBACK, EDIT, ROLLBACK AGAIN (the chain must not be poisoned)
// ---------------------------------------------------------------------------
await engine.beginTurn(workspace)
fs.writeFileSync(p('post-rollback.txt'), 'SCRATCH\n')
const note11 = await engine.endTurn(workspace, { turn: 11 })

r = engine.revert({ root: workspace, id: byTurn(6).id })
assert.equal(r.ok, true, `revert to 6 after new work failed: ${JSON.stringify(r.failed)}`)
assert.equal(exists('post-rollback.txt'), false, 'the post-rollback scratch file must be undone')
assert.equal(exists('one.txt'), true, 'turn 6 state must still be reachable')
assert.equal(exists('final.txt'), false)
pass('rollback -> 6 after further edits still lands exactly')

r = engine.revert({ root: workspace, id: note11.id })
assert.equal(r.ok, true, `revert to 11 failed: ${JSON.stringify(r.failed)}`)
assert.equal(read('post-rollback.txt'), 'SCRATCH\n', 'the new note must still be reachable')
pass('rollback -> 11 restored the newest work again')

// ---------------------------------------------------------------------------
// undo-one-turn repeatedly (revert --before), walking back
// ---------------------------------------------------------------------------
r = engine.revertBefore({ root: workspace, id: note11.id })
assert.equal(r.ok, true, `undo turn 11 failed: ${JSON.stringify(r.failed)}`)
assert.equal(exists('post-rollback.txt'), false, 'undoing turn 11 removes its creation')
assert.equal(exists('final.txt'), true, 'turn 10 state is what turn 11 started from')
pass('undo turn 11 lands on the turn-10 state')

r = engine.revertBefore({ root: workspace, id: byTurn(10).id })
assert.equal(r.ok, true, `undo turn 10 failed: ${JSON.stringify(r.failed)}`)
assert.equal(exists('final.txt'), false, 'undoing turn 10 removes final.txt')
assert.equal(read('src/created-a.txt'), 'A-EDITED-9\n')
pass('undo turn 10 lands on the turn-9 state')

r = engine.revertBefore({ root: workspace, id: byTurn(9).id })
assert.equal(r.ok, true, `undo turn 9 failed: ${JSON.stringify(r.failed)}`)
assert.equal(read('src/created-a.txt'), 'A-EDITED\n', 'turn 9 edited created-a, so undoing it restores the turn-5 text')
assert.equal(exists('three.txt'), true, 'turn 9 deleted three.txt, so undoing it brings it back')
assert.equal(exists('final.txt'), false)
pass('undo turn 9 resurrected the deletion and reverted the edit')

// ---------------------------------------------------------------------------
// whole-chain integrity
// ---------------------------------------------------------------------------
const chain = engine.getManifestEntries(engine.openRepo(workspace), byTurn(10).manifestHash)
assert.equal(chain.broken, false, 'the manifest chain must still resolve')
for (const t of [10, 9, 7, 3, 1]) {
  const c = engine.getManifestEntries(engine.openRepo(workspace), byTurn(t).manifestHash)
  assert.equal(c.broken, false, `chain broken at turn ${t}`)
}
pass('manifest chain intact across every rollback target', `depth ${chain.chainLength}`)

const status = engine.status(workspace)
pass('repository status', `${status.noteCount} notes, ${status.objects} objects`)

fs.rmSync(sandbox, { recursive: true, force: true })
process.stdout.write('\nMulti-turn rollback probe complete.\n')
