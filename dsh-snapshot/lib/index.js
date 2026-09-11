/**
 * dsh-snapshot — host-side plugin + engine + CLI.
 *
 * A git-independent local snapshot repository giving every DSH turn a
 * rollback point. Three layers of incrementality keep it small:
 *
 *   1. Blob layer  — content-addressed storage. Identical bytes are stored
 *                    once, across files and across turns.
 *   2. Chunk layer — files at or above `chunkThresholdBytes` are split into
 *                    fixed chunks (default 64 KiB) and stored per chunk, so a
 *                    one-line edit to a large file rewrites exactly one chunk.
 *   3. Note layer  — a note stores only the paths whose content differs from
 *                    its parent note (plus the parent's manifest hash), so N
 *                    turns over the same tree keep N small deltas.
 *
 * Rollback walks a note's manifest (following `parentManifest` for paths the
 * note did not touch) and writes every referenced blob back to disk.
 *
 * Layout (per workspace):
 *   <SNAP_ROOT>/<workspaceId>/
 *     meta.json                 sparse-checkout style path / chunk-size config
 *     objects/<aa>/<sha>        chunk and whole-file blobs, deduplicated
 *     manifests/<sha>.json      path -> { hash, size, chunked }, delta-chained
 *     notes/<noteId>.json       one note per finished turn that changed bytes
 *     state.json                content index + baseline, powers `status`
 *
 * CLI (run as `node lib/index.js <cmd> [args]`):
 *   status [--root DIR]
 *   list   [--root DIR] [--limit N]
 *   show   --id NOTE [--root DIR]
 *   revert --id NOTE [--root DIR] [--path P ...] [--dry-run]
 *   gc     [--root DIR]
 *
 * @module dsh-snapshot
 */

import fs from 'node:fs'
import fsp from 'node:fs/promises'
import os from 'node:os'
import path from 'node:path'
import crypto from 'node:crypto'
import { fileURLToPath } from 'node:url'

const LOG_TAG = '[dsh-snapshot]'
const ENGINE_VERSION = 2

/**
 * Where snapshot repositories live.
 *
 * The default sits on the harness volume rather than under `~/.dsh`: the
 * baseline of a large workspace runs to hundreds of megabytes and the system
 * drive is the one that runs out first. `DSH_SNAPSHOT_ROOT` still wins.
 */
function resolveSnapRoot() {
  if (process.env.DSH_SNAPSHOT_ROOT) return process.env.DSH_SNAPSHOT_ROOT
  return path.join('D:', path.sep, 'users', '21656', 'Desktop', 'harness', '.dsh-snap')
}

/** Chunk size for large files. 64 KiB keeps per-chunk hashing cheap. */
const DEFAULT_CHUNK_SIZE = 64 * 1024

/** Directories never scanned: VCS metadata, dependency trees and build output. */
const DEFAULT_EXCLUDES = [
  '.git', '.gradle', '.dsh-snap', '.venv', '.venv-4', '.vscode', '.idea',
  'node_modules', 'build', 'out', 'dist', 'target', 'run',
  '__pycache__', '.cache', '.mypy_cache', '.pytest_cache', '.dsh',
]

/** Suffixes skipped outright: binaries and archives are not rollback material. */
const DEFAULT_SKIP_SUFFIXES = [
  '.jar', '.class', '.zip', '.png', '.jpg', '.jpeg', '.gif', '.webp', '.ico',
  '.pdf', '.mp4', '.mp3', '.wav', '.ogg', '.exe', '.dll', '.so', '.dylib',
  '.bin',
]

const DEFAULT_CONFIG = {
  version: ENGINE_VERSION,
  enabled: true,
  /** Files at or above this size go to the chunk layer. */
  chunkThresholdBytes: 128 * 1024,
  chunkSize: DEFAULT_CHUNK_SIZE,
  /** Single files above this are fingerprinted but never stored. */
  maxFileBytes: 16 * 1024 * 1024,
  keepNotes: 300,
  maxFilesPerScan: 20000,
  excludes: DEFAULT_EXCLUDES,
  skipSuffixes: DEFAULT_SKIP_SUFFIXES,
}

// ---------------------------------------------------------------------------
// helpers
// ---------------------------------------------------------------------------

const sha256 = (buf) => crypto.createHash('sha256').update(buf).digest('hex')
const nowIso = () => new Date().toISOString()
const stamp = () => nowIso().replace(/[:.]/g, '-')

function ensureDirSync(dir) {
  try {
    fs.mkdirSync(dir, { recursive: true })
  } catch (err) {
    /* a racing creator is harmless */
  }
}

function readJsonSync(file, fallback) {
  try {
    const parsed = JSON.parse(fs.readFileSync(file, 'utf8'))
    return parsed && typeof parsed === 'object' ? parsed : fallback
  } catch (err) {
    return fallback
  }
}

/** Write JSON atomically (tmp + rename) so a crash never truncates state. */
function writeJsonAtomic(file, value) {
  const tmp = `${file}.tmp-${process.pid}-${Date.now()}`
  try {
    fs.writeFileSync(tmp, JSON.stringify(value), 'utf8')
    fs.renameSync(tmp, file)
    return true
  } catch (err) {
    try {
      fs.unlinkSync(tmp)
    } catch (ignored) {}
    return false
  }
}

/** Stable, filesystem-safe repository id: readable slug + short content hash. */
function workspaceIdOf(root) {
  const norm = path.resolve(root)
  const slug = (path.basename(norm).toLowerCase().replace(/[^a-z0-9._-]+/g, '-').replace(/^-+|-+$/g, '') || 'workspace').slice(0, 48)
  return `${slug}-${sha256(Buffer.from(norm)).slice(0, 10)}`
}

function isInside(root, abs) {
  const rel = path.relative(root, abs)
  return rel.length > 0 && !rel.startsWith('..') && !path.isAbsolute(rel)
}

// ---------------------------------------------------------------------------
// engine
// ---------------------------------------------------------------------------

class SnapshotEngine {
  /**
   * @param options - engine options.
   * @param options.root - default workspace root for CLI calls.
   * @param options.snapRoot - repository container directory.
   * @param options.config - partial configuration overrides.
   * @param options.log - sink for human-readable progress lines.
   */
  constructor(options = {}) {
    this.root = options.root === undefined ? process.cwd() : path.resolve(options.root)
    this.snapRoot = options.snapRoot ?? resolveSnapRoot()
    this.config = { ...DEFAULT_CONFIG, ...(options.config ?? {}) }
    this.config.excludes = new Set(this.config.excludes)
    this.config.skipSuffixes = new Set(this.config.skipSuffixes)
    this.log = typeof options.log === 'function' ? options.log : () => {}
    /** workspaceRoot -> repo record */
    this.repos = new Map()
    /** state.json + baseline cache are per repo; loaded lazily */
  }

