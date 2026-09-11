/**
 * mtime-precision probe.
 *
 * The scan's fast path skips a file whose (size, mtime) pair is unchanged. If
 * the filesystem reports coarse mtimes, several writes to one file inside the
 * same tick look identical and the whole turn is skipped silently — which is
 * exactly the "no note was recorded" symptom.
 *
 * Run: node tests/debug.mtime.mjs
 */
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'

const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'dsh-mtime-'))
const file = path.join(dir, 'a.txt')

const samples = []
for (let i = 0; i < 6; i += 1) {
  fs.writeFileSync(file, `content ${i} ${'x'.repeat(i * 10)}\n`)
  const s = fs.statSync(file)
  samples.push({ i, size: s.size, mtimeMs: s.mtimeMs })
  // Busy-wait a moment so a coarse clock has a chance to advance differently.
  await new Promise((r) => setTimeout(r, 1))
}

console.log('=== writes to one file, ~1ms apart ===')
for (const s of samples) {
  console.log(`  write ${s.i}: size=${String(s.size).padStart(3)} mtimeMs=${s.mtimeMs}`)
}

const sameSize = samples.filter((s) => s.size === samples[0].size).length
const distinctMtimes = new Set(samples.map((s) => s.mtimeMs)).size
console.log(`\n  distinct mtime values: ${distinctMtimes} of ${samples.length}`)
console.log(`  fs.statSync mtimeMs has sub-ms precision: ${String(samples[0].mtimeMs).includes('.')}`)

// The dangerous case: same size, same mtime, different content.
const a = path.join(dir, 'same.txt')
fs.writeFileSync(a, 'AAAA\n')
const s1 = fs.statSync(a)
fs.writeFileSync(a, 'BBBB\n')
const s2 = fs.statSync(a)
console.log('\n=== same-size rewrite ===')
console.log(`  before: size=${s1.size} mtimeMs=${s1.mtimeMs}`)
console.log(`  after : size=${s2.size} mtimeMs=${s2.mtimeMs}`)
console.log(`  fast path would SKIP this file: ${s1.size === s2.size && s1.mtimeMs === s2.mtimeMs}`)
console.log(`  content really changed: ${fs.readFileSync(a, 'utf8') === 'BBBB\n'}`)

fs.rmSync(dir, { recursive: true, force: true })
