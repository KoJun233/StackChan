import { apiJson, csrfHeaders, notifySessionExpired } from '../client'

export type DelegationSchedule = 'ONCE' | 'DAILY' | 'WEEKLY' | 'INTERVAL'
export type DelegationDeliveryMode = 'CONSOLE' | 'BARK'
export type DelegationRunStatus = 'RUNNING' | 'SUCCEEDED' | 'NO_UPDATES' | 'FAILED' | 'BLOCKED' | 'CANCELLED'
export type DelegationDeliveryStatus = 'NONE' | 'PENDING' | 'SENDING' | 'SENT' | 'FAILED' | 'UNKNOWN' | 'SKIPPED'

export interface DelegationInput {
  deviceId: string
  roleId: string
  description: string
  zoneId?: string
  deliveryMode?: DelegationDeliveryMode
  scheduleType?: DelegationSchedule
  daysOfWeek?: number[]
  localTime?: string
  onceAt?: string | null
  intervalMinutes?: number | null
  enabled: boolean
}

export interface DelegationBriefItem {
  title: string
  summary: string
  impact: string
  sourceName: string
  url: string
  publishedAt: string | null
}

export interface DelegationRun {
  id: string
  taskId: string
  taskTitle: string
  scheduledDate: string
  scheduledAt: string | null
  occurrenceKey: string
  status: DelegationRunStatus
  output: string
  steps: { toolName: string, status: string, summary: string }[]
  evidence: { title: string, url: string | null, detail: string }[]
  items: DelegationBriefItem[]
  sourceWarnings: string[]
  errorCode: string | null
  deliveryStatus: DelegationDeliveryStatus
  deliveryAttempts: number
  createdAt: string
  completedAt: string | null
}

export interface Delegation extends DelegationInput {
  id: string
  title: string
  instruction: string
  understanding: string
  sourceUrls: string[]
  scheduleType: DelegationSchedule
  daysOfWeek: number[]
  localTime: string
  zoneId: string
  onceAt: string | null
  intervalMinutes: number | null
  deliveryMode: DelegationDeliveryMode
  requiresToolEvidence: boolean
  nextRunAt: string | null
  createdAt: string
  updatedAt: string
  lastRun: DelegationRun | null
}

export interface DelegationPage<T> {
  list: T[]
  total: number
}

export interface BarkSettings {
  serverUrl: string
  keyConfigured: boolean
  enabled: boolean
  updatedAt: string | null
}

export interface BarkSettingsInput {
  serverUrl: string
  deviceKey: string
  clearDeviceKey: boolean
  enabled: boolean
}

function jsonBody(method: 'POST' | 'PUT', input: unknown): RequestInit {
  return { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input) }
}

export class DelegationSubmissionError extends Error {
  constructor(public code: string, message: string, public clarification = '') {
    super(message)
    this.name = 'DelegationSubmissionError'
  }
}

async function submitDelegation(path: string, method: 'POST' | 'PUT', input: DelegationInput): Promise<Delegation> {
  const response = await fetch(path, {
    ...jsonBody(method, input),
    credentials: 'same-origin',
    headers: { 'Accept': 'application/json', ...csrfHeaders(), 'Content-Type': 'application/json' },
  })
  if (response.status === 401) {
    notifySessionExpired()
  }
  if (!response.ok) {
    const body = await response.json().catch(() => ({})) as { code?: string, message?: string, clarification?: string }
    throw new DelegationSubmissionError(
      typeof body.code === 'string' ? body.code : '',
      typeof body.message === 'string' && body.message.trim() ? body.message : '任务未能保存，请稍后重试。',
      typeof body.clarification === 'string' ? body.clarification : '',
    )
  }
  return response.json() as Promise<Delegation>
}

export function listDelegations(params: { deviceId?: string, roleId?: string, from: number, limit: number }): Promise<DelegationPage<Delegation>> {
  const query = new URLSearchParams({ from: String(params.from), limit: String(params.limit) })
  if (params.deviceId) {
    query.set('deviceId', params.deviceId)
  }
  if (params.roleId) {
    query.set('roleId', params.roleId)
  }
  return apiJson(`/api/v1/delegations?${query.toString()}`)
}

export function getDelegation(id: string): Promise<Delegation> {
  return apiJson(`/api/v1/delegations/${encodeURIComponent(id)}`)
}

export function createDelegation(input: DelegationInput): Promise<Delegation> {
  return submitDelegation('/api/v1/delegations', 'POST', input)
}

export function updateDelegation(id: string, input: DelegationInput): Promise<Delegation> {
  return submitDelegation(`/api/v1/delegations/${encodeURIComponent(id)}`, 'PUT', input)
}

export function deleteDelegation(id: string): Promise<void> {
  return apiJson(`/api/v1/delegations/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

export function pauseDelegation(id: string): Promise<Delegation> {
  return apiJson(`/api/v1/delegations/${encodeURIComponent(id)}:pause`, { method: 'POST' })
}

export function resumeDelegation(id: string): Promise<Delegation> {
  return apiJson(`/api/v1/delegations/${encodeURIComponent(id)}:resume`, { method: 'POST' })
}

export function runDelegation(id: string): Promise<DelegationRun> {
  return apiJson(`/api/v1/delegations/${encodeURIComponent(id)}:run`, { method: 'POST' })
}

export function listDelegationRuns(id: string, params: { from: number, limit: number }): Promise<DelegationPage<DelegationRun>> {
  const query = new URLSearchParams({ from: String(params.from), limit: String(params.limit) })
  return apiJson(`/api/v1/delegations/${encodeURIComponent(id)}/runs?${query.toString()}`)
}

export function retryDelegationDelivery(id: string): Promise<DelegationRun> {
  return apiJson(`/api/v1/delegation-runs/${encodeURIComponent(id)}:retry-delivery`, { method: 'POST' })
}

export function getBarkSettings(): Promise<BarkSettings> {
  return apiJson('/api/v1/delegation-push/settings')
}

export function updateBarkSettings(input: BarkSettingsInput): Promise<BarkSettings> {
  return apiJson('/api/v1/delegation-push/settings', jsonBody('PUT', input))
}
