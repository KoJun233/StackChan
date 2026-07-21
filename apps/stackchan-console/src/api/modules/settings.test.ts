import { afterEach, describe, expect, it, vi } from 'vitest'
import { getSpeechSettings, saveSpeechSettings, testSpeechConnection } from './settings'

describe('speech settings API', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('loads, saves and tests the encrypted speech provider configuration', async () => {
    const saved = {
      apiKeyConfigured: true,
      asrMode: 'NON_REALTIME' as const,
      asrModel: 'whisper-1',
      baseUrl: 'https://speech.example.com/v1',
      providerType: 'OPENAI_COMPATIBLE' as const,
      speechSilenceThreshold: 200,
      speechStartThreshold: 350,
      ttsMode: 'NON_REALTIME' as const,
      ttsModel: 'tts-1',
      ttsVoice: 'alloy',
      updatedAt: '2026-07-19T10:00:00Z',
      wakeSensitivity: 'SENSITIVE' as const,
      workspaceId: '',
    }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(saved), {
        headers: { 'Content-Type': 'application/json' },
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify(saved), {
        headers: { 'Content-Type': 'application/json' },
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ ok: true, message: 'ok' }), {
        headers: { 'Content-Type': 'application/json' },
      }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(getSpeechSettings()).resolves.toEqual(saved)
    await expect(saveSpeechSettings({
      apiKey: 'secret',
      asrMode: 'NON_REALTIME',
      asrModel: 'whisper-1',
      baseUrl: 'https://speech.example.com/v1',
      providerType: 'OPENAI_COMPATIBLE',
      speechSilenceThreshold: 200,
      speechStartThreshold: 350,
      ttsMode: 'NON_REALTIME',
      ttsModel: 'tts-1',
      ttsVoice: 'alloy',
      wakeSensitivity: 'SENSITIVE',
      workspaceId: '',
    })).resolves.toEqual(saved)
    await expect(testSpeechConnection()).resolves.toEqual({ ok: true, message: 'ok' })

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/settings/speech', expect.any(Object))
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/settings/speech', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({
        apiKey: 'secret',
        asrMode: 'NON_REALTIME',
        asrModel: 'whisper-1',
        baseUrl: 'https://speech.example.com/v1',
        providerType: 'OPENAI_COMPATIBLE',
        speechSilenceThreshold: 200,
        speechStartThreshold: 350,
        ttsMode: 'NON_REALTIME',
        ttsModel: 'tts-1',
        ttsVoice: 'alloy',
        wakeSensitivity: 'SENSITIVE',
        workspaceId: '',
      }),
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/v1/settings/speech/test', expect.objectContaining({
      method: 'POST',
    }))
  })
})
