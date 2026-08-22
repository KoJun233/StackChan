import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

import {
  FRAME_COUNT,
  HEIGHT,
  WIDTH,
  createBenchmarkEaf,
  inspectEaf,
} from './generate-eaf-benchmark.mjs'

test('generates deterministic project-owned EAF benchmark animation', () => {
  const first = createBenchmarkEaf()
  const second = createBenchmarkEaf()
  assert.deepEqual(first, second)
  const metadata = inspectEaf(first)
  assert.equal(metadata.frames, FRAME_COUNT)
  assert.equal(metadata.width, WIDTH)
  assert.equal(metadata.height, HEIGHT)
  assert.equal(metadata.bytes, 53_854)
  assert.equal(
    metadata.sha256,
    'ABD64A59F59CAFF9CEE7A65921781BF6FE28B150AD876ADDF8CF52505690FE81',
  )
})

test('rejects checksum and frame table corruption', () => {
  const checksumCorruption = Buffer.from(createBenchmarkEaf())
  checksumCorruption[checksumCorruption.length - 1] ^= 0xff
  assert.throws(() => inspectEaf(checksumCorruption), /checksum/)

  const frameCorruption = Buffer.from(createBenchmarkEaf())
  const frames = frameCorruption.readUInt32LE(4)
  const frameRegion = 16 + frames * 8
  frameCorruption[frameRegion] = 0
  let checksum = 0
  for (const byte of frameCorruption.subarray(16)) checksum = (checksum + byte) >>> 0
  frameCorruption.writeUInt32LE(checksum, 8)
  assert.throws(() => inspectEaf(frameCorruption), /frame 0/)
})

test('keeps the embedded benchmark asset synchronized with the generator', async () => {
  const directory = path.dirname(fileURLToPath(import.meta.url))
  const stored = await readFile(path.join(
    directory,
    'eaf_probe',
    'assets',
    'media003-benchmark.eaf',
  ))
  assert.deepEqual(stored, createBenchmarkEaf())
})
