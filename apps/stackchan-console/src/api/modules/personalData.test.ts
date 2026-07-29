import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  deletePersonalDataConversation,
  deletePersonalDataMessage,
  exportPersonalDataConversations,
  getBackupStatus,
  getPersonalDataMessages,
  listPersonalDataConversations,
} from './personalData'

describe('personal data API', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('uses encoded admin-only list, detail, delete and status contracts', async () => {
    const fetchMock = vi.fn().mockImplementation(async () => new Response(JSON.stringify({ list: [], total: 0 }), {
      headers: { 'Content-Type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetchMock)

    await listPersonalDataConversations({
      from: 20,
      limit: 20,
      query: '旅行 计划',
      deviceId: 'device/id',
      fromTime: '2026-07-01T00:00:00.000Z',
      toTime: '2026-07-31T23:59:59.000Z',
    })
    await getPersonalDataMessages('conversation/id')
    await deletePersonalDataMessage('conversation/id', 'message/id')
    await deletePersonalDataConversation('conversation/id')
    await getBackupStatus()

    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/personal-data/conversations?from=20&limit=20&query=%E6%97%85%E8%A1%8C+%E8%AE%A1%E5%88%92&deviceId=device%2Fid&fromTime=2026-07-01T00%3A00%3A00.000Z&toTime=2026-07-31T23%3A59%3A59.000Z')
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/personal-data/conversations/conversation%2Fid/messages', expect.any(Object))
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/v1/personal-data/conversations/conversation%2Fid/messages/message%2Fid', expect.objectContaining({ method: 'DELETE' }))
    expect(fetchMock).toHaveBeenNthCalledWith(4, '/api/v1/personal-data/conversations/conversation%2Fid', expect.objectContaining({ method: 'DELETE' }))
    expect(fetchMock).toHaveBeenNthCalledWith(5, '/api/v1/personal-data/backups/status', expect.any(Object))
  })

  it('downloads only the requested export scope and uses the safe server filename', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('{"schemaVersion":1}', {
      headers: {
        'Content-Type': 'application/json',
        'Content-Disposition': "attachment; filename*=UTF-8''stackchan-conversations-20260729.json",
      },
    }))
    vi.stubGlobal('fetch', fetchMock)

    const result = await exportPersonalDataConversations({
      from: 0,
      limit: 20,
      query: '只导出这个',
      conversationId: 'conversation-id',
    })

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/personal-data/conversations:export?query=%E5%8F%AA%E5%AF%BC%E5%87%BA%E8%BF%99%E4%B8%AA&conversationId=conversation-id',
      expect.objectContaining({ credentials: 'same-origin' }),
    )
    expect(result.fileName).toBe('stackchan-conversations-20260729.json')
    await expect(result.blob.text()).resolves.toContain('"schemaVersion":1')
  })
})
