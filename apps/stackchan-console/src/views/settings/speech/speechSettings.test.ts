import { describe, expect, it } from 'vitest'
import {
  createSpeechSettingsSchema,
  isValidDashScopeWorkspaceId,
  isValidSpeechBaseUrl,
} from './speechSettings'

describe('speech provider form rules', () => {
  it('rejects provider host injection while preserving HTTP compatible endpoints', () => {
    expect(isValidDashScopeWorkspaceId('llm-workspace123')).toBe(true)
    expect(isValidDashScopeWorkspaceId('workspace.example.com')).toBe(false)
    expect(isValidDashScopeWorkspaceId('-workspace')).toBe(false)
    expect(isValidSpeechBaseUrl('https://speech.example.com/v1')).toBe(true)
    expect(isValidSpeechBaseUrl('wss://speech.example.com')).toBe(false)
  })

  it('does not reject an unmounted field from the inactive provider', () => {
    const schema = createSpeechSettingsSchema(() => true)

    expect(schema.safeParse({
      apiKey: '',
      asrMode: 'NON_REALTIME',
      asrModel: 'future-provider-asr-model',
      providerType: 'DASHSCOPE',
      speechSilenceThreshold: 200,
      speechStartThreshold: 350,
      ttsMode: 'REALTIME',
      ttsModel: 'future-provider-tts-model',
      ttsVoice: 'longanhuan_v3.6',
      wakeSensitivity: 'SENSITIVE',
      workspaceId: 'llm-example-workspace',
    }).success).toBe(true)
  })

  it('requires the silence threshold to remain below the speech start threshold', () => {
    const schema = createSpeechSettingsSchema(() => true)

    const result = schema.safeParse({
      apiKey: '',
      asrMode: 'NON_REALTIME',
      asrModel: 'asr',
      baseUrl: 'https://speech.example.com/v1',
      providerType: 'OPENAI_COMPATIBLE',
      speechSilenceThreshold: 350,
      speechStartThreshold: 350,
      ttsMode: 'NON_REALTIME',
      ttsModel: 'tts',
      ttsVoice: 'voice',
      wakeSensitivity: 'NORMAL',
      workspaceId: '',
    })

    expect(result.success).toBe(false)
  })
})