  // ---- repository -----------------------------------------------------------

  repoFor(root) {
    const norm = path.resolve(root ?? this.root)
    let repo = this.repos.get(norm)
    if (repo === undefined) {
      const id = workspaceIdOf(norm)
      const dir = path.join(this.snapRoot, id)
      repo = {
        id,
        root: norm,
        dir,
        objectsDir: path.join(dir, 'objects'),
        manifestsDir: path.join(dir, 'manifests'),
        notesDir: path.join(dir, 'notes'),
        stateFile: path.join(dir, 'state.json'),
        metaFile: path.join(dir, 'meta.json'),
        state: null,
      }
      this.repos.set(norm, repo)
    }
    return repo
  }

  /** Open a repository, creating directories and reading its state file. */
  openRepo(root) {
    const repo = this.repoFor(root)
    ensureDirSync(repo.objectsDir)
    ensureDirSync(repo.manifestsDir)
    ensureDirSync(repo.notesDir)
    if (repo.state === null) {
      const raw = readJsonSync(repo.stateFile, null)
      repo.state =
        raw !== null && raw.version === ENGINE_VERSION && raw.files && typeof raw.files === 'object'
          ? raw
          : { version: ENGINE_VERSION, root: repo.root, files: {}, head: null, baseline: null, createdAt: nowIso() }
      if (raw === null) writeJsonAtomic(repo.metaFile, { version: ENGINE_VERSION, root: repo.root, chunkSize: this.config.chunkSize, chunkThresholdBytes: this.config.chunkThresholdBytes, createdAt: nowIso() })
    }
    return repo
  }

  saveState(repo) {
    writeJsonAtomic(repo.stateFile, repo.state)
  }

  // ---- blob layer -----------------------------------------------------------

  objectPath(repo, hash) {
    return path.join(repo.objectsDir, hash.slice(0, 2), hash)
  }

  hasObject(repo, hash) {
    return fs.existsSync(this.objectPath(repo, hash))
  }

  putBuffer(repo, buf) {
    const hash = sha256(buf)
    if (!this.hasObject(repo, hash)) {
      const file = this.objectPath(repo, hash)
      ensureDirSync(path.dirname(file))
      try {
        fs.writeFileSync(file, buf)
      } catch (err) {
        return null
      }
    }
    return hash
  }

  getBuffer(repo, hash) {
    try {
      return fs.readFileSync(this.objectPath(repo, hash))
    } catch (err) {
      return null
    }
  }

  // ---- chunk layer ----------------------------------------------------------

  /** Split a buffer into fixed-size chunks. */
  *chunksOf(buf, chunkSize) {
    for (let offset = 0; offset < buf.length; offset += chunkSize) {
      yield buf.subarray(offset, Math.min(offset + chunkSize, buf.length))
    }
  }

  /**
   * Store a file body, choosing the whole-blob or chunk layer. Returns a
   * descriptor `{ hash, size, chunked, chunks? }`.
   *
   * The whole-file hash is checked first and returned as-is when present. This
   * matters more than it looks: `scan` stores every file it reads as one whole
   * blob, while a large file that gets edited later switches to the chunk
   * layer. Without the early return, that switch would re-store the entire file
   * — a 192 KiB body became ~384 KiB of storage in testing before this check.
   *
   * @param options - storage options.
   * @param options.hintHash - known whole-file hash, avoids re-hashing a large buffer.
   */
  storeBody(repo, buf, options = {}) {
    const wholeHash = options.hintHash ?? sha256(buf)
    if (this.hasObject(repo, wholeHash)) {
      return { hash: wholeHash, size: buf.length, chunked: false }
    }
    if (buf.length < this.config.chunkThresholdBytes) {
      const hash = this.putBuffer(repo, buf)
      return hash === null ? null : { hash, size: buf.length, chunked: false }
    }
    const chunks = []
    for (const chunk of this.chunksOf(buf, this.config.chunkSize)) {
      const hash = this.putBuffer(repo, chunk)
      if (hash === null) return null
      chunks.push(hash)
    }
    return { hash: sha256(Buffer.from(chunks.join(':'))), size: buf.length, chunked: true, chunks }
  }

  /** Rebuild a body from its descriptor. */
  loadBody(repo, descriptor) {
    if (descriptor === null || descriptor === undefined) return null
    if (!descriptor.chunked) return this.getBuffer(repo, descriptor.hash)
    const parts = []
    for (const chunkHash of descriptor.chunks ?? []) {
      const part = this.getBuffer(repo, chunkHash)
      if (part === null) return null
      parts.push(part)
    }
    return Buffer.concat(parts)
  }

  /** Count stored objects, for status and gc. */
  countObjects(repo) {
    let total = 0
    let bytes = 0
    let shards
    try {
      shards = fs.readdirSync(repo.objectsDir)
    } catch (err) {
      return { total, bytes }
    }
    for (const shard of shards) {
      const dir = path.join(repo.objectsDir, shard)
      let names
      try {
        names = fs.readdirSync(dir)
      } catch (err) {
        continue
      }
      for (const objectName of names) {
        total += 1
        try {
          bytes += fs.statSync(path.join(dir, objectName)).size
        } catch (err) {}
      }
    }
    return { total, bytes }
  }

  // ---- manifests ------------------------------------------------------------

  /**
   * Persist a tree manifest both as a content-addressed object (so notes can
   * reference it by hash) and as a file under `manifests/` (so garbage
   * collection has a directory to walk).
   *
   * Storing it as an object alone is not enough: gc sweeps `manifests/` to learn
   * which blobs are live, and an empty directory makes it treat every chunk a
   * note still needs as garbage.
   */
  saveManifest(repo, manifest) {
    const body = Buffer.from(JSON.stringify(manifest), 'utf8')
    const hash = this.putBuffer(repo, body)
    ensureDirSync(repo.manifestsDir)
    try {
      fs.writeFileSync(path.join(repo.manifestsDir, `${hash}.json`), body)
    } catch (err) {
      return null
    }
    return hash
  }

  readManifest(repo, hash) {
    if (typeof hash !== 'string' || hash.length === 0) return null
    // Prefer the plain directory file: gc keeps it, and it survives even if the
    // object copy was swept.
    const fromDir = readJsonSync(path.join(repo.manifestsDir, `${hash}.json`), null)
    if (fromDir !== null && fromDir.entries) return fromDir
    const raw = this.getBuffer(repo, hash)
    if (raw === null) return null
    try {
      const parsed = JSON.parse(raw.toString('utf8'))
      return parsed && typeof parsed === 'object' && parsed.entries ? parsed : null
    } catch (err) {
      return null
    }
  }

