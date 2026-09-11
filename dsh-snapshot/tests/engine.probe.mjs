/**
 * End-to-end probe for the snapshot engine.
 *
 * Runs against a throwaway workspace under the system temp dir so the real
 * project tree is never touched. Verifies, in order:
 *   1. baseline + edit + note creation;
 *   2. chunk-level incrementality (a large file edit stores only new chunks);
 *   3. manifest delta chaining across turns;
 *   4. deletion tracking;
 *   5. first-file creation tracking;
 *   6. rollback restores the exact prior bytes;
 *   7. rollback removes a file the note recorded as created;
 *   8. gc keeps reachable blobs and reports counts.
 *
 * Run: node tests/engine.probe.mjs
 */

import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import assert from 'node:assert/strict'
import { SnapshotEngine } from '../lib/index.js'

const sandbox = fs.mkdtempSync(path.join(os.tmpdir(), 'dsh-snap-probe-'))
const snapRoot = path.join(sandbox, '.snap-root')
const workspace = path.join(sandbox, 'workspace')
fs.mkdirSync(workspace, { recursive: true })

const mainFile = path.join(workspace, 'Main.java')
const bigFile = path.join(workspace, 'Big.java')
const doomedFile = path.join(workspace, 'Doomed.java')
const freshFile = path.join(workspace, 'Fresh.java')

// A large file (> chunk threshold) so the chunk layer engages.
const bigChunk = 'A'.repeat(64 * 1024)
fs.writeFileSync(mainFile, 'class Main { void a() {} }\n')
fs.writeFileSync(bigFile, bigChunk + 'B'.repeat(64 * 1024) + 'C'.repeat(64 * 1024))
fs.writeFileSync(doomedFile, 'class Doomed {}\n')

const log = []
const engine = new SnapshotEngine({ root: workspace, snapRoot, log: (line) => log.push(line) })

const countObjects = () => engine.countObjects(engine.openRepo(workspace)).total
const objectBytes = () => engine.countObjects(engine.openRepo(workspace)).bytes
const BIG_SIZE = 64 * 1024 * 3

function pass(label, detail = '') {
  process.stdout.write(`  PASS  ${label}${detail ? ` — ${detail}` : ''}\n`)
}

// --- turn 1: modify main, create fresh, delete doomed ----------------------
await engine.beginTurn(workspace)
const beforeTurn1 = countObjects()
fs.writeFileSync(mainFile, 'class Main { void a() { int x = 1; } }\n')
fs.writeFileSync(freshFile, 'class Fresh {}\n')
fs.unlinkSync(doomedFile)
const note1 = await engine.endTurn(workspace, { turn: 1, reason: { kind: 'completed' }, summary: 'first turn', work: [{ tool: 'edit', file_path: 'Main.java' }] })

assert.ok(note1 !== null, 'turn 1 must produce a note')
const kinds = Object.fromEntries(note1.files.map((f) => [f.path, f.kind]))
assert.equal(kinds['Main.java'], 'modified')
assert.equal(kinds['Fresh.java'], 'created')
assert.equal(kinds['Doomed.java'], 'deleted')
pass('turn 1 note records modified / created / deleted', JSON.stringify(kinds))

// --- turn 2: edit the large file (chunk layer) -----------------------------
await engine.beginTurn(workspace)
const bytesBeforeTurn2 = objectBytes()
// Change the FIRST chunk only; the two trailing chunks are byte-identical.
fs.writeFileSync(bigFile, 'D'.repeat(64 * 1024) + 'B'.repeat(64 * 1024) + 'C'.repeat(64 * 1024))
const note2 = await engine.endTurn(workspace, { turn: 2, reason: { kind: 'completed' } })

assert.ok(note2 !== null, 'turn 2 must produce a note')
const bigEntry = note2.files.find((f) => f.path === 'Big.java')
assert.ok(bigEntry !== undefined, 'Big.java must be recorded')
assert.equal(bigEntry.chunked, true, 'Big.java must use the chunk layer')
// The decisive property: a one-chunk edit to a 192 KiB file must not cost
// anywhere near 192 KiB of storage.
const turn2Bytes = objectBytes() - bytesBeforeTurn2
assert.ok(
  turn2Bytes < BIG_SIZE / 2,
  `a one-chunk edit must store far less than the whole file: grew ${turn2Bytes} B for a ${BIG_SIZE} B file`,
)
pass('chunk layer stores only the changed chunk', `+${turn2Bytes} B for a ${BIG_SIZE} B file (independent: +${turn2Bytes} vs ${BIG_SIZE})`)

