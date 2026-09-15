import { apiJson } from '../client'

export type MissedReminderPolicy = 'PLAY_NOW' | 'SKIP' | 'SNOOZE'

export interface InteractionSettings {
  continuousConversationEnabled: boolean
  deviceId: string
  dndEnabled: boolean
  dndEnd: string
  dndStart: string
  followUpWindowSeconds: number
  missedReminderPolicy: MissedReminderPolicy
  missedSnoozeMinutes: number
  nightMode: boolean
  proactiveContent: string
  proactiveCounter: number
  proactiveCounterDate: string | null
  proactiveDailyLimit: number
  proactiveEnabled: boolean
  proactiveEnd: string
  proactiveLastAt: string | null
  proactiveNextAt: string | null
  proactiveMinIntervalMinutes: number
  proactivePersonalizationEnabled: boolean
  proactiveStart: string
  silentPresenceCounter: number
  silentPresenceCounterDate: string | null
  silentPresenceEnabled: boolean
  silentPresenceLastAt: string | null
  silentPresenceNextAt: string | null
  updatedAt: string | null
  volumePercent: number
  zoneId: string
}

export interface ProactiveTopicCooldown {
  cooldownUntil: string
  lastMentionedAt: string
  topicKey: string
  userMuted: boolean
}

export type SaveInteractionSettingsInput = Omit<
  InteractionSettings,
  'deviceId' | 'proactiveCounter' | 'proactiveCounterDate' | 'proactiveLastAt' | 'proactiveNextAt'
  | 'silentPresenceCounter' | 'silentPresenceCounterDate' | 'silentPresenceLastAt' | 'silentPresenceNextAt' | 'updatedAt'
>

export function getInteractionSettings(deviceId: string): Promise<InteractionSettings> {
  return apiJson(`/api/v1/settings/interactions/${encodeURIComponent(deviceId)}`)
}

export function saveInteractionSettings(
  deviceId: string,
  input: SaveInteractionSettingsInput,
): Promise<InteractionSettings> {
  return apiJson(`/api/v1/settings/interactions/${encodeURIComponent(deviceId)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export function stopDeviceAudio(deviceId: string): Promise<{ accepted: boolean }> {
  return apiJson(`/api/v1/settings/interactions/${encodeURIComponent(deviceId)}:stop`, { method: 'POST' })
}

export function listProactiveTopics(deviceId: string, roleId?: string): Promise<ProactiveTopicCooldown[]> {
  const scope = roleId ? `?roleId=${encodeURIComponent(roleId)}` : ''
  return apiJson(`/api/v1/settings/interactions/${encodeURIComponent(deviceId)}/proactive-topics${scope}`)
}

export interface ProactivePause {
  deviceId: string
  roleId: string
  paused: boolean
  pausedUntil: string | null
}

function proactivePausePath(deviceId: string, roleId: string) {
  return `/api/v1/settings/interactions/${encodeURIComponent(deviceId)}/roles/${encodeURIComponent(roleId)}/proactive-pause`
}

export function getProactivePause(deviceId: string, roleId: string): Promise<ProactivePause> {
  return apiJson(proactivePausePath(deviceId, roleId))
}

export function pauseProactive(deviceId: string, roleId: string, minutes: number | null): Promise<ProactivePause> {
  return apiJson(proactivePausePath(deviceId, roleId), {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ minutes }),
  })
}

export function resumeProactive(deviceId: string, roleId: string): Promise<ProactivePause> {
  return apiJson(proactivePausePath(deviceId, roleId), { method: 'DELETE' })
}

export function resumeProactiveTopic(deviceId: string, topicKey: string, roleId?: string): Promise<ProactiveTopicCooldown> {
  const scope = roleId ? `?roleId=${encodeURIComponent(roleId)}` : ''
  return apiJson(`/api/v1/settings/interactions/${encodeURIComponent(deviceId)}/proactive-topics:resume${scope}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ topicKey }),
  })
}
