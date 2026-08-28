import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  calibrateDeviceBody,
  configureDeviceBodyMotion,
  createPairingCode,
  isPairingCodeExpired,
  playDeviceBodyMotion,
  stopDeviceMotion,
} from './devices'

describe('device management API', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('creates a one-time pairing code for the administrator', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      value: 'ABCD_123',
      expiresAt: '2026-07-18T12:10:00Z',
    }), { status: 201, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(createPairingCode('admin')).resolves.toEqual({
      value: 'ABCD_123',
      expiresAt: '2026-07-18T12:10:00Z',
    })
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/pairing/codes', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ createdBy: 'admin' }),
    }))
  })

  it('treats a pairing code as expired at or after its expiry instant', () => {
    const pairingCode = {
      value: 'ABCD_123',
      expiresAt: '2026-07-18T12:10:00Z',
    }
    const expiresAt = Date.parse(pairingCode.expiresAt)

    expect(isPairingCodeExpired(pairingCode, expiresAt - 1)).toBe(false)
    expect(isPairingCodeExpired(pairingCode, expiresAt)).toBe(true)
    expect(isPairingCodeExpired(pairingCode, expiresAt + 1)).toBe(true)
  })

  it('sends the safety stop command to an online device', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 202 }))
    vi.stubGlobal('fetch', fetchMock)

    await stopDeviceMotion('device-id')

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/devices/device-id/commands/stop-motion',
      expect.objectContaining({
        method: 'POST',
        credentials: 'same-origin',
      }),
    )
  })

  it('sends only structured body calibration, enable, and named motion commands', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 202 }))
    vi.stubGlobal('fetch', fetchMock)

    await calibrateDeviceBody('device/id')
    await configureDeviceBodyMotion('device/id', true)
    await playDeviceBodyMotion('device/id', 'NOD_SMALL')

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      '/api/v1/devices/device%2Fid/commands/calibrate-body',
      expect.objectContaining({ method: 'POST' }),
    )
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      '/api/v1/devices/device%2Fid/body-motion',
      expect.objectContaining({ method: 'PUT', body: '{"enabled":true}' }),
    )
    expect(fetchMock).toHaveBeenNthCalledWith(
      3,
      '/api/v1/devices/device%2Fid/commands/body-motion',
      expect.objectContaining({ method: 'POST', body: '{"motion":"NOD_SMALL"}' }),
    )
  })
})
