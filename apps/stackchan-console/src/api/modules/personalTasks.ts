import { apiJson } from '../client'

export type PersonalTaskPriority = 'HIGH' | 'LOW' | 'NORMAL'
export type PersonalTaskStatus = 'COMPLETED' | 'OPEN'

export interface PersonalTask {
  completedAt: string | null
  createdAt: string
  deviceId: string
  dueAt: string | null
  id: string
  notes: string | null
  priority: PersonalTaskPriority
  reminderId: string | null
  roleId: string
  status: PersonalTaskStatus
  title: string
  updatedAt: string
  zoneId: string
}

export interface PersonalTaskPage {
  list: PersonalTask[]
  total: number
}

export interface PersonalTaskInput {
  deviceId: string
  dueAt: string | null
  notes?: string | null
  priority: PersonalTaskPriority
  roleId?: string
  title: string
  zoneId: string
}

export interface PersonalTaskListParams {
  from: number
  limit: number
  priority?: PersonalTaskPriority | ''
  query?: string
  roleId?: string
  status?: PersonalTaskStatus | ''
}

export function listPersonalTasks(params: PersonalTaskListParams): Promise<PersonalTaskPage> {
  const query = new URLSearchParams({ from: String(params.from), limit: String(params.limit) })
  if (params.query?.trim()) {
    query.set('query', params.query.trim())
  }
  if (params.status) {
    query.set('status', params.status)
  }
  if (params.priority) {
    query.set('priority', params.priority)
  }
  if (params.roleId) {
    query.set('roleId', params.roleId)
  }
  return apiJson(`/api/v1/personal-tasks?${query.toString()}`)
}

export function getPersonalTask(id: string): Promise<PersonalTask> {
  return apiJson(`/api/v1/personal-tasks/${encodeURIComponent(id)}`)
}

export function createPersonalTask(input: PersonalTaskInput): Promise<PersonalTask> {
  return apiJson('/api/v1/personal-tasks', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export function updatePersonalTask(id: string, input: PersonalTaskInput): Promise<PersonalTask> {
  return apiJson(`/api/v1/personal-tasks/${encodeURIComponent(id)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export function completePersonalTask(id: string): Promise<PersonalTask> {
  return apiJson(`/api/v1/personal-tasks/${encodeURIComponent(id)}:complete`, { method: 'POST' })
}

export function reopenPersonalTask(id: string): Promise<PersonalTask> {
  return apiJson(`/api/v1/personal-tasks/${encodeURIComponent(id)}:reopen`, { method: 'POST' })
}

export function deletePersonalTask(id: string): Promise<void> {
  return apiJson(`/api/v1/personal-tasks/${encodeURIComponent(id)}`, { method: 'DELETE' })
}
