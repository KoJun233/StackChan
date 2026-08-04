import { apiJson } from '../client'

export type PersonaTone = 'WARM' | 'CALM' | 'LIVELY' | 'PROFESSIONAL'
export type PersonaReplyLength = 'SHORT' | 'BALANCED' | 'DETAILED'
export type PersonaProactivity = 'RESERVED' | 'BALANCED' | 'PROACTIVE'

export interface PersonaSettings {
  displayName: string
  proactivity: PersonaProactivity
  replyLength: PersonaReplyLength
  taboos: string
  tone: PersonaTone
  topicBoundaries: string
  updatedAt: string | null
}

export interface PersonaInput {
  displayName: string
  proactivity: PersonaProactivity
  replyLength: PersonaReplyLength
  taboos: string
  tone: PersonaTone
  topicBoundaries: string
}

export type MemoryScopeType = 'GLOBAL' | 'DEVICE'
export type MemoryCategory = 'USER_PROFILE' | 'EVENT'
export type MemorySource = 'USER_ENTERED' | 'ASSISTANT_SUGGESTED'
export type MemoryConfirmationStatus = 'PENDING' | 'CONFIRMED' | 'REJECTED'

export interface LongTermMemory {
  category: MemoryCategory
  confirmationStatus: MemoryConfirmationStatus
  confirmedAt: string | null
  content: string
  createdAt: string
  deviceId: string | null
  enabled: boolean
  id: string
  importance: number
  lastUsedAt: string | null
  sourceTurnId: string | null
  replacesMemoryId: string | null
  supersededByMemoryId: string | null
  allowProactiveMention: boolean
  possibleDuplicateIds: string[]
  scopeType: MemoryScopeType
  source: MemorySource
  sourceDetail: string
  title: string
  topicKey: string
  updatedAt: string
}

export interface MemoryInput {
  category: MemoryCategory
  content: string
  deviceId: string | null
  scopeType: MemoryScopeType
  title: string
  topicKey: string
  importance: number
  allowProactiveMention: boolean
}

export interface MemoryPage {
  list: LongTermMemory[]
  total: number
}

export interface MemoryListParams {
  category?: MemoryCategory | ''
  confirmationStatus?: MemoryConfirmationStatus | ''
  deviceId?: string
  enabled?: boolean
  from: number
  limit: number
  query?: string
  scopeType?: MemoryScopeType | ''
}

export interface ClearMemoryInput {
  deviceId?: string | null
  scopeType?: MemoryScopeType | null
}

export function getPersona(): Promise<PersonaSettings> {
  return apiJson('/api/v1/persona')
}

export function savePersona(input: PersonaInput): Promise<PersonaSettings> {
  return apiJson('/api/v1/persona', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export function listMemories(params: MemoryListParams): Promise<MemoryPage> {
  const query = new URLSearchParams({
    from: String(params.from),
    limit: String(params.limit),
  })
  if (params.query?.trim()) {
    query.set('query', params.query.trim())
  }
  if (params.category) {
    query.set('category', params.category)
  }
  if (params.confirmationStatus) {
    query.set('confirmationStatus', params.confirmationStatus)
  }
  if (params.enabled !== undefined) {
    query.set('enabled', String(params.enabled))
  }
  if (params.scopeType) {
    query.set('scopeType', params.scopeType)
  }
  if (params.deviceId) {
    query.set('deviceId', params.deviceId)
  }
  return apiJson(`/api/v1/memories?${query.toString()}`)
}

export function getMemory(id: string): Promise<LongTermMemory> {
  return apiJson(`/api/v1/memories/${encodeURIComponent(id)}`)
}

export function createMemory(input: MemoryInput): Promise<LongTermMemory> {
  return apiJson('/api/v1/memories', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export function updateMemory(id: string, input: MemoryInput): Promise<LongTermMemory> {
  return apiJson(`/api/v1/memories/${encodeURIComponent(id)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export function confirmMemory(id: string): Promise<LongTermMemory> {
  return apiJson(`/api/v1/memories/${encodeURIComponent(id)}:confirm`, { method: 'POST' })
}

export function rejectMemory(id: string): Promise<LongTermMemory> {
  return apiJson(`/api/v1/memories/${encodeURIComponent(id)}:reject`, { method: 'POST' })
}

export function setMemoryEnabled(id: string, enabled: boolean): Promise<LongTermMemory> {
  return apiJson(`/api/v1/memories/${encodeURIComponent(id)}/enabled`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ enabled }),
  })
}

export function deleteMemory(id: string): Promise<void> {
  return apiJson(`/api/v1/memories/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

export function clearMemories(input: ClearMemoryInput = {}): Promise<{ deletedCount: number }> {
  return apiJson('/api/v1/memories:clear', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export interface MemoryUsageReference {
  memoryId: string
  title: string
  topicKey: string
  scopeType: MemoryScopeType
  source: MemorySource
  sourceDetail: string
}

export function getMemoryUsage(turnId: string): Promise<{ turnId: string, memories: MemoryUsageReference[] }> {
  return apiJson(`/api/v1/memories/usage/${encodeURIComponent(turnId)}`)
}
