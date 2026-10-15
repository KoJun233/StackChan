import { apiJson, csrfHeaders, notifySessionExpired } from '../client'

export type DelegationSchedule = 'ONCE' | 'DAILY' | 'WEEKLY' | 'INTERVAL'
export type DelegationDeliveryMode = 'CONSOLE' | 'BARK'
export type DelegationRunStatus = 'RUNNING' | 'SUCCEEDED' | 'NO_UPDATES' | 'FAILED' | 'BLOCKED' | 'CANCELLED'
export type DelegationDeliveryStatus = 'NONE' | 'PENDING' | 'SENDING' | 'SENT' | 'FAILED' | 'UNKNOWN' | 'SKIPPED'
export type DelegationReviewState = 'UNREVIEWED' | 'ACCEPTED' | 'REVISION_REQUESTED' | 'SUPERSEDED'
export type DelegationFeedbackKind = 'ACCEPT_RESULT' | 'REVISE_PREFERENCES' | 'PAUSE' | 'CANCEL'
export type DelegationOperationAction = 'DELEGATION_CREATE' | 'DELEGATION_FEEDBACK' | 'DELEGATION_RUN_REVISION'
export interface DelegationScope { deviceId: string, roleId: string }
export interface DelegationReview {
  reviewState: DelegationReviewState
  resourceVersion: number
  supersedesRunId: string | null
  acknowledgedAt: string | null
}
export interface DelegationFeedbackInput {
  kind: DelegationFeedbackKind
  comment: string
  expectedRevision: number
  resourceVersion: number
  runMode?: 'SAVE_ONLY' | 'SAVE_AND_RUN'
}
export interface DelegationFeedbackResponse {
  feedbackId: string
  taskId: string
  runId: string
  revision: number
  resourceVersion: number
  reviewState: DelegationReviewState
  newRunId: string | null
}
export interface DelegationProjectSummary {
  task: Delegation
  latestRun: DelegationRun | null
  review: DelegationReview | null
  summary: string
  scope: string[]
  sourceCount: number
  missingInformation: string
  nextDecision: string
  feedbackHistory: { id: string, kind: DelegationFeedbackKind, comment: string, createdAt: string, runId: string }[]
}
export interface DelegationOperation {
  key: string
  action: DelegationOperationAction
  status: 'PENDING' | 'COMMITTED' | 'FAILED' | 'NOT_FOUND'
  resourceIds: { taskId?: string, runId?: string, feedbackId?: string }
  revision: number | null
  errorCode: string | null
  createdAt: string | null
  updatedAt: string | null
}
export interface RecoveredProjectOperation { recovered: true, project: DelegationProjectSummary }

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
  evidence: { title: string, url: string | null, detail: string, inputUrl?: string | null, readAt?: string | null }[]
  items: DelegationBriefItem[]
  sourceWarnings: string[]
  errorCode: string | null
  deliveryStatus: DelegationDeliveryStatus
  deliveryAttempts: number
  createdAt: string
  completedAt: string | null
  taskRevision?: number
  attempt?: number
  review?: DelegationReview | null
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
  revision?: number
  cancelledAt?: string | null
  cancellationReason?: string | null
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
  constructor(public code: string, message: string, public clarification = '', public status = 0) {
    super(message)
    this.name = 'DelegationSubmissionError'
  }
}

export class DelegationOperationPendingError extends DelegationSubmissionError {
  constructor(public key: string) {
    super('OPERATION_PENDING', '保存状态暂未确认，请保留当前内容并核对状态，避免重复提交。')
  }
}

export function newDelegationOperationKey(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6] & 0x0F) | 0x40
  bytes[8] = (bytes[8] & 0x3F) | 0x80
  const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

async function projectRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(path, { ...init, credentials: 'same-origin', headers: { Accept: 'application/json', ...csrfHeaders(), ...init.headers } })
  if (response.status === 401) {
    notifySessionExpired()
  }
  if (!response.ok) {
    const body = await response.json().catch(() => ({})) as { code?: string, message?: string, clarification?: string }
    throw new DelegationSubmissionError(body.code || '', body.message || '请求未能完成，请稍后重试。', body.clarification || '', response.status)
  }
  return response.json() as Promise<T>
}

export function getDelegationProjectSummary(taskId: string): Promise<DelegationProjectSummary> {
  return projectRequest(`/api/v1/delegations/${encodeURIComponent(taskId)}/project-summary`)
}

