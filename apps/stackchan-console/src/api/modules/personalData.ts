import { apiJson, csrfHeaders, notifySessionExpired, responseError } from '../client'
import type { ConversationMessage } from './companion'

export interface PersonalDataConversation {
  createdAt: string
  deviceId: string | null
  deviceName: string | null
  id: string
  messageCount: number
  roleId: string
  roleName: string
  title: string
  updatedAt: string
}

export interface PersonalDataConversationPage {
  list: PersonalDataConversation[]
  total: number
}

export interface PersonalDataFilter {
  conversationId?: string
  deviceId?: string
  from: number
  fromTime?: string
  limit: number
  query?: string
  roleId?: string
  toTime?: string
}

export interface BackupStatus {
  available: boolean
  dailyBackupCount: number
  dailyRetention: number
  lastAttemptAt: string | null
  lastFailureAt: string | null
  lastFailureCode: string | null
  lastRestoreVerificationAt: string | null
  lastRestoreVerificationFailureCode: string | null
  lastRestoreVerificationSuccessful: boolean | null
  lastSuccessfulBackupAt: string | null
  storageBytes: number
  weeklyBackupCount: number
  weeklyRetention: number
}

function filterQuery(filter: PersonalDataFilter, includePagination = true) {
  const query = new URLSearchParams()
  if (includePagination) {
    query.set('from', String(filter.from))
    query.set('limit', String(filter.limit))
  }
  if (filter.query?.trim()) {
    query.set('query', filter.query.trim())
  }
  if (filter.deviceId) {
    query.set('deviceId', filter.deviceId)
  }
  if (filter.roleId) query.set('roleId', filter.roleId)
  if (filter.fromTime) {
    query.set('fromTime', filter.fromTime)
  }
  if (filter.toTime) {
    query.set('toTime', filter.toTime)
  }
  if (filter.conversationId) {
    query.set('conversationId', filter.conversationId)
  }
  return query
}

export function listPersonalDataConversations(filter: PersonalDataFilter): Promise<PersonalDataConversationPage> {
  return apiJson(`/api/v1/personal-data/conversations?${filterQuery(filter).toString()}`)
}

export function getPersonalDataMessages(conversationId: string): Promise<ConversationMessage[]> {
  return apiJson(`/api/v1/personal-data/conversations/${encodeURIComponent(conversationId)}/messages`)
}

export function deletePersonalDataMessage(conversationId: string, messageId: string): Promise<void> {
  return apiJson(
    `/api/v1/personal-data/conversations/${encodeURIComponent(conversationId)}/messages/${encodeURIComponent(messageId)}`,
    { method: 'DELETE' },
  )
}

export function deletePersonalDataConversation(conversationId: string): Promise<void> {
  return apiJson(`/api/v1/personal-data/conversations/${encodeURIComponent(conversationId)}`, { method: 'DELETE' })
}

export function getBackupStatus(): Promise<BackupStatus> {
  return apiJson('/api/v1/personal-data/backups/status')
}

export async function exportPersonalDataConversations(filter: PersonalDataFilter): Promise<{ blob: Blob, fileName: string }> {
  const response = await fetch(`/api/v1/personal-data/conversations:export?${filterQuery(filter, false).toString()}`, {
    credentials: 'same-origin',
    headers: {
      Accept: 'application/json',
      ...csrfHeaders(),
    },
  })
  if (response.status === 401) {
    notifySessionExpired()
  }
  if (!response.ok) {
    throw await responseError(response, '对话导出失败，请缩小筛选范围后重试。')
  }
  const disposition = response.headers.get('Content-Disposition') || ''
  const encodedName = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
  const plainName = disposition.match(/filename="?([^";]+)"?/i)?.[1]
  return {
    blob: await response.blob(),
    fileName: encodedName ? decodeURIComponent(encodedName) : plainName || 'stackchan-conversations.json',
  }
}
