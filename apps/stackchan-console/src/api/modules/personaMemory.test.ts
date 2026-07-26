import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  clearMemories,
  confirmMemory,
  createMemory,
  listMemories,
  savePersona,
  setMemoryEnabled,
} from './personaMemory'

describe('persona and memory API', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('queries memory filters with stable paging parameters', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ list: [], total: 0 }), {
      headers: { 'Content-Type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetchMock)

    await listMemories({
      from: 20,
      limit: 10,
      query: '称呼',
      category: 'USER_PROFILE',
      confirmationStatus: 'CONFIRMED',
      scopeType: 'DEVICE',
      deviceId: 'device-id',
      enabled: true,
    })

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/memories?from=20&limit=10&query=%E7%A7%B0%E5%91%BC&category=USER_PROFILE&confirmationStatus=CONFIRMED&enabled=true&scopeType=DEVICE&deviceId=device-id',
      expect.any(Object),
    )
  })

  it('uses explicit mutation endpoints for persona and memory controls', async () => {
    const persona = {
      displayName: '小栈',
      tone: 'WARM' as const,
      replyLength: 'SHORT' as const,
      proactivity: 'BALANCED' as const,
      topicBoundaries: '',
      taboos: '',
    }
    const memory = {
      scopeType: 'GLOBAL' as const,
      deviceId: null,
      category: 'EVENT' as const,
      title: '项目进度',
      content: '完成联调',
    }
    const responseBody = { id: 'memory-id', ...memory }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(persona))
      .mockResolvedValueOnce(jsonResponse(responseBody, 201))
      .mockResolvedValueOnce(jsonResponse(responseBody))
      .mockResolvedValueOnce(jsonResponse(responseBody))
      .mockResolvedValueOnce(jsonResponse({ deletedCount: 1 }))
    vi.stubGlobal('fetch', fetchMock)

    await savePersona(persona)
    await createMemory(memory)
    await confirmMemory('memory-id')
    await setMemoryEnabled('memory-id', false)
    await clearMemories()

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/persona', expect.objectContaining({ method: 'PUT' }))
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/memories', expect.objectContaining({ method: 'POST' }))
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/v1/memories/memory-id:confirm', expect.objectContaining({ method: 'POST' }))
    expect(fetchMock).toHaveBeenNthCalledWith(4, '/api/v1/memories/memory-id/enabled', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({ enabled: false }),
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(5, '/api/v1/memories:clear', expect.objectContaining({
      body: JSON.stringify({}),
    }))
  })
})

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}
