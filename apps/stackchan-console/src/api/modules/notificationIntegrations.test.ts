import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  createNotificationIntegration,
  deleteExternalNotification,
  deleteNotificationIntegration,
  issueNotificationToken,
  listExternalNotifications,
  revokeNotificationToken,
  testNotificationIntegration,
} from './notificationIntegrations'

describe('notification integrations API', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('uses administrator endpoints for integration and one-time token management', async () => {
    const input = {
      name: 'Codex',
      deviceId: 'a88e4a94-8536-4fa1-91ed-8681b597429d',
      enabled: true,
    }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'integration-id', ...input, tokens: [] }), {
        status: 201,
        headers: { 'Content-Type': 'application/json' },
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: 'one-time-token', metadata: { id: 'token-id' } }), {
        status: 201,
        headers: { 'Content-Type': 'application/json' },
      }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await createNotificationIntegration(input)
    await issueNotificationToken('integration-id', null)
    await revokeNotificationToken('integration-id', 'token-id')
    await deleteNotificationIntegration('integration-id')

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/notification-integrations', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify(input),
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/notification-integrations/integration-id/tokens', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ expiresAt: null }),
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/v1/notification-integrations/integration-id/tokens/token-id', expect.objectContaining({
      method: 'DELETE',
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(4, '/api/v1/notification-integrations/integration-id', expect.objectContaining({
      method: 'DELETE',
    }))
  })

  it('queries the queue with safe filters and sends a deterministic test message', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ list: [], total: 0 }), {
        headers: { 'Content-Type': 'application/json' },
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'notification-id', status: 'PENDING' }), {
        status: 201,
        headers: { 'Content-Type': 'application/json' },
      }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await listExternalNotifications({
      from: 20,
      limit: 10,
      integrationId: 'integration-id',
      status: 'PENDING',
    })
    await testNotificationIntegration('integration-id', '测试播报')
    await deleteExternalNotification('notification-id')

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      '/api/v1/notification-integrations/notifications?from=20&limit=10&integrationId=integration-id&status=PENDING',
      expect.any(Object),
    )
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/notification-integrations/integration-id:test', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ content: '测试播报' }),
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/v1/notification-integrations/notifications/notification-id', expect.objectContaining({
      method: 'DELETE',
    }))
  })

  it('sends response actions only for an explicitly interactive test notification', async () => {
    const fetchMock = vi.fn()
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ id: 'notification-id' }), {
      status: 201,
      headers: { 'Content-Type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetchMock)

    await testNotificationIntegration('integration-id', '需要处理', ['ACKNOWLEDGE', 'COMPLETE'])

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/notification-integrations/integration-id:test', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ content: '需要处理', responseActions: ['ACKNOWLEDGE', 'COMPLETE'] }),
    }))
  })
})
