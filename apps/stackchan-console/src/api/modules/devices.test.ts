import { afterEach, describe, expect, it, vi } from 'vitest'
import { createPairingCode, isPairingCodeExpired, stopDeviceMotion } from './devices'

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
})