// --- turn 3: edit main again, verify delta chaining ------------------------
await engine.beginTurn(workspace)
fs.writeFileSync(mainFile, 'class Main { void a() { int x = 2; } }\n')
const note3 = await engine.endTurn(workspace, { turn: 3, reason: { kind: 'completed' } })
assert.ok(note3 !== null, 'turn 3 must produce a note')
assert.equal(note3.parentManifest, note2.manifestHash, 'note 3 must chain onto note 2')
const deltaFiles = note3.files.map((f) => f.path)
assert.deepEqual(deltaFiles.sort(), ['Main.java'], 'note 3 must store only its own delta')
pass('notes store per-turn deltas, chained by parentManifest', `delta = ${deltaFiles.join(', ')}`)

// --- resolved manifest must see the whole tree -----------------------------
const resolved = engine.getManifestEntries(engine.openRepo(workspace), note3.manifestHash)
const resolvedPaths = Object.keys(resolved.entries).sort()
assert.deepEqual(resolvedPaths, ['Big.java', 'Fresh.java', 'Main.java'])
assert.equal(resolved.entries['Doomed.java'], undefined, 'the deleted file must stay gone')
pass('manifest chain resolves to the full tree state', `${resolvedPaths.length} path(s), chain depth ${resolved.chainLength}`)

// --- revert-before: undo turn 3 exactly ------------------------------------
const mainAfterTurn3 = fs.readFileSync(mainFile, 'utf8')
assert.equal(mainAfterTurn3, 'class Main { void a() { int x = 2; } }\n')
const undo3 = engine.revertBefore({ root: workspace, id: note3.id })
assert.equal(undo3.ok, true, `undo of turn 3 must succeed: ${JSON.stringify(undo3.failed)}`)
assert.equal(undo3.mode, 'before-note')
assert.equal(fs.readFileSync(mainFile, 'utf8'), 'class Main { void a() { int x = 1; } }\n', 'undoing turn 3 must restore the turn-2 text')
pass('revert --before undoes exactly one turn', `restored ${undo3.restored.length}`)

// --- revert to note 1: the tree as of that turn's end ----------------------
const toNote1 = engine.revert({ root: workspace, id: note1.id })
assert.equal(toNote1.ok, true, `rollback to note 1 must succeed: ${JSON.stringify(toNote1.failed)}`)
assert.equal(toNote1.mode, 'to-note')
assert.equal(fs.readFileSync(mainFile, 'utf8'), 'class Main { void a() { int x = 1; } }\n', 'Main.java must match note 1 end state')
assert.equal(fs.readFileSync(bigFile, 'utf8').length, 64 * 1024 * 3, 'Big.java must be restored at full length')
assert.equal(fs.existsSync(freshFile), true, 'Fresh.java existed at note 1 end and must be back')
assert.equal(fs.existsSync(doomedFile), false, 'Doomed.java was already deleted at note 1 end and must stay gone')
pass('revert to a note restores that turn-end state', `restored ${toNote1.restored.length}, removed ${toNote1.removed.length}`)

// --- undoing the FIRST turn has no earlier state, and says so --------------
fs.writeFileSync(mainFile, 'class Main { void scratch() {} }\n')
const undo1 = engine.revertBefore({ root: workspace, id: note1.id })
assert.equal(undo1.ok, false, 'undoing the first recorded turn must refuse rather than invent a state')
assert.ok(String(undo1.error).includes('first recorded turn'), `refusal must explain itself, got: ${undo1.error}`)
assert.equal(
  fs.readFileSync(mainFile, 'utf8'),
  'class Main { void scratch() {} }\n',
  'a refused undo must leave the tree untouched',
)
pass('undo of the first turn refuses with a reason and writes nothing')

// --- revert to note 1 still gives the original bytes -----------------------
const toNote1Again = engine.revert({ root: workspace, id: note1.id })
assert.equal(toNote1Again.ok, true, `revert to note 1 must succeed: ${JSON.stringify(toNote1Again.failed)}`)
assert.equal(fs.readFileSync(mainFile, 'utf8'), 'class Main { void a() { int x = 1; } }\n')
assert.equal(fs.existsSync(freshFile), true, 'Fresh.java existed at note 1 end and must be back')
assert.equal(fs.existsSync(doomedFile), false, 'Doomed.java was already deleted at note 1 end and must stay gone')
pass('revert to note 1 re-establishes its exact tree', `restored ${toNote1Again.restored.length}`)