  /**
   * Fully resolve a manifest hash into a concrete `path -> descriptor | null`
   * tree, following the delta chain all the way to the root.
   *
   * Deltas are applied from the OLDEST manifest forward, so a later manifest
   * overrides an earlier one and an explicit `null` (deletion) wins over an
   * earlier descriptor. A note's manifest therefore resolves to the complete
   * tree as of that turn's end, even though it stores only that turn's changes.
   *
   * @returns the resolved tree plus chain metadata for reporting.
   */
  getManifestEntries(repo, hash) {
    const chain = []
    const seen = new Set()
    let cursor = hash
    let broken = false
    while (cursor !== null && cursor !== undefined && !seen.has(cursor)) {
      seen.add(cursor)
      const manifest = this.readManifest(repo, cursor)
      if (manifest === null) {
        broken = true
        break
      }
      chain.push(manifest)
      cursor = manifest.parentManifest ?? null
    }
    const entries = {}
    for (let i = chain.length - 1; i >= 0; i -= 1) {
      for (const [rel, descriptor] of Object.entries(chain[i].entries ?? {})) {
        if (descriptor === null) delete entries[rel]
        else entries[rel] = descriptor
      }
    }
    return { entries, chainLength: chain.length, broken }
  }

  /**
   * Store one file's content and return the descriptor the manifest should
   * carry. The layer choice lives here and nowhere else, so `scan` and
   * `endTurn` can never disagree about how the same bytes are represented.
   *
   * `hintHash` short-circuits the whole-file hash for small files that were
   * just hashed by the scanner.
   */
  detectDescriptor(repo, rel, hintHash) {
    const abs = path.join(repo.root, rel)
    let buf
    try {
      buf = fs.readFileSync(abs)
    } catch (err) {
      return null
    }
    if (buf.length >= this.config.chunkThresholdBytes) {
      const chunks = []
      for (const chunk of this.chunksOf(buf, this.config.chunkSize)) {
        const hash = this.putBuffer(repo, chunk)
        if (hash === null) return null
        chunks.push(hash)
      }
      // Chunked bodies have no whole-file blob, so identity is derived from the
      // chunk list. Every already-stored chunk is reused, which is exactly the
      // incrementality this layer exists for.
      return { hash: sha256(Buffer.from(chunks.join(':'))), size: buf.length, chunked: true, chunks }
    }
    const hash = hintHash ?? sha256(buf)
    if (this.putBuffer(repo, buf) === null) return null
    return { hash, size: buf.length, chunked: false }
  }

  // ---- scan -----------------------------------------------------------------

  /** Recursively list candidate files (relative paths), bounded by the cap. */
  async collectFiles(repo) {
    const out = []
    const queue = ['']
    while (queue.length > 0 && out.length < this.config.maxFilesPerScan) {
      const relDir = queue.shift()
      const absDir = relDir === '' ? repo.root : path.join(repo.root, relDir)
      let entries
      try {
        entries = await fsp.readdir(absDir, { withFileTypes: true })
      } catch (err) {
        continue
      }
      for (const entry of entries) {
        if (out.length >= this.config.maxFilesPerScan) break
        const rel = relDir === '' ? entry.name : `${relDir}${path.sep}${entry.name}`
        if (this.isExcluded(rel)) continue
        if (entry.isDirectory()) queue.push(rel)
        else if (entry.isFile()) out.push(rel)
      }
    }
    return out
  }

  isExcluded(relPath) {
    for (const segment of relPath.split(/[\\/]/)) {
      if (this.config.excludes.has(segment)) return true
    }
    return this.config.skipSuffixes.has(path.extname(relPath).toLowerCase())
  }

  /**
   * Refresh the content index. The cheap signals (size + mtime) decide whether
   * a content hash must be recomputed, so an unchanged tree costs one
   * readdir+stat walk and no reads at all.
   *
   * @returns paths whose content index moved during this scan.
   */
  async scan(repo) {
    const state = repo.state
    const files = await this.collectFiles(repo)
    const present = new Set()
    const changed = []
    for (const rel of files) {
      present.add(rel)
      const abs = path.join(repo.root, rel)
      let stat
      try {
        stat = await fsp.stat(abs)
      } catch (err) {
        continue
      }
      const previous = state.files[rel]
      if (previous !== undefined && previous.size === stat.size && previous.mtimeMs === stat.mtimeMs) {
        // Unchanged since the last scan: its content is already stored and the
        // descriptor is already known, so do not read the file at all.
        if (previous.descriptor !== undefined && previous.hash !== null) continue
      }
      if (stat.size > this.config.maxFileBytes) {
        // Fingerprint only; never store a body this large.
        state.files[rel] = { size: stat.size, mtimeMs: stat.mtimeMs, hash: null, tooLarge: true }
        changed.push({ path: rel, kind: 'too-large' })
        continue
      }
      let buf
      try {
        buf = await fsp.readFile(abs)
      } catch (err) {
        continue
      }
      const hash = sha256(buf)
      // Persist with the SAME layer policy `endTurn` will use, and remember the
      // descriptor. Storing a whole-file blob here and then chunking the same
      // bytes at turn end is what doubled the cost of editing a large file.
      const descriptor = this.detectDescriptor(repo, rel, hash)
      state.files[rel] = { size: stat.size, mtimeMs: stat.mtimeMs, hash, descriptor: descriptor ?? null }
      changed.push({ path: rel, kind: 'content' })
    }
    for (const rel of Object.keys(state.files)) {
      if (!present.has(rel)) {
        delete state.files[rel]
        changed.push({ path: rel, kind: 'missing' })
      }
    }
    return changed
  }

  // ---- turn lifecycle -------------------------------------------------------

  /** Baseline every file's content when a turn opens. */
  async beginTurn(root) {
    const repo = this.openRepo(root)
    // `scan` fingerprints every file and persists every body it reads, so the
    // turn's opening bytes are all resolvable from the object store. That is
    // what lets `revertBefore` undo the FIRST turn as faithfully as any other:
    // untracked-by-delta files still have a pre-image on disk.
    await this.scan(repo)
    repo.state.baseline = {}
    for (const [rel, entry] of Object.entries(repo.state.files)) repo.state.baseline[rel] = entry.hash
    repo.state.baselineAt = nowIso()
    repo.state.head = repo.state.head ?? null
    this.saveState(repo)
    return { repo, tracked: Object.keys(repo.state.files).length }
  }

