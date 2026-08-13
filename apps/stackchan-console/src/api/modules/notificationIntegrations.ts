import { apiJson } from '../client'

export type ExternalNotificationStatus = 'PENDING' | 'DISPATCHED' | 'DELIVERED' | 'FAILED' | 'EXPIRED' | 'CANCELLED'
export type NotificationResponseAction = 'ACKNOWLEDGE' | 'SNOOZE' | 'COMPLETE'

export interface NotificationResponse {
  action: NotificationResponseAction
  respondedAt: string
  snoozeMinutes: number | null
}

export interface NotificationTokenMetadata {
  createdAt: string
  expiresAt: string | null
  id: string
  lastUsedAt: string | null
  revokedAt: string | null
}

export interface NotificationIntegration {
  createdAt: string
  deviceId: string
  digestWindowSeconds: number
  enabled: boolean
  id: string
  name: string
  roleId: string
  tokens: NotificationTokenMetadata[]
  updatedAt: string
}

export interface NotificationIntegrationInput {
  deviceId: string
  digestWindowSeconds: number
  enabled: boolean
  name: string
  roleId?: string
}

export interface IssuedNotificationToken {
  metadata: NotificationTokenMetadata
  token: string
}

export interface ExternalNotification {
  attemptCount: number
  content: string
  createdAt: string
  deliveredAt: string | null
  deviceId: string
  expiresAt: string
  failureCode: string | null
  id: string
  integrationId: string
  roleId: string
  response: NotificationResponse | null
  responseActions: NotificationResponseAction[]
  status: ExternalNotificationStatus
  updatedAt: string
}

export interface ExternalNotificationPage {
  list: ExternalNotification[]
  total: number
}

export function listNotificationIntegrations(): Promise<NotificationIntegration[]> {
  return apiJson('/api/v1/notification-integrations')
}

export function getNotificationIntegration(id: string): Promise<NotificationIntegration> {
  return apiJson(`/api/v1/notification-integrations/${encodeURIComponent(id)}`)
}

export function createNotificationIntegration(input: NotificationIntegrationInput): Promise<NotificationIntegration> {
  return apiJson('/api/v1/notification-integrations', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export function updateNotificationIntegration(id: string, input: NotificationIntegrationInput): Promise<NotificationIntegration> {
  return apiJson(`/api/v1/notification-integrations/${encodeURIComponent(id)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export function deleteNotificationIntegration(id: string): Promise<void> {
  return apiJson(`/api/v1/notification-integrations/${encodeURIComponent(id)}`, {
    method: 'DELETE',
  })
}

export function issueNotificationToken(id: string, expiresAt: string | null): Promise<IssuedNotificationToken> {
  return apiJson(`/api/v1/notification-integrations/${encodeURIComponent(id)}/tokens`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ expiresAt }),
  })
}

export function revokeNotificationToken(integrationId: string, tokenId: string): Promise<void> {
  return apiJson(`/api/v1/notification-integrations/${encodeURIComponent(integrationId)}/tokens/${encodeURIComponent(tokenId)}`, {
    method: 'DELETE',
  })
}

export function listExternalNotifications(params: {
  from: number
  integrationId?: string
  limit: number
  status?: ExternalNotificationStatus | ''
}): Promise<ExternalNotificationPage> {
  const query = new URLSearchParams({ from: String(params.from), limit: String(params.limit) })
  if (params.integrationId) query.set('integrationId', params.integrationId)
  if (params.status) query.set('status', params.status)
  return apiJson(`/api/v1/notification-integrations/notifications?${query.toString()}`)
}

export function deleteExternalNotification(id: string): Promise<void> {
  return apiJson(`/api/v1/notification-integrations/notifications/${encodeURIComponent(id)}`, {
    method: 'DELETE',
  })
}

export function respondExternalNotification(
  id: string,
  action: NotificationResponseAction,
  snoozeMinutes?: number,
): Promise<NotificationResponse> {
  return apiJson(`/api/v1/notification-integrations/notifications/${encodeURIComponent(id)}:respond`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ action, snoozeMinutes }),
  })
}

export function testNotificationIntegration(
  integrationId: string,
  content: string,
  responseActions: NotificationResponseAction[] = [],
): Promise<ExternalNotification> {
  const body = responseActions.length ? { content, responseActions } : { content }
  return apiJson(`/api/v1/notification-integrations/${encodeURIComponent(integrationId)}:test`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}
