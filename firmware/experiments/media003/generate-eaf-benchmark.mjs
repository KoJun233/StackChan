import { createHash } from 'node:crypto'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

export const WIDTH = 160
export const HEIGHT = 160
export const FRAME_COUNT = 18
export const BLOCK_HEIGHT = 16

const PALETTE = [
  [0x00, 0x00, 0x00, 0xff], // black background, BGRA
  [0xb5, 0xc3, 0xcf, 0xff], // body shadow
  [0xdf, 0xea, 0xf3, 0xff], // cream body
  [0xf1, 0xf9, 0xff, 0xff], // highlight
  [0x11, 0x11, 0x11, 0xff], // eyes
  [0xa3, 0x4f, 0xff, 0xff], // role accent
  [0xcb, 0x8b, 0xff, 0xff], // light accent
  [0x9a, 0x82, 0xe9, 0xff], // blush
  ...Array.from({ length: 8 }, () => [0x00, 0x00, 0x00, 0xff]),
]

function writeU16(value) {
  const buffer = Buffer.alloc(2)
  buffer.writeUInt16LE(value)
  return buffer
}

function writeU32(value) {
  const buffer = Buffer.alloc(4)
  buffer.writeUInt32LE(value >>> 0)
  return buffer
}

function insideEllipse(x, y, cx, cy, rx, ry) {
  const dx = (x - cx) / rx
  const dy = (y - cy) / ry
  return dx * dx + dy * dy <= 1
}

function rasterizeFrame(frameIndex) {
  const pixels = new Uint8Array(WIDTH * HEIGHT)
  const phase = frameIndex / FRAME_COUNT * Math.PI * 2
  const cx = 80 + Math.round(Math.sin(phase) * 2)
  const cy = 80 + Math.round(Math.cos(phase) * 2)
  const scale = 1 + Math.sin(phase) * 0.025
  const rx = 64 * scale
  const ry = 64 / scale
  const blink = frameIndex === 8 || frameIndex === 9 ? 0.18 : 1
  const eyeHeight = Math.max(3, Math.round(18 * blink))
  const eyeY = cy - 7

  for (let y = 0; y < HEIGHT; y++) {
    for (let x = 0; x < WIDTH; x++) {
      let color = 0
      if (insideEllipse(x, y, cx, cy + 4, rx + 4, ry + 4)) color = 1
      if (insideEllipse(x, y, cx, cy, rx, ry)) color = 2
      if (insideEllipse(x, y, cx - 4, cy - 6, rx - 7, ry - 8)) color = 3
      if (insideEllipse(x, y, cx, cy, rx - 12, ry - 12)) color = 2
      if (insideEllipse(x, y, cx - 25, eyeY, 10, eyeHeight)) color = 4
      if (insideEllipse(x, y, cx + 25, eyeY, 10, eyeHeight)) color = 4
      if (blink > 0.5 && insideEllipse(x, y, cx - 43, cy + 24, 8, 4)) color = 7
      if (blink > 0.5 && insideEllipse(x, y, cx + 43, cy + 24, 8, 4)) color = 7
      pixels[y * WIDTH + x] = color
    }
  }

  const orbitAngle = phase * 1.7
  const particleX = Math.round(cx + Math.cos(orbitAngle) * 70)
  const particleY = Math.round(cy + Math.sin(orbitAngle) * 70)
  for (let y = particleY - 3; y <= particleY + 3; y++) {
    for (let x = particleX - 3; x <= particleX + 3; x++) {
      if (x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT
        && insideEllipse(x, y, particleX, particleY, 3, 3)) {
        pixels[y * WIDTH + x] = frameIndex % 2 === 0 ? 5 : 6
      }
    }
  }
  return pixels
}

function pack4Bit(pixels) {
  const packed = Buffer.alloc(Math.ceil(pixels.length / 2))
  for (let index = 0; index < pixels.length; index += 2) {
    packed[index / 2] = (pixels[index] << 4) | (pixels[index + 1] ?? 0)
  }
  return packed
}