  /**
   * Cut a note at turn end.
   *
   * @returns the note when bytes changed, else null.
   */
  async endTurn(root, meta = {}) {
    const repo = this.openRepo(root)
    const scanChanged = await this.scan(repo)
    const baseline = repo.state.baseline ?? {}

    // `scan` reports exactly which paths moved, so the diff needs no re-reading
    // and no re-hashing: reuse the descriptors it already stored.
    const touched = new Set()
    for (const rel of meta.candidates ?? []) touched.add(rel)
    for (const entry of scanChanged) touched.add(entry.path)
    for (const rel of Object.keys(repo.state.files)) {
      const before = baseline[rel]
      const after = repo.state.files[rel]?.hash ?? null
      if (before !== after) touched.add(rel)
    }
    for (const rel of Object.keys(baseline)) {
      if (repo.state.files[rel] === undefined) touched.add(rel)
    }

    const entries = {}
    const createdPaths = []
    const fileNotes = []
    const pinnedNotes = []
    for (const rel of touched) {
      if (this.isExcluded(rel)) continue
      const after = repo.state.files[rel]
      if (after === undefined) {
        // Deleted this turn: the manifest records the removal explicitly.
        entries[rel] = null
        fileNotes.push({ path: rel, kind: 'deleted' })
        continue
      }
      if (after.tooLarge === true || after.hash === null) {
        fileNotes.push({ path: rel, kind: 'skipped', reason: 'too-large' })
        continue
      }
      // `scan` already stored this content under the shared layer policy and
      // hung the descriptor on the index entry — no re-read, no re-store.
      const stored = after.descriptor
      if (stored === null || stored === undefined || typeof stored.hash !== 'string') {
        fileNotes.push({ path: rel, kind: 'skipped', reason: 'store-failed' })
        continue
      }
      const descriptor = { hash: stored.hash, size: stored.size, chunked: stored.chunked === true }
      if (stored.chunked === true) descriptor.chunks = stored.chunks
      entries[rel] = descriptor
      const existedBefore = baseline[rel] !== undefined && baseline[rel] !== null
      if (!existedBefore) createdPaths.push(rel)
      fileNotes.push({ path: rel, kind: existedBefore ? 'modified' : 'created', size: descriptor.size, chunked: descriptor.chunked })
    }

    // Pin every indexed path the chain cannot already account for.
    //
    // A file that exists but was never MODIFIED appears in no turn's delta, so
    // without this the chain holds no evidence it ever existed. Rolling back to
    // a note from before such a file was deleted would then restore everything
    // except that file — the tree would silently lose it. This is the step that
    // keeps `resolve`-style replay a faithful picture of the whole tree:
    // anything the chain has not mentioned yet gets its current content
    // recorded here, once. Afterwards the chain covers it, so later turns stay
    // small deltas.
    const knownInChain = (() => {
      const paths = new Set()
      const seen = new Set()
      let cursor = repo.state.head ?? null
      while (cursor !== null && cursor !== undefined && !seen.has(cursor)) {
        seen.add(cursor)
        const m = this.readManifest(repo, cursor)
        if (m === null) break
        for (const rel of Object.keys(m.entries ?? {})) paths.add(rel)
        cursor = m.parentManifest ?? null
      }
      return paths
    })()
    for (const [rel, indexEntry] of Object.entries(repo.state.files)) {
      if (entries[rel] !== undefined) continue
      if (knownInChain.has(rel)) continue
      if (this.isExcluded(rel)) continue
      if (indexEntry === null || indexEntry === undefined) continue
      if (indexEntry.tooLarge === true || indexEntry.hash === null) continue
      const stored = indexEntry.descriptor
      if (stored === null || stored === undefined || typeof stored.hash !== 'string') continue
      const descriptor = { hash: stored.hash, size: stored.size, chunked: stored.chunked === true }
      if (stored.chunked === true) descriptor.chunks = stored.chunks
      entries[rel] = descriptor
      // Pinned, not changed: it belongs in the tree picture but is not an edit,
      // so it must not by itself make this turn note-worthy.
      pinnedNotes.push({ path: rel, kind: 'pinned', size: descriptor.size })
    }

    // No edit and nothing to pin: this turn genuinely has nothing to record.
    //
    // This test MUST stay after the pin step, and it is not a style choice.
    // While it sat before, a turn with no edits returned early and skipped
    // pinning altogether. A file that appeared between two turns is absorbed
    // into the next turn's baseline by `beginTurn` (which scans before it
    // snapshots), so it never registers as an edit — and with the pin step
    // unreachable, it stayed in the index and out of the chain forever. A later
    // `revert` then read it as "on disk but absent from the target tree" and
    // deleted it. Pinned entries are excluded from `changedCount`, so a
    // pin-only note still reports zero edits.
    if (fileNotes.length === 0 && pinnedNotes.length === 0) {
      repo.state.baseline = null
      this.saveState(repo)
      return null
    }

    // The manifest is a DELTA on top of the previous note's manifest, which is
    // what keeps N turns over a large tree cheap: one JSON object per turn
    // instead of a full tree listing.
    //
    // Two details make a delta chain safe here:
    //   * a deleted path is recorded as an explicit `null` (not merely
    //     omitted) so replaying the chain removes it, and
    //   * paths created this turn are listed in `createdPaths`, so undoing this
    //     turn knows which files to delete — they are absent from the parent
    //     manifest, and absence alone never means "delete".
    const manifest = {
      version: ENGINE_VERSION,
      at: nowIso(),
      source: 'delta',
      parentManifest: repo.state.head ?? null,
      createdPaths,
      entries,
    }
    const manifestHash = this.saveManifest(repo, manifest)
    repo.state.head = manifestHash
    repo.state.baseline = null
    this.saveState(repo)
    this.prune(repo)

    const note = {
      version: ENGINE_VERSION,
      id: `${stamp()}-${String(meta.turn ?? 0).padStart(3, '0')}`,
      at: nowIso(),
      sessionId: meta.sessionId ?? 'default',
      workspaceRoot: repo.root,
      repoId: repo.id,
      turn: meta.turn ?? null,
      reason: meta.reason ?? null,
      completed: Boolean(meta.completed),
      summary: typeof meta.summary === 'string' ? meta.summary.slice(0, 4000) : '',
      work: Array.isArray(meta.work) ? meta.work.slice(-40) : [],
      parentManifest: manifest.parentManifest,
      manifestHash,
      files: fileNotes,
      // Pinned paths live in the manifest but are not edits; kept separate so
      // `files` keeps describing what this turn actually did.
      pinned: pinnedNotes,
      changedCount: fileNotes.filter((f) => f.kind !== 'skipped').length,
    }
    writeJsonAtomic(path.join(repo.notesDir, `${note.id}.json`), note)
    this.log(`${LOG_TAG} note ${note.id}: ${note.changedCount} file(s) changed (manifest ${manifestHash.slice(0, 10)})`)
    return note
  }

