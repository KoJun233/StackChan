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
  proactiveMinIntervalMinutes: number
  proactivePersonalizationEnabled: boolean
  proactiveStart: string
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
  'deviceId' | 'proactiveCounter' | 'proactiveCounterDate' | 'proactiveLastAt' | 'updatedAt'
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

export function listProactiveTopics(deviceId: string): Promise<ProactiveTopicCooldown[]> {
  return apiJson(`/api/v1/settings/interactions/${encodeURIComponent(deviceId)}/proactive-topics`)
}

export function resumeProactiveTopic(deviceId: string, topicKey: string): Promise<ProactiveTopicCooldown> {
  return apiJson(`/api/v1/settings/interactions/${encodeURIComponent(deviceId)}/proactive-topics:resume`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ topicKey }),
  })
}
