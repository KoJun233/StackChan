import { apiJson } from '../client'

export interface LlmSettings {
  apiKeyConfigured: boolean
  baseUrl: string
  model: string
  systemPrompt: string
  updatedAt: string | null
}

export interface SaveLlmSettingsInput {
  apiKey: string
  baseUrl: string
  model: string
  systemPrompt: string
}

export interface LlmConnectionTestResult {
  message: string
  ok: boolean
}

export interface SpeechSettings {
  apiKeyConfigured: boolean
  asrMode: SpeechAccessMode
  asrModel: string
  baseUrl: string
  providerType: SpeechProviderType
  speechSilenceThreshold: number
  speechStartThreshold: number
  ttsMode: SpeechAccessMode
  ttsModel: string
  ttsVoice: string
  updatedAt: string | null
  wakeSensitivity: VoiceWakeSensitivity
  workspaceId: string
}

export type SpeechProviderType = 'DASHSCOPE' | 'OPENAI_COMPATIBLE'
export type SpeechAccessMode = 'NON_REALTIME' | 'REALTIME'
export type VoiceWakeSensitivity = 'NORMAL' | 'SENSITIVE'

export interface SaveSpeechSettingsInput {
  apiKey: string
  asrMode: SpeechAccessMode
  asrModel: string
  baseUrl: string
  providerType: SpeechProviderType
  speechSilenceThreshold: number
  speechStartThreshold: number
  ttsMode: SpeechAccessMode
  ttsModel: string
  ttsVoice: string
  wakeSensitivity: VoiceWakeSensitivity
  workspaceId: string
}

export interface SpeechConnectionTestResult {
  message: string
  ok: boolean
}

export function getLlmSettings(): Promise<LlmSettings> {
  return apiJson('/api/v1/settings/llm')
}

export function saveLlmSettings(input: SaveLlmSettingsInput): Promise<LlmSettings> {
  return apiJson('/api/v1/settings/llm', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export function testLlmConnection(): Promise<LlmConnectionTestResult> {
  return apiJson('/api/v1/settings/llm/test', { method: 'POST' })
}

export function getSpeechSettings(): Promise<SpeechSettings> {
  return apiJson('/api/v1/settings/speech')
}

export function saveSpeechSettings(input: SaveSpeechSettingsInput): Promise<SpeechSettings> {
  return apiJson('/api/v1/settings/speech', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export function testSpeechConnection(): Promise<SpeechConnectionTestResult> {
  return apiJson('/api/v1/settings/speech/test', { method: 'POST' })
}
