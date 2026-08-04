import { apiJson } from '../client'

export type ReminderStatus = 'PENDING' | 'DISPATCHED' | 'DELIVERED' | 'FAILED' | 'CANCELLED' | 'SKIPPED'
export type ReminderRecurrence = 'DAILY' | 'NONE' | 'WEEKLY'
export type ReminderSource = 'PROACTIVE' | 'USER'
export type ProactiveGenerationStatus = 'FALLBACK' | 'FIXED' | 'GENERATED'

export interface Reminder {
  attemptCount: number
  content: string
  createdAt: string
  deviceId: string
  failureCode: string | null
  id: string
  lastCompletedAt: string | null
  lastOutcome: ReminderStatus | null
  recurrenceInterval: number
  recurrenceType: ReminderRecurrence
  scheduledAt: string
  status: ReminderStatus
  source: ReminderSource
  proactiveTopicKey: string | null
  proactiveGenerationStatus: ProactiveGenerationStatus | null
  updatedAt: string
  zoneId: string
}

export interface ReminderPage {
  list: Reminder[]
  total: number
}

export interface ReminderInput {
  content: string
  deviceId: string
  recurrenceInterval?: number
  recurrenceType?: ReminderRecurrence
  scheduledAt: string
  zoneId: string
}

export interface ReminderListParams {
  content?: string
  from: number
  limit: number
  status?: ReminderStatus | ''
}

export function listReminders(params: ReminderListParams): Promise<ReminderPage> {
  const query = new URLSearchParams({
    from: String(params.from),
    limit: String(params.limit),
  })
  if (params.content?.trim()) {
    query.set('content', params.content.trim())
  }
  if (params.status) {
    query.set('status', params.status)
  }
  return apiJson(`/api/v1/reminders?${query.toString()}`)
}

export function getReminder(id: string): Promise<Reminder> {
  return apiJson(`/api/v1/reminders/${encodeURIComponent(id)}`)
}

export function createReminder(input: ReminderInput): Promise<Reminder> {
  return apiJson('/api/v1/reminders', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export function updateReminder(id: string, input: ReminderInput): Promise<Reminder> {
  return apiJson(`/api/v1/reminders/${encodeURIComponent(id)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export function deleteReminder(id: string): Promise<void> {
  return apiJson(`/api/v1/reminders/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

export function snoozeReminder(id: string, minutes: number): Promise<Reminder> {
  return apiJson(`/api/v1/reminders/${encodeURIComponent(id)}:snooze`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ minutes }),
  })
}

export function skipNextReminder(id: string): Promise<Reminder> {
  return apiJson(`/api/v1/reminders/${encodeURIComponent(id)}:skip-next`, { method: 'POST' })
}

function parseLocalDateTime(value: string): [number, number, number, number, number] {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/.exec(value)
  if (!match) {
    throw new Error('提醒时间格式无效。')
  }
  return [Number(match[1]), Number(match[2]), Number(match[3]), Number(match[4]), Number(match[5])]
}

export function toReminderInstant(localDateTime: string, timezoneOffsetMinutes?: number): string {
  const [year, month, day, hour, minute] = parseLocalDateTime(localDateTime)
  const localDate = new Date(year, month - 1, day, hour, minute)
  if (Number.isNaN(localDate.getTime())) {
    throw new Error('提醒时间格式无效。')
  }
  const offset = timezoneOffsetMinutes ?? localDate.getTimezoneOffset()
  return new Date(Date.UTC(year, month - 1, day, hour, minute) + offset * 60_000).toISOString()
}

export function toLocalDateTimeValue(instant: string, timezoneOffsetMinutes?: number): string {
  const date = new Date(instant)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  const offset = timezoneOffsetMinutes ?? date.getTimezoneOffset()
  return new Date(date.getTime() - offset * 60_000).toISOString().slice(0, 16)
}

export function currentTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Shanghai'
}
