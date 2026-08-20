import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  activateExpressionPack,
  createExpressionPack,
  deactivateExpressionPack,
  deleteExpressionPack,
  expressionStates,
  getDeviceExpressionPack,
  getExpressionFrameRate,
  listExpressionPacks,
  previewExpression,
  updateExpressionFrameRate,
} from './expressionPacks'

describe('expression resource pack API', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('creates a multipart package containing exactly the eight stable states', async () => {
    const pack = { id: 'pack-id', name: '机械宠物', states: expressionStates.map(item => item.value) }
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(pack), {
      status: 201,
      headers: { 'Content-Type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetchMock)
    const images = Object.fromEntries(expressionStates.map(({ value }) => [
      value,
      new File([value], `${value}.png`, { type: 'image/png' }),
    ])) as Parameters<typeof createExpressionPack>[0]['images']

    await expect(createExpressionPack({ name: '机械宠物', description: '测试', images })).resolves.toEqual(pack)

    const request = fetchMock.mock.calls[0]
    const form = (request[1] as RequestInit).body as FormData
    expect(request[0]).toBe('/api/v1/expression-packs')
    expect(request[1]).toEqual(expect.objectContaining({ method: 'POST', credentials: 'same-origin' }))
    expect(form.get('name')).toBe('机械宠物')
    expect(expressionStates.every(({ value }) => form.get(value) === images[value])).toBe(true)
  })

  it('lists packages and controls one encoded device selection', async () => {
    const selection = { deviceId: 'device/id', enabled: true, packId: 'pack/id', status: 'READY' }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ packs: [] }), { headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify(selection), { headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify(selection), { headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ ...selection, enabled: false }), { headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(listExpressionPacks()).resolves.toEqual([])
    await expect(getDeviceExpressionPack('device/id')).resolves.toEqual(selection)
    await expect(activateExpressionPack('pack/id', 'device/id')).resolves.toEqual(selection)
    await expect(deactivateExpressionPack('device/id')).resolves.toEqual({ ...selection, enabled: false })
    await expect(deleteExpressionPack('pack/id')).resolves.toBeUndefined()

    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/expression-packs/device?deviceId=device%2Fid', expect.any(Object))
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/v1/expression-packs/pack%2Fid/activate', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ deviceId: 'device/id' }),
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(5, '/api/v1/expression-packs/pack%2Fid', expect.objectContaining({ method: 'DELETE' }))
  })

  it('persists frame-rate policy and sends bounded semantic previews', async () => {
    const settings = { mode: 'ADAPTIVE', minFps: 24, maxFps: 57, applied: true } as const
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(settings), { headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify(settings), { headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ accepted: true }), { status: 202, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(getExpressionFrameRate('device/id')).resolves.toEqual(settings)
    await expect(updateExpressionFrameRate('device/id', {
      mode: 'ADAPTIVE', minFps: 24, maxFps: 57,
    })).resolves.toEqual(settings)
    await expect(previewExpression('device/id', {
      category: 'EMOTION', value: 'HAPPY', durationSeconds: 5,
    })).resolves.toBeUndefined()

    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/devices/device%2Fid/expression/frame-rate', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({ mode: 'ADAPTIVE', minFps: 24, maxFps: 57 }),
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/v1/devices/device%2Fid/expression/preview', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ category: 'EMOTION', value: 'HAPPY', durationSeconds: 5 }),
    }))
  })
})
