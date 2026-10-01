import type { VoiceTurn } from '@/api/modules/devices'
import { describe, expect, it } from 'vitest'
import { formatVoiceDuration, voiceResponseLatency, voiceStageTiming } from './voiceDiagnostics'

describe('voice phase timing', () => {
  const turn = (events: VoiceTurn['events']): VoiceTurn => ({
    turnId: 'turn',
    status: 'COMPLETED',
    failureCode: null,
    startedAt: '2026-10-01T00:00:00Z',
    updatedAt: '2026-10-01T00:00:10Z',
    events,
  })
  const event = (stage: string, elapsedMs: number | null, source: 'DEVICE' | 'SERVER' = 'DEVICE') => ({
    stage,
    elapsedMs,
    source,
    failureCode: null,
    occurredAt: '2026-10-01T00:00:10Z',
  })
  it('uses only elapsed values from the same device clock', () => {
    expect(voiceResponseLatency(turn([event('SPEECH_CAPTURED', 2100), event('PLAYBACK_STARTED', 5100)]))).toBe(3000)
    expect(voiceResponseLatency(turn([event('SPEECH_CAPTURED', 2100), event('PLAYBACK_STARTED', 5100, 'SERVER')]))).toBeNull()
    expect(voiceResponseLatency(turn([event('SPEECH_CAPTURED', null), event('PLAYBACK_STARTED', 5100)]))).toBeNull()
    expect(voiceResponseLatency(turn([event('SPEECH_CAPTURED', 5100), event('PLAYBACK_STARTED', 2100)]))).toBeNull()
  })
  it('does not invent durations from reception timestamps or missing phases', () => {
    expect(voiceStageTiming(event('ASR_COMPLETED', null, 'SERVER'))).toBe('本阶段 未知')
    expect(voiceStageTiming({ ...event('ASR_COMPLETED', null, 'SERVER'), durationMs: 1250 })).toBe('本阶段 1.25 秒')
    expect(voiceStageTiming({ ...event('LLM_COMPLETED', null, 'SERVER'), diagnosticCode: 'deterministic_action' })).toBe('本地指令')
    expect(formatVoiceDuration(Number.NaN)).toBe('未知')
    expect(formatVoiceDuration(-1)).toBe('未知')
    expect(formatVoiceDuration(300001)).toBe('未知')
    expect(voiceResponseLatency(turn([]))).toBeNull()
  })
})
