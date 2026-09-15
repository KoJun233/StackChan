import { afterEach, describe, expect, it, vi } from 'vitest'
import { getInteractionSettings, getProactivePause, listProactiveTopics, pauseProactive, resumeProactive, resumeProactiveTopic, saveInteractionSettings, stopDeviceAudio } from './interactions'

it('loads pauses and resumes only the selected device and partner', async () => {
  const fetchMock = vi.fn().mockImplementation(async () => new Response(JSON.stringify({ paused: true }), {
    headers: { 'Content-Type': 'application/json' },
  }))
  vi.stubGlobal('fetch', fetchMock)
  const path = '/api/v1/settings/interactions/device/roles/partner/proactive-pause'
  await getProactivePause('device', 'partner')
  await pauseProactive('device', 'partner', null)
  await pauseProactive('device', 'partner', 60)
  await resumeProactive('device', 'partner')
  expect(fetchMock).toHaveBeenNthCalledWith(1, path, expect.any(Object))
  expect(fetchMock).toHaveBeenNthCalledWith(2, path, expect.objectContaining({ method: 'PUT', body: '{"minutes":null}' }))
  expect(fetchMock).toHaveBeenNthCalledWith(3, path, expect.objectContaining({ method: 'PUT', body: '{"minutes":60}' }))
  expect(fetchMock).toHaveBeenNthCalledWith(4, path, expect.objectContaining({ method: 'DELETE' }))
})

it('keeps topic listing and resume in the explicitly selected role', async () => {
  const fetchMock = vi.fn().mockImplementation(async () => new Response(JSON.stringify([]), {
    headers: { 'Content-Type': 'application/json' },
  }))
  vi.stubGlobal('fetch', fetchMock)
  await listProactiveTopics('device', 'role-b')
  await resumeProactiveTopic('device', '咖啡偏好', 'role-b')
  expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/settings/interactions/device/proactive-topics?roleId=role-b', expect.any(Object))
  expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/settings/interactions/device/proactive-topics:resume?roleId=role-b', expect.objectContaining({
    method: 'POST',
    body: JSON.stringify({ topicKey: '咖啡偏好' }),
  }))
})

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
      proactiveMinIntervalMinutes: 60,
      proactivePersonalizationEnabled: false,
      proactiveDailyLimit: 3,
      proactiveContent: '你好',
      silentPresenceEnabled: false,
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