  /** Keep note history bounded; blobs are reclaimed by `gc`. */
  prune(repo) {
    try {
      const notes = fs.readdirSync(repo.notesDir).filter((f) => f.endsWith('.json')).sort()
      for (const stale of notes.slice(0, Math.max(0, notes.length - this.config.keepNotes))) {
        try {
          fs.unlinkSync(path.join(repo.notesDir, stale))
        } catch (err) {}
      }
    } catch (err) {}
  }

  // ---- read side ------------------------------------------------------------

  listNotes(root, limit = 30) {
    const repo = this.openRepo(root)
    let names
    try {
      names = fs.readdirSync(repo.notesDir).filter((f) => f.endsWith('.json')).sort().reverse()
    } catch (err) {
      return []
    }
    return names.slice(0, limit).map((n) => readJsonSync(path.join(repo.notesDir, n), null)).filter(Boolean)
  }

  readNote(root, noteId) {
    const repo = this.openRepo(root)
    return readJsonSync(path.join(repo.notesDir, `${noteId}.json`), null)
  }

  /** Files a note can roll back, with the position of each path in the chain. */
  noteDelta(root, noteId) {
    const note = this.readNote(root, noteId)
    if (note === null) return null
    const repo = this.openRepo(root)
    const manifest = this.readManifest(repo, note.manifestHash)
    if (manifest === null) return null
    return { note, entries: manifest.entries }
  }

  status(root) {
    const repo = this.openRepo(root)
    const notes = this.listNotes(root, 1)
    const objects = this.countObjects(repo)
    return {
      ok: true,
      repoId: repo.id,
      root: repo.root,
      dir: repo.dir,
      snapRoot: this.snapRoot,
      filesIndexed: Object.keys(repo.state.files).length,
      head: repo.state.head,
      objects: objects.total,
      objectBytes: objects.bytes,
      noteCount: (() => {
        try {
          return fs.readdirSync(repo.notesDir).filter((f) => f.endsWith('.json')).length
        } catch (err) {
          return 0
        }
      })(),
      lastNote: notes[0] ?? null,
    }
  }

  // ---- rollback -------------------------------------------------------------