export function getDelegationOperation(key: string, action: DelegationOperationAction, scope: DelegationScope, resourceId?: string): Promise<DelegationOperation> {
  const query = new URLSearchParams({ action, deviceId: scope.deviceId, roleId: scope.roleId })
  if (resourceId) {
    query.set('resourceId', resourceId)
  }
  return projectRequest(`/api/v1/companion/operations/${encodeURIComponent(key)}?${query}`)
}

const uncertainOperations = new Set<string>()
async function projectMutation<T>(path: string, input: unknown, key: string, action: DelegationOperationAction, scope: DelegationScope, resourceId: string | undefined, recover: (receipt: DelegationOperation) => Promise<T>): Promise<T> {
  const identity = `${scope.deviceId}/${scope.roleId}/${action}/${key}`
  const check = async (): Promise<T | undefined> => {
    let receipt: DelegationOperation
    try {
      receipt = await getDelegationOperation(key, action, scope, resourceId)
    }
    catch {
      throw new DelegationOperationPendingError(key)
    }
    if (receipt.status === 'COMMITTED') {
      try {
        const result = await recover(receipt)
        uncertainOperations.delete(identity)
        return result
      }
      catch {
        throw new DelegationOperationPendingError(key)
      }
    }
    if (receipt.status === 'FAILED') {
      uncertainOperations.delete(identity)
      throw new DelegationSubmissionError(receipt.errorCode || 'DEPENDENCY_UNAVAILABLE', '这次操作未完成，请查看当前成果后重新决定。')
    }
    if (receipt.status === 'PENDING') {
      throw new DelegationOperationPendingError(key)
    }
    return undefined
  }
  if (uncertainOperations.has(identity)) {
    const recovered = await check()
    if (recovered !== undefined) {
      return recovered
    }
  }
  try {
    const result = await projectRequest<T>(path, { ...jsonBody('POST', input), headers: { 'Content-Type': 'application/json', 'Idempotency-Key': key } })
    uncertainOperations.delete(identity)
    return result
  }
  catch (error) {
    if (error instanceof DelegationSubmissionError && error.status > 0 && error.status < 500 && error.code !== 'OPERATION_PENDING') {
      uncertainOperations.delete(identity)
      throw error
    }
    uncertainOperations.add(identity)
    const recovered = await check()
    if (recovered !== undefined) {
      return recovered
    }
    throw new DelegationOperationPendingError(key)
  }
}

export function submitDelegationFeedback(taskId: string, runId: string, input: DelegationFeedbackInput, key: string, scope: DelegationScope): Promise<DelegationFeedbackResponse | RecoveredProjectOperation> {
  return projectMutation<DelegationFeedbackResponse | RecoveredProjectOperation>(`/api/v1/delegation-runs/${encodeURIComponent(runId)}/feedback`, input, key, 'DELEGATION_FEEDBACK', scope, runId, async () => ({ recovered: true, project: await getDelegationProjectSummary(taskId) }))
}

export function runDelegationRevision(taskId: string, expectedRevision: number, key: string, scope: DelegationScope): Promise<DelegationRun | RecoveredProjectOperation> {
  return projectMutation<DelegationRun | RecoveredProjectOperation>(`/api/v1/delegations/${encodeURIComponent(taskId)}:run-revision`, { expectedRevision }, key, 'DELEGATION_RUN_REVISION', scope, taskId, async () => ({ recovered: true, project: await getDelegationProjectSummary(taskId) }))
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

export function listDelegationReviewReady(params: { deviceId?: string, roleId?: string }): Promise<DelegationPage<Delegation>> {
  const query = new URLSearchParams({ limit: '3' })
  if (params.deviceId) {
    query.set('deviceId', params.deviceId)
  }
  if (params.roleId) {
    query.set('roleId', params.roleId)
  }
  return apiJson(`/api/v1/delegations/review-ready?${query}`)
}

export function getDelegation(id: string): Promise<Delegation> {
  return apiJson(`/api/v1/delegations/${encodeURIComponent(id)}`)
}

export function createDelegation(input: DelegationInput, key = newDelegationOperationKey()): Promise<Delegation> {
  return projectMutation('/api/v1/delegations', input, key, 'DELEGATION_CREATE', input, undefined, async (receipt) => {
    if (!receipt.resourceIds.taskId) {
      throw new DelegationOperationPendingError(key)
    }
    return getDelegation(receipt.resourceIds.taskId)
  })
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