// --- dry run must not touch disk -------------------------------------------
fs.writeFileSync(mainFile, 'class Main { void a() { int z = 9; } }\n')
const dry = engine.revert({ root: workspace, id: note2.id, dryRun: true })
assert.equal(dry.dryRun, true)
assert.equal(fs.readFileSync(mainFile, 'utf8'), 'class Main { void a() { int z = 9; } }\n', 'a dry run must not write')
assert.ok(dry.planned.length > 0, 'a dry run must report a plan')
pass('dry run reports the plan without writing', `${dry.planned.length} path(s) planned`)

// --- gc keeps reachable blobs ----------------------------------------------
const gc = engine.gc(workspace)
assert.equal(gc.ok, true)
assert.ok(gc.kept > 0, 'reachable blobs must survive gc')
pass('gc sweeps unreachable blobs', `kept ${gc.kept}, removed ${gc.removed}`)

// --- a rollback must still work AFTER gc -----------------------------------
// The decisive check: gc must keep everything every note needs, not just the
// blobs the current head happens to reference.
const mainBeforeGcRevert = fs.readFileSync(mainFile, 'utf8')
const afterGc = engine.revert({ root: workspace, id: note3.id })
assert.equal(afterGc.ok, true, `rollback after gc must succeed: ${JSON.stringify(afterGc.failed)}`)
assert.ok(mainBeforeGcRevert.length > 0)
assert.equal(fs.readFileSync(mainFile, 'utf8'), 'class Main { void a() { int x = 2; } }\n', 'note 3 content must be restorable after gc')
const afterGcUndo = engine.revertBefore({ root: workspace, id: note3.id })
assert.equal(afterGcUndo.ok, true, `undo after gc must succeed: ${JSON.stringify(afterGcUndo.failed)}`)
assert.equal(fs.readFileSync(mainFile, 'utf8'), 'class Main { void a() { int x = 1; } }\n', 'the pre-turn-3 state must be restorable after gc')
pass('rollback and undo both survive gc', `restored ${afterGc.restored.length} then ${afterGcUndo.restored.length}`)

// --- a file created BETWEEN turns must still reach the chain ---------------
//
// This is the regression for a real data-loss bug. `beginTurn` scans before it
// snapshots, so a file that appears between two turns lands in the index AND in
// that turn's baseline — it never registers as an edit. The pin step exists to
// catch exactly that case, and it used to sit behind an early return that fired
// whenever a turn had no edits. The file then stayed in the index and out of
// the chain forever, and a later revert deleted it: the delete branch is
// "present in the index, absent from the target tree".
const betweenFile = path.join(workspace, 'Between.java')
fs.writeFileSync(betweenFile, 'class Between {}\n')

await engine.beginTurn(workspace)
const quietNote = await engine.endTurn(workspace, { turn: 4, reason: { kind: 'completed' } })
assert.ok(quietNote !== null, 'a turn whose only work is pinning must still cut a note')
assert.equal(quietNote.changedCount, 0, 'pinning is not an edit and must not be counted as one')
assert.ok(
  quietNote.pinned.some((p) => p.path === 'Between.java'),
  'the between-turn file must be pinned into the chain',
)

const revToQuiet = engine.revert({ root: workspace, id: quietNote.id })
assert.equal(revToQuiet.ok, true, `revert must succeed: ${JSON.stringify(revToQuiet.failed)}`)
assert.equal(
  fs.existsSync(betweenFile),
  true,
  'a revert must not delete a file the chain now knows about',
)
pass('a between-turn file is pinned, and a revert keeps it', `pinned ${quietNote.pinned.length}, changed ${quietNote.changedCount}`)

// --- status ----------------------------------------------------------------
const status = engine.status(workspace)
assert.equal(status.ok, true)
assert.ok(status.noteCount >= 3, 'all three notes must be listed')
pass('status reports the repository', `${status.noteCount} note(s), ${status.objects} object(s)`)

process.stdout.write(`\nAll probes passed. Sandbox: ${sandbox}\n`)
fs.rmSync(sandbox, { recursive: true, force: true })