  /**
   * Materialize a target manifest onto disk: every referenced blob is written
   * back, and every path the target does not reference but the disk still has
   * (under the previous manifest) is removed.
   *
   * @param repo - open repository.
   * @param targetEntries - resolved `path -> descriptor | null` map.
   * @param options - apply options.
   * @param options.paths - restrict to these relative paths.
   * @param options.dryRun - report the plan without writing.
   * @param options.previousEntries - manifest the disk is expected to match,
   *   used to decide which extra files must be deleted; when omitted (or
   *   `null`) only explicit `null` entries cause a deletion.
   */
  applyManifest(repo, targetEntries, options = {}) {
    const { paths, dryRun = false, previousEntries } = options
    const filter = Array.isArray(paths) && paths.length > 0 ? new Set(paths.map((p) => p.replace(/\//g, path.sep))) : null
    const restored = []
    const alreadyCorrect = []
    const removed = []
    const failed = []
    const planned = []
    const touched = new Set()

    for (const [rel, descriptor] of Object.entries(targetEntries)) {
      if (filter !== null && !filter.has(rel)) continue
      if (this.isExcluded(rel)) continue
      touched.add(rel)
      const abs = path.join(repo.root, rel)
      if (descriptor === null) {
        planned.push({ path: rel, action: 'remove' })
        if (dryRun) continue
        try {
          fs.unlinkSync(abs)
          removed.push(rel)
        } catch (err) {
          if (err && err.code !== 'ENOENT') failed.push({ path: rel, error: String(err.message) })
        }
        continue
      }
      planned.push({ path: rel, action: 'restore', size: descriptor.size })
      if (dryRun) continue

      // Skip files that already hold the target bytes.
      //
      // Restoring a note rewrites its whole tree, but most of that tree is
      // usually identical to what is on disk — a ten-turn history with one
      // edited file still lists every file. Rewriting them all is wasted I/O and
      // makes a rollback look slow (measured: 2000 rewrites took ~6.9 s versus
      // ~0.6 s when only the genuinely changed paths are written).
      //
      // The comparison reads the FILE, never the index. The index can be stale
      // whenever something other than this engine wrote to the tree (the agent's
      // own edit tools, a person, another process), and trusting it makes the
      // engine skip a restore that was genuinely needed — which silently leaves
      // the workspace in the wrong state.
      let needsWrite = true
      try {
        const stat = fs.statSync(abs)
        if (stat.size === descriptor.size) {
          needsWrite = sha256(fs.readFileSync(abs)) !== descriptor.hash
        }
      } catch (err) {
        needsWrite = true // absent or unreadable: restore it
      }
      if (!needsWrite) {
        alreadyCorrect.push(rel)
        continue
      }

      const buf = this.loadBody(repo, descriptor)
      if (buf === null) {
        failed.push({ path: rel, error: 'blob missing' })
        continue
      }
      try {
        ensureDirSync(path.dirname(abs))
        fs.writeFileSync(abs, buf)
        restored.push(rel)
      } catch (err) {
        failed.push({ path: rel, error: String((err && err.message) || err) })
      }
    }

    // Extract every path the target tree does not contain but the disk still
    // holds, and delete those.
    //
    // Rolling back to an EARLIER note has to undo the turns in between, and a
    // turn that created a file leaves nothing in the delta chain to say "this
    // path must now go away" — the target manifest simply has no entry for it.
    // So deletion is driven by the live index (what the disk is believed to
    // hold right now) minus the target tree.
    //
    // Skipped for a partial rollback (`paths` given): the caller listed exactly
    // what to touch, and deleting everything else would be destructive.
    if (filter === null && previousEntries !== null && previousEntries !== undefined) {
      for (const rel of Object.keys(previousEntries)) {
        if (this.isExcluded(rel)) continue
        if (touched.has(rel)) continue
        if (Object.prototype.hasOwnProperty.call(targetEntries, rel)) continue
        planned.push({ path: rel, action: 'remove' })
        if (dryRun) continue
        try {
          fs.unlinkSync(path.join(repo.root, rel))
          removed.push(rel)
        } catch (err) {
          if (err && err.code !== 'ENOENT') failed.push({ path: rel, error: String(err.message) })
        }
      }

      // Directories the target tree does not contain must go too, or a rollback
      // leaves empty scaffolding behind — `pkg/deep/nested/` surviving a rollback
      // to before it was created is visibly wrong even though no FILE is wrong.
      // Deepest first, and only while empty: a directory still holding files the
      // target keeps must stay.
      if (!dryRun) {
        const dirs = new Set()
        for (const rel of removed) {
          let dir = path.dirname(rel)
          while (dir !== '.' && dir !== path.sep && dir.length > 0) {
            dirs.add(dir)
            dir = path.dirname(dir)
          }
        }
        const ordered = [...dirs].sort((a, b) => b.split(path.sep).length - a.split(path.sep).length)
        for (const rel of ordered) {
          try {
            fs.rmdirSync(path.join(repo.root, rel))
            removed.push(rel)
          } catch (err) {
            // ENOTEMPTY means the target still keeps something in there: fine.
          }
        }
      }
    }

    if (!dryRun) {
      // Re-fingerprint what moved so the next baseline starts clean.
      try {
        for (const rel of [...restored, ...removed]) {
          const abs = path.join(repo.root, rel)
          if (!fs.existsSync(abs)) {
            delete repo.state.files[rel]
            continue
          }
          const stat = fs.statSync(abs)
          repo.state.files[rel] = { size: stat.size, mtimeMs: stat.mtimeMs, hash: sha256(fs.readFileSync(abs)) }
        }
        repo.state.baseline = null
        this.saveState(repo)
      } catch (err) {}
    }

    return { restored, alreadyCorrect, removed, failed, planned }
  }

  /**
   * Roll forward/back to the state a note captured: the workspace as of that
   * turn's END. Use this for "put the tree back to how it was at that moment".
   *
   * @param options - rollback options.
   * @param options.root - workspace root.
   * @param options.id - note id.
   * @param options.paths - restrict the rollback to these relative paths.
   * @param options.dryRun - report the plan without writing.
   */
  revert({ root, id, paths, dryRun = false }) {
    const note = this.readNote(root, id)
    if (note === null) return { ok: false, error: `note not found: ${id}` }
    const repo = this.openRepo(root)
    const resolved = this.getManifestEntries(repo, note.manifestHash)
    if (resolved.broken) return { ok: false, error: `manifest chain broken for note ${id}` }

    // The live index describes what the disk holds now; `applyManifest` diffs it
    // against the target to decide what has to be deleted. Omitting it here (as
    // an earlier revision did) made a rollback restore files but never remove
    // the later turns' creations.
    const outcome = this.applyManifest(repo, resolved.entries, { paths, dryRun, previousEntries: repo.state.files })
    return {
      ok: outcome.failed.length === 0,
      mode: 'to-note',
      noteId: id,
      dryRun,
      chainLength: resolved.chainLength,
      ...outcome,
    }
  }

  /**
   * Undo the turn a note recorded: restore the workspace to the state it had
   * BEFORE that turn ran. Since every note's parent is the turn's complete
   * baseline checkpoint, this is a plain manifest replay — no inference, and
   * no risk of deleting a file the turn never touched.
   */
  revertBefore({ root, id, paths, dryRun = false }) {
    const note = this.readNote(root, id)
    if (note === null) return { ok: false, error: `note not found: ${id}` }
    const repo = this.openRepo(root)
    const parentHash = note.parentManifest
    if (parentHash === null || parentHash === undefined) {
      return {
        ok: false,
        error: `note ${id} is the first recorded turn, so there is no earlier state to restore`,
        hint: 'use revert (without --before) to restore this turn\'s own result instead',
      }
    }
    const resolved = this.getManifestEntries(repo, parentHash)
    if (resolved.broken) return { ok: false, error: `manifest chain broken for note ${id}` }
    // Restore the parent tree. Files this turn introduced are marked for removal
    // explicitly (their paths are recorded in `createdPaths`), and the live
    // index supplies the rest of the diff for anything else that should not
    // survive.
    const target = { ...resolved.entries }
    for (const rel of this.readManifest(repo, note.manifestHash)?.createdPaths ?? []) target[rel] = null
    const outcome = this.applyManifest(repo, target, { paths, dryRun, previousEntries: repo.state.files })
    return {
      ok: outcome.failed.length === 0,
      mode: 'before-note',
      noteId: id,
      dryRun,
      chainLength: resolved.chainLength,
      ...outcome,
    }
  }

  // ---- garbage collection ---------------------------------------------------

  /**
   * Mark-and-sweep: keep every blob reachable from any manifest, from the live
   * index, or from a note's resolved chain; drop the rest. With the chunk layer
   * this reclaims orphaned chunks after old notes are pruned.
   */
  gc(root) {
    const repo = this.openRepo(root)
    const reachable = new Set()
    const markDescriptor = (descriptor) => {
      if (descriptor === null || descriptor === undefined) return
      if (descriptor.chunked) {
        for (const chunkHash of descriptor.chunks ?? []) reachable.add(chunkHash)
      }
      if (typeof descriptor.hash === 'string') reachable.add(descriptor.hash)
    }
    let manifestCount = 0
    for (const manifestName of fs.readdirSync(repo.manifestsDir)) {
      const manifest = readJsonSync(path.join(repo.manifestsDir, manifestName), null)
      if (manifest === null) continue
      manifestCount += 1
      // The manifest's own object copy must survive too: content addressing
      // stores it in the object tree, and a note references it by hash.
      reachable.add(manifestName.replace(/\.json$/, ''))
      for (const descriptor of Object.values(manifest.entries ?? {})) markDescriptor(descriptor)
      if (typeof manifest.parentManifest === 'string') reachable.add(manifest.parentManifest)
    }
    for (const entry of Object.values(repo.state.files)) {
      if (entry === null || entry === undefined) continue
      if (typeof entry.hash === 'string') reachable.add(entry.hash)
      // A chunked file's `entry.hash` is a SYNTHETIC digest of its chunk list —
      // not an object in the store. Its real chunks live in the descriptor, and
      // failing to mark them here makes gc delete a live backup.
      markDescriptor(entry.descriptor)
    }
    let removed = 0
    let kept = 0
    for (const shard of fs.readdirSync(repo.objectsDir)) {
      const dir = path.join(repo.objectsDir, shard)
      for (const objectName of fs.readdirSync(dir)) {
        if (reachable.has(objectName)) {
          kept += 1
          continue
        }
        try {
          fs.unlinkSync(path.join(dir, objectName))
          removed += 1
        } catch (err) {}
      }
    }
    return { ok: true, repoId: repo.id, manifestCount, kept, removed }
  }
}

// ---------------------------------------------------------------------------
// cordis plugin
// ---------------------------------------------------------------------------

export const name = 'snapshot'
export const inject = []

export function apply(ctx) {
  const engine = new SnapshotEngine({ log: (line) => ctx.logger?.info?.(line) })
  const config = engine.config
  /** in-flight turn state keyed by session id */
  const turns = new Map()
  const disposers = []
  let observedCount = 0

  // Diagnostics ring.
  //
  // The host's service log may be captured elsewhere (or truncated), so a
  // "why did nothing get recorded" investigation cannot rely on it. This keeps
  // the last N lifecycle decisions in memory and serves them over HTTP, which
  // is what actually made the first deployment's silence diagnosable.
  const diagRing = []
  const DIAG_LIMIT = 80
  function diag(line) {
    diagRing.push(`${new Date().toISOString()} ${line}`)
    if (diagRing.length > DIAG_LIMIT) diagRing.splice(0, diagRing.length - DIAG_LIMIT)
  }

  function sessionKey(session) {
    return session && session.id ? String(session.id) : 'default'
  }

  function turnState(session) {
    const key = sessionKey(session)
    let state = turns.get(key)
    if (state === undefined) {
      state = { key, session, turn: 0, work: [], candidates: new Set(), summary: '', began: false }
      turns.set(key, state)
    }
    if (session !== undefined) state.session = session
    return state
  }

  function workspaceRootFor(session) {
    try {
      const policy = ctx.get('sandboxPolicy')
      if (policy !== undefined && typeof policy.resolve === 'function') {
        const resolved = policy.resolve({ session })
        if (resolved && typeof resolved.workspaceRoot === 'string' && resolved.workspaceRoot.length > 0) return resolved.workspaceRoot
      }
    } catch (err) {}
    return process.cwd()
  }

  function summarizeToolCall(callName, rawArgs) {
    let args = null
    try {
      args = JSON.parse(rawArgs)
    } catch (err) {
      args = null
    }
    const entry = { tool: callName }
    if (args !== null && typeof args === 'object') {
      for (const key of ['file_path', 'path', 'pattern', 'command', 'description', 'old_string']) {
        const value = args[key]
        if (value === undefined) continue
        entry[key] = typeof value === 'string' && value.length > 300 ? `${value.slice(0, 300)}…` : value
      }
    }
    return entry
  }

  function extractAssistantText(message) {
    if (message === null || typeof message !== 'object') return ''
    const content = Array.isArray(message.content) ? message.content : []
    return content
      .filter((block) => block && typeof block === 'object' && typeof block.text === 'string')
      .map((block) => block.text)
      .join('\n')
      .trim()
  }

  async function beginTurn(session, turn) {
    const state = turnState(session)
    state.turn = Number(turn) || 0
    state.work = []
    state.candidates = new Set()
    state.summary = ''
    state.began = false
    if (!config.enabled) return
    const root = workspaceRootFor(session)
    try {
      await engine.beginTurn(root)
      state.began = true
      diag(`turn/start #${state.turn} baseline ok root=${root}`)
    } catch (err) {
      diag(`turn/start #${state.turn} baseline FAILED: ${String((err && err.message) || err)}`)
      ctx.logger?.warn?.(`${LOG_TAG} baseline failed: ${String((err && err.message) || err)}`)
    }
  }

  async function endTurn(session, reason) {
    const key = sessionKey(session)
    const state = turns.get(key)
    if (state === undefined || !config.enabled) {
      diag(`turn/end ignored (no turn state; enabled=${config.enabled})`)
      turns.delete(key)
      return
    }
    try {
      const note = await engine.endTurn(workspaceRootFor(session), {
        sessionId: state.key,
        turn: state.turn,
        reason: reason ?? null,
        completed: Boolean(reason && reason.kind === 'completed'),
        summary: state.summary,
        work: state.work,
        candidates: [...state.candidates],
      })
      diag(
        note === null
          ? `turn/end #${state.turn} NO NOTE (no content change; candidates=${state.candidates.size}, work=${state.work.length})`
          : `turn/end #${state.turn} note ${note.id} (${note.changedCount} files)`,
      )
    } catch (err) {
      diag(`turn/end #${state.turn} FAILED: ${String((err && err.message) || err)}`)
      ctx.logger?.warn?.(`${LOG_TAG} note failed: ${String((err && err.message) || err)}`)
    }
    turns.delete(key)
  }

  disposers.push(
    ctx.on('session/event', (session, event) => {
      try {
        if (!event || typeof event.type !== 'string') return
        const data = event.data && typeof event.data === 'object' ? event.data : {}
        switch (event.type) {
          case 'turn/start':
            void beginTurn(session, data.turn)
            return
          case 'turn/end':
            void endTurn(session, data.reason)
            return
          case 'tool/call': {
            const state = turnState(session)
            state.work.push(summarizeToolCall(String(data.name ?? ''), String(data.arguments ?? '')))
            return
          }
          case 'assistant/message': {
            const state = turnState(session)
            const text = extractAssistantText(data.message)
            if (text.length > 0) state.summary = text.slice(0, 4000)
            return
          }
          default:
            return
        }
      } catch (err) {
        ctx.logger?.warn?.(`${LOG_TAG} session event failed: ${String((err && err.message) || err)}`)
      }
    }),
  )

  // `fs/observed` fires after a write already landed, so it cannot supply a
  // pre-image. It is used only to narrow the turn-end diff to the paths the
  // agent actually touched, which keeps a large workspace cheap to check.
  disposers.push(
    ctx.on('fs/observed', (target, observation, actor) => {
      try {
        const display = target && typeof target.displayPath === 'string' ? target.displayPath : undefined
        if (display === undefined) return
        observedCount += 1
        // Record the tool name for diagnostics but do NOT gate on it: the
        // host's writing tools are named `edit`, `write` or
        // `str_replace_editor` depending on which plugin provides them, and an
        // earlier name-based filter silently dropped every candidate. A read
        // raising this event is harmless — a path whose content never moved is
        // discarded by the hash comparison at turn end.
        const toolName = actor && typeof actor === 'object' && 'name' in actor ? String(actor.name) : 'unknown'
        for (const state of turns.values()) {
          const root = workspaceRootFor(state.session)
          const abs = path.isAbsolute(display) ? display : path.join(root, display)
          if (!isInside(root, abs)) continue
          const rel = path.relative(root, abs)
          if (engine.isExcluded(rel)) continue
          state.candidates.add(rel)
        }
        diag(`fs/observed tool=${toolName}`)
      } catch (err) {}
    }),
  )

  disposers.push(
    ctx.on('session/disposed', (session) => {
      turns.delete(sessionKey(session))
    }),
  )

  ctx.inject(['webServer'], (webCtx) => {
    const json = (res, body, code = 200) => {
      res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' })
      res.end(JSON.stringify(body))
    }
    const readBody = (req) =>
      new Promise((resolve, reject) => {
        const chunks = []
        let size = 0
        req.on('data', (c) => {
          size += c.length
          if (size > 1 << 20) {
            reject(new Error('body too large'))
            req.destroy()
            return
          }
          chunks.push(c)
        })
        req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')))
        req.on('error', reject)
      })

    const routes = [
      [
        '/dsh-snapshot/diag.json',
        (req, res) => {
          json(res, {
            ok: true,
            snapRoot: engine.snapRoot,
            observed: observedCount,
            liveTurns: [...turns.values()].map((t) => ({ key: t.key, turn: t.turn, candidates: [...t.candidates], work: t.work.length, began: t.began })),
            events: [...diagRing].reverse(),
          })
        },
      ],
      [
        '/dsh-snapshot/status.json',
        (req, res) => {
          try {
            const url = new URL(req.url, 'http://localhost')
            json(res, { ...engine.status(url.searchParams.get('root') || process.cwd()), observed: observedCount, snapRoot: engine.snapRoot })
          } catch (err) {
            json(res, { ok: false, error: String((err && err.message) || err) }, 500)
          }
        },
      ],
      [
        '/dsh-snapshot/notes.json',
        (req, res) => {
          try {
            const url = new URL(req.url, 'http://localhost')
            const root = url.searchParams.get('root') || process.cwd()
            const limit = Number(url.searchParams.get('limit') || 30)
            json(res, { ok: true, root, notes: engine.listNotes(root, Number.isFinite(limit) ? limit : 30) })
          } catch (err) {
            json(res, { ok: false, error: String((err && err.message) || err) }, 500)
          }
        },
      ],
      [
        '/dsh-snapshot/note.json',
        (req, res) => {
          try {
            const url = new URL(req.url, 'http://localhost')
            const root = url.searchParams.get('root') || process.cwd()
            const id = url.searchParams.get('id') || ''
            const delta = engine.noteDelta(root, id)
            if (delta === null) return json(res, { ok: false, error: `note not found: ${id}` }, 404)
            json(res, { ok: true, note: delta.note, entries: delta.entries })
          } catch (err) {
            json(res, { ok: false, error: String((err && err.message) || err) }, 500)
          }
        },
      ],
      [
        '/dsh-snapshot/revert.json',
        async (req, res) => {
          if (req.method !== 'POST' && req.method !== 'PUT') return json(res, { ok: false, error: 'POST required' }, 405)
          try {
            const body = JSON.parse(await readBody(req))
            const root = typeof body.root === 'string' && body.root.length > 0 ? body.root : process.cwd()
            const result = engine.revert({ root, id: String(body.id ?? ''), paths: body.paths, dryRun: body.dryRun === true })
            json(res, result, result.ok ? 200 : 400)
          } catch (err) {
            json(res, { ok: false, error: String((err && err.message) || err) }, 400)
          }
        },
      ],
    ]

    const registered = []
    for (const [routePath, handler] of routes) {
      try {
        registered.push(webCtx.webServer.register({ kind: 'exact', path: routePath, handler }))
      } catch (err) {
        ctx.logger?.warn?.(`${LOG_TAG} route ${routePath} failed: ${String((err && err.message) || err)}`)
      }
    }
    disposers.push(() => {
      for (const d of registered) {
        try {
          d()
        } catch (err) {}
      }
    })
  })

  ctx.effect(() => () => {
    for (const d of disposers) {
      try {
        d()
      } catch (err) {}
    }
  })

  ctx.logger?.info?.(`${LOG_TAG} ready (root: ${engine.snapRoot})`)
}

// ---------------------------------------------------------------------------
// CLI
// ---------------------------------------------------------------------------

function parseArgs(argv) {
  const out = { _: [], paths: [] }
  for (let i = 0; i < argv.length; i += 1) {
    const token = argv[i]
    if (token === '--path') {
      out.paths.push(argv[i + 1])
      i += 1
      continue
    }
    if (token.startsWith('--')) {
      const key = token.slice(2)
      const next = argv[i + 1]
      if (next === undefined || next.startsWith('--')) out[key] = true
      else {
        out[key] = next
        i += 1
      }
      continue
    }
    out._.push(token)
  }
  return out
}

function runCli(argv) {
  const args = parseArgs(argv)
  const command = args._[0]
  const root = args.root === true || args.root === undefined ? process.cwd() : args.root
  const engine = new SnapshotEngine({ root, log: (line) => process.stdout.write(`${line}\n`) })
  const print = (value) => process.stdout.write(`${JSON.stringify(value, null, 2)}\n`)

  switch (command) {
    case 'status':
      return print(engine.status(root))
    case 'list':
      return print({ ok: true, root, notes: engine.listNotes(root, Number(args.limit ?? 20)) })
    case 'show': {
      const delta = engine.noteDelta(root, String(args.id ?? ''))
      return print(delta === null ? { ok: false, error: `note not found: ${args.id}` } : { ok: true, note: delta.note, entries: delta.entries })
    }
    case 'revert':
      return print(
        args.before === true
          ? engine.revertBefore({ root, id: String(args.id ?? ''), paths: args.paths, dryRun: args['dry-run'] === true })
          : engine.revert({ root, id: String(args.id ?? ''), paths: args.paths, dryRun: args['dry-run'] === true }),
      )
    case 'gc':
      return print(engine.gc(root))
    default:
      process.stdout.write(
        [
          'dsh-snapshot CLI',
          '',
          '  node lib/index.js status   [--root DIR]',
          '  node lib/index.js list     [--root DIR] [--limit N]',
          '  node lib/index.js show     --id NOTE [--root DIR]',
          '  node lib/index.js revert   --id NOTE [--root DIR] [--path P ...] [--dry-run] [--before]',
          '  node lib/index.js gc       [--root DIR]',
          '',
          '  revert            restores the tree as of that turn\'s END',
          '  revert --before   undoes that turn (restores its pre-turn state)',
          '  --path P          restrict a revert to the given relative path (repeatable)',
          '',
        ].join('\n'),
      )
      return undefined
  }
}

const isDirectRun = process.argv[1] !== undefined && path.resolve(process.argv[1]) === path.resolve(fileURLToPath(import.meta.url))
if (isDirectRun) {
  try {
    runCli(process.argv.slice(2))
  } catch (err) {
    process.stderr.write(`${LOG_TAG} ${String((err && err.stack) || err)}\n`)
    process.exitCode = 1
  }
}

export { SnapshotEngine }
