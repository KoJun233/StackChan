import { describe, expect, it, vi } from 'vitest'
import { createPairingCode, getLlmSettings, listDevices, saveLlmSettings, stopMotion } from './api'

describe('device API client', () => {
  it('maps the device list API shape', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      devices: [{
        id: '4e7e985e-8f4c-46f2-a691-d2ec792e8423',
        displayName: 'Studio StackChan',
        firmwareVersion: '1.4.2',
        safetyState: 'motion_enabled',
        lastSeenAt: '2026-07-17T14:59:30Z',
        online: true,
      }],
    }), { status: 200 })))

    await expect(listDevices()).resolves.toEqual([{
      id: '4e7e985e-8f4c-46f2-a691-d2ec792e8423',
      displayName: 'Studio StackChan',
      firmwareVersion: '1.4.2',
      safetyState: 'motion_enabled',
      lastSeenAt: '2026-07-17T14:59:30Z',
      online: true,
    }])
  })

  it('rejects a device list without the server-derived online state', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      devices: [{
        id: '4e7e985e-8f4c-46f2-a691-d2ec792e8423',
        displayName: 'Studio StackChan',
        firmwareVersion: '1.4.2',
        safetyState: 'motion_enabled',
      }],
    }), { status: 200 })))

    await expect(listDevices()).rejects.toThrow('无法加载设备。')
  })

  it('reports a stop command as pending from the 202 response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 202 })))

    await expect(stopMotion('device-id')).resolves.toEqual({ status: 'pending' })
  })

  it('reports an offline stop command from the 409 response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ error: 'device_offline' }), {
      status: 409,
      headers: { 'content-type': 'application/json' },
    })))

    await expect(stopMotion('device-id')).resolves.toEqual({ status: 'offline' })
  })

  it('sends a non-empty creator to the pairing API', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ value: 'SC-1284' }), { status: 201 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(createPairingCode('Console operator')).resolves.toEqual('SC-1284')
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/pairing/codes', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ createdBy: 'Console operator' }),
    }))
  })

  it('loads LLM settings without any API key in the response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      baseUrl: 'https://api.example.com/v1',
      model: 'companion-model',
      systemPrompt: 'prompt',
      apiKeyConfigured: true,
      updatedAt: '2026-07-17T12:00:00Z',
    }), { status: 200 })))

    await expect(getLlmSettings()).resolves.toEqual({
      baseUrl: 'https://api.example.com/v1',
      model: 'companion-model',
      systemPrompt: 'prompt',
      apiKeyConfigured: true,
      updatedAt: '2026-07-17T12:00:00Z',
    })
  })

  it('saves LLM settings to the dedicated API endpoint', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      baseUrl: 'https://api.example.com/v1',
      model: 'companion-model',
      systemPrompt: 'prompt',
      apiKeyConfigured: true,
      updatedAt: '2026-07-17T12:00:00Z',
    }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await saveLlmSettings({
      baseUrl: 'https://api.example.com/v1',
      model: 'companion-model',
      systemPrompt: 'prompt',
      apiKey: 'sk-secret',
    })

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/settings/llm', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({
        baseUrl: 'https://api.example.com/v1',
        model: 'companion-model',
        systemPrompt: 'prompt',
        apiKey: 'sk-secret',
      }),
    }))
  })
})
