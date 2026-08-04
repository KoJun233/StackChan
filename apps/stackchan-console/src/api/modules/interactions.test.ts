import { afterEach, describe, expect, it, vi } from 'vitest'
import { getInteractionSettings, listProactiveTopics, resumeProactiveTopic, saveInteractionSettings, stopDeviceAudio } from './interactions'

describe('interaction settings API', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('loads, saves and immediately stops a selected device', async () => {
    const deviceId = 'a88e4a94-8536-4fa1-91ed-8681b597429d'
    const input = {
      volumePercent: 50,
      nightMode: false,
      continuousConversationEnabled: true,
      followUpWindowSeconds: 8,
      dndEnabled: true,
      dndStart: '22:00',
      dndEnd: '07:00',
      zoneId: 'Asia/Shanghai',
      missedReminderPolicy: 'PLAY_NOW' as const,
      missedSnoozeMinutes: 10,
      proactiveEnabled: false,
      proactiveStart: '09:00',
      proactiveEnd: '21:00',
      proactiveMinIntervalMinutes: 240,
      proactivePersonalizationEnabled: false,
      proactiveDailyLimit: 2,
      proactiveContent: '你好',
    }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ deviceId, ...input }), {
        headers: { 'Content-Type': 'application/json' },
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ deviceId, ...input }), {
        headers: { 'Content-Type': 'application/json' },
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ accepted: true }), {
        headers: { 'Content-Type': 'application/json' },
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify([]), {
        headers: { 'Content-Type': 'application/json' },
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ topicKey: '咖啡偏好', userMuted: false }), {
        headers: { 'Content-Type': 'application/json' },
      }))
    vi.stubGlobal('fetch', fetchMock)

    await getInteractionSettings(deviceId)
    await saveInteractionSettings(deviceId, input)
    await stopDeviceAudio(deviceId)
    await listProactiveTopics(deviceId)
    await resumeProactiveTopic(deviceId, '咖啡偏好')

    expect(fetchMock).toHaveBeenNthCalledWith(1, `/api/v1/settings/interactions/${deviceId}`, expect.any(Object))
    expect(fetchMock).toHaveBeenNthCalledWith(2, `/api/v1/settings/interactions/${deviceId}`, expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify(input),
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(3, `/api/v1/settings/interactions/${deviceId}:stop`, expect.objectContaining({
      method: 'POST',
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(4, `/api/v1/settings/interactions/${deviceId}/proactive-topics`, expect.any(Object))
    expect(fetchMock).toHaveBeenNthCalledWith(5, `/api/v1/settings/interactions/${deviceId}/proactive-topics:resume`, expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ topicKey: '咖啡偏好' }),
    }))
  })
})