function encodeRle(bytes) {
  const output = []
  for (let index = 0; index < bytes.length;) {
    const value = bytes[index]
    let count = 1
    while (index + count < bytes.length && bytes[index + count] === value && count < 255) count++
    output.push(count, value)
    index += count
  }
  return Buffer.from(output)
}

function buildFrame(pixels) {
  const blocks = []
  for (let y = 0; y < HEIGHT; y += BLOCK_HEIGHT) {
    const rows = pixels.subarray(y * WIDTH, Math.min(y + BLOCK_HEIGHT, HEIGHT) * WIDTH)
    blocks.push(Buffer.concat([Buffer.from([0]), encodeRle(pack4Bit(rows))]))
  }
  const header = Buffer.concat([
    Buffer.from('_S\0'),
    Buffer.from('1.0.0\0'),
    Buffer.from([4]),
    writeU16(WIDTH),
    writeU16(HEIGHT),
    writeU16(blocks.length),
    writeU16(BLOCK_HEIGHT),
    ...blocks.map(block => writeU32(block.length)),
    Buffer.from(PALETTE.flat()),
  ])
  return Buffer.concat([Buffer.from([0x5a, 0x5a]), header, ...blocks])
}

export function createBenchmarkEaf() {
  const frames = Array.from({ length: FRAME_COUNT }, (_, index) => buildFrame(rasterizeFrame(index)))
  let frameOffset = 0
  const table = []
  for (const frame of frames) {
    table.push(writeU32(frame.length), writeU32(frameOffset))
    frameOffset += frame.length
  }
  const payload = Buffer.concat([...table, ...frames])
  let checksum = 0
  for (const byte of payload) checksum = (checksum + byte) >>> 0
  return Buffer.concat([
    Buffer.from([0x89]),
    Buffer.from('EAF'),
    writeU32(frames.length),
    writeU32(checksum),
    writeU32(payload.length),
    payload,
  ])
}

export function inspectEaf(buffer) {
  if (buffer.length < 16 || buffer[0] !== 0x89 || buffer.subarray(1, 4).toString() !== 'EAF') {
    throw new Error('invalid EAF signature')
  }
  const frames = buffer.readUInt32LE(4)
  const checksum = buffer.readUInt32LE(8)
  const payloadLength = buffer.readUInt32LE(12)
  if (16 + payloadLength !== buffer.length) throw new Error('invalid EAF payload length')
  let calculated = 0
  for (const byte of buffer.subarray(16)) calculated = (calculated + byte) >>> 0
  if (calculated !== checksum) throw new Error('invalid EAF checksum')
  const frameRegion = 16 + frames * 8
  for (let index = 0; index < frames; index++) {
    const size = buffer.readUInt32LE(16 + index * 8)
    const offset = buffer.readUInt32LE(20 + index * 8)
    const start = frameRegion + offset
    if (start + size > buffer.length || buffer.readUInt16LE(start) !== 0x5a5a) {
      throw new Error(`invalid EAF frame ${index}`)
    }
    if (buffer.subarray(start + 2, start + 4).toString() !== '_S') {
      throw new Error(`invalid EAF frame header ${index}`)
    }
  }
  return {
    bytes: buffer.length,
    checksum,
    frames,
    height: buffer.readUInt16LE(frameRegion + 2 + 12),
    payloadLength,
    sha256: createHash('sha256').update(buffer).digest('hex').toUpperCase(),
    width: buffer.readUInt16LE(frameRegion + 2 + 10),
  }
}

async function main() {
  const scriptDirectory = path.dirname(fileURLToPath(import.meta.url))
  const defaultOutput = path.join(scriptDirectory, 'eaf_probe', 'assets', 'media003-benchmark.eaf')
  const outputArgument = process.argv.indexOf('--output')
  const output = outputArgument >= 0 ? path.resolve(process.argv[outputArgument + 1]) : defaultOutput
  const eaf = createBenchmarkEaf()
  await mkdir(path.dirname(output), { recursive: true })
  await writeFile(output, eaf)
  const stored = await readFile(output)
  process.stdout.write(`${JSON.stringify(inspectEaf(stored), null, 2)}\n`)
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await main()
