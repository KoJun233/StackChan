import { afterEach, describe, expect, it, vi } from 'vitest'
import { createWakeWordModelJob, listWakeWordModelJobs, listWakeWordModels } from './wakeWords'

describe('wake word model API', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('creates a persistent built-in model installation job', async () => {
    const body = {
      id: 'job-id',
      deviceId: 'device-id',
      modelName: 'wn9_xiao3feng1xiao3feng1_tts3',
      phrase: '小峰小峰',
      status: 'READY',
    }
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(body), {
      status: 202,
      headers: { 'Content-Type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(createWakeWordModelJob('device-id', 'wn9_xiao3feng1xiao3feng1_tts3')).resolves.toEqual(body)
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/wake-word-model-jobs', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ deviceId: 'device-id', modelName: 'wn9_xiao3feng1xiao3feng1_tts3' }),
    }))
  })

  it('lists jobs for one encoded device id', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ jobs: [] }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(listWakeWordModelJobs('device/id')).resolves.toEqual([])
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/wake-word-model-jobs?deviceId=device%2Fid',
      expect.any(Object),
    )
  })

  it('lists the trusted built-in model catalog', async () => {
    const models = [{ locale: 'zh-CN', modelName: 'wn9_xiao3feng1xiao3feng1_tts3', phrase: '小峰小峰' }]
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ models }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(listWakeWordModels()).resolves.toEqual(models)
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/wake-word-model-jobs/catalog', expect.any(Object))
  })
})
