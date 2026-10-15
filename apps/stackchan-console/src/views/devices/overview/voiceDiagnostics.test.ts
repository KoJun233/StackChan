import type { VoiceQualityMetric, VoiceTurn } from '@/api/modules/devices'
import { describe, expect, it } from 'vitest'
import { formatVoiceDuration, voiceCaptureLimitLabel, voiceDiagnosticCompleteness, voiceEndpointFeedbackLatency, voiceQualityPercentiles, voiceResponseLatency, voiceResponseLatencyExplanation, voiceStageTiming } from './voiceDiagnostics'

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
    diagnosticCode: source !== 'DEVICE' ? null : stage === 'PLAYBACK_STARTED' ? 'playback_codec_write' : stage === 'SPEECH_CAPTURED' ? 'capture_endpoint' : null,
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

  it('explains the missing device stage without substituting a server TTS time', () => {
    const missingPlayback = turn([
      event('SPEECH_CAPTURED', 2100),
      { ...event('TTS_COMPLETED', null, 'SERVER'), durationMs: 10 },
    ])
    expect(voiceResponseLatency(missingPlayback)).toBeNull()
    expect(voiceResponseLatencyExplanation(missingPlayback)).toBe('未知：缺少设备播放开始记录')
    expect(voiceResponseLatencyExplanation(turn([event('PLAYBACK_STARTED', 5100)]))).toBe('未知：缺少设备采音端点记录')
    expect(voiceResponseLatencyExplanation(turn([event('SPEECH_CAPTURED', 5100), event('PLAYBACK_STARTED', 2100)]))).toContain('顺序不一致')
  })

  it('does not allow contradictory duplicates to create a timing sample', () => {
    const events = [event('SPEECH_CAPTURED', 2100), event('PLAYBACK_STARTED', 5100)]
    expect(voiceResponseLatency(turn([...events, event('PLAYBACK_STARTED', 5100)]))).toBe(3000)
    expect(voiceResponseLatency(turn([...events, event('PLAYBACK_STARTED', 6100)]))).toBeNull()
    expect(voiceResponseLatency(turn([...events, event('PLAYBACK_STARTED', null)]))).toBeNull()
  })

  it('keeps old playback request events unknown without new actual-codec evidence', () => {
    const legacy = turn([event('SPEECH_CAPTURED', 2100), { ...event('PLAYBACK_STARTED', 5100), diagnosticCode: null }])
    expect(voiceResponseLatency(legacy)).toBeNull()
    expect(voiceResponseLatencyExplanation(legacy)).toContain('旧固件缺少设备实际播放开始记录')
    const legacyCapture = turn([{ ...event('SPEECH_CAPTURED', 2100), diagnosticCode: null }, event('PLAYBACK_STARTED', 5100)])
    expect(voiceResponseLatency(legacyCapture)).toBeNull()
    expect(voiceResponseLatencyExplanation(legacyCapture)).toContain('旧固件缺少实际采音端点记录')
  })

  it('keeps completed server status distinct from complete device and phase evidence', () => {
    expect(voiceDiagnosticCompleteness(turn([]))).toContain('未知')
    expect(voiceDiagnosticCompleteness(turn([event('PLAYBACK_STARTED', 5100)]))).toContain('部分')
    const complete = turn([
      event('SPEECH_CAPTURED', 2100),
      event('PLAYBACK_STARTED', 5100),
      event('PLAYBACK_COMPLETED', 7100),
      ...['ASR_COMPLETED', 'LLM_COMPLETED', 'TTS_COMPLETED'].map(stage => ({ ...event(stage, null, 'SERVER'), durationMs: 500 })),
    ])
    expect(voiceDiagnosticCompleteness(complete)).toContain('部分')
    expect(voiceDiagnosticCompleteness({ ...complete, diagnosticCompleteness: 'COMPLETE' })).toBe('完整阶段记录')
    expect(voiceDiagnosticCompleteness({ ...complete, status: 'CANCELLED' })).toContain('部分')
  })

  it('requires explicit same-device feedback telemetry and never fills old firmware fields', () => {
    const oldTurn = turn([event('SPEECH_CAPTURED', 2100)])
    expect(voiceEndpointFeedbackLatency(oldTurn)).toBeNull()
    expect(voiceCaptureLimitLabel(oldTurn)).toContain('未知')
    const capture = {
      ...event('SPEECH_CAPTURED', 2100),
      measurementSource: 'DEVICE_MONOTONIC' as const,
      endpointToLocalFeedbackMs: 12,
      captureLimitReached: true,
    }
    expect(voiceEndpointFeedbackLatency(turn([capture]))).toBe(12)
    expect(voiceCaptureLimitLabel(turn([capture]))).toContain('可能未说完')
    expect(voiceCaptureLimitLabel(turn([{ ...capture, captureLimitReached: false }]))).toBe('未达到采音上限')
    expect(voiceEndpointFeedbackLatency(turn([{ ...capture, measurementSource: 'SERVER_DURATION' }]))).toBeNull()
    expect(voiceEndpointFeedbackLatency(turn([capture, { ...capture, endpointToLocalFeedbackMs: 13 }]))).toBeNull()
    expect(voiceCaptureLimitLabel(turn([capture, { ...capture, captureLimitReached: false }]))).toContain('未知')
  })

  it('shows only sufficiently sampled device percentiles supplied by the full-range summary', () => {
    const metric: VoiceQualityMetric = {
      measurementSource: 'DEVICE_MONOTONIC',
      applicableSamples: 30,
      missingSamples: 2,
      minimumSamples: 30,
      p50Ms: 2000,
      p95Ms: 5000,
    }
    expect(voiceQualityPercentiles(metric)).toBe('p50 2.00 秒 · p95 5.00 秒')
    expect(voiceQualityPercentiles({ ...metric, applicableSamples: 29 })).toBe('样本不足，暂不能判断')
    expect(voiceQualityPercentiles({ ...metric, minimumSamples: 40 })).toBe('样本不足，暂不能判断')
    expect(voiceQualityPercentiles({ ...metric, measurementSource: 'SERVER_DURATION' })).toContain('未知')
    expect(voiceQualityPercentiles({ ...metric, measurementSource: 'PHYSICAL_OBSERVATION' })).toContain('未知')
    expect(voiceQualityPercentiles({ ...metric, p95Ms: null })).toContain('未知')
    expect(voiceQualityPercentiles({ ...metric, p95Ms: 1000 })).toContain('未知')
    expect(voiceQualityPercentiles({ ...metric, applicableSamples: Number.NaN })).toContain('样本不足')
  })
})
