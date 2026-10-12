import type { VoiceQualityMetric, VoiceTurn, VoiceTurnEvent } from '@/api/modules/devices'

function boundedMilliseconds(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0 && value <= 300000
}

function deviceElapsed(turn: VoiceTurn, stage: string): number | null {
  const events = turn.events.filter(event => event.source === 'DEVICE' && event.stage === stage)
  const elapsed = events[0]?.elapsedMs
  return boundedMilliseconds(elapsed) && events.every(event => event.elapsedMs === elapsed) ? elapsed : null
}

export function voiceResponseLatency(turn: VoiceTurn): number | null {
  const captured = deviceElapsed(turn, 'SPEECH_CAPTURED')
  const playback = deviceElapsed(turn, 'PLAYBACK_STARTED')
  const actualPlayback = turn.events.filter(event => event.source === 'DEVICE' && event.stage === 'PLAYBACK_STARTED')
    .every(event => event.diagnosticCode === 'playback_codec_write')
  const actualCapture = turn.events.filter(event => event.source === 'DEVICE' && event.stage === 'SPEECH_CAPTURED')
    .every(event => event.diagnosticCode === 'capture_endpoint' || event.diagnosticCode === 'capture_limit')
  return actualCapture && actualPlayback && captured !== null && playback !== null && playback >= captured
    ? playback - captured
    : null
}

export function voiceResponseLatencyExplanation(turn: VoiceTurn): string {
  if (!turn.events.some(event => event.source === 'DEVICE' && event.stage === 'SPEECH_CAPTURED')) {
    return '未知：缺少设备采音端点记录'
  }
  if (!turn.events.some(event => event.source === 'DEVICE' && event.stage === 'SPEECH_CAPTURED' && (event.diagnosticCode === 'capture_endpoint' || event.diagnosticCode === 'capture_limit'))) {
    return '未知：旧固件缺少实际采音端点记录'
  }
  if (!turn.events.some(event => event.source === 'DEVICE' && event.stage === 'PLAYBACK_STARTED')) {
    return '未知：缺少设备播放开始记录'
  }
  if (!turn.events.some(event => event.source === 'DEVICE' && event.stage === 'PLAYBACK_STARTED' && event.diagnosticCode === 'playback_codec_write')) {
    return '未知：旧固件缺少设备实际播放开始记录'
  }
  return voiceResponseLatency(turn) === null ? '未知：设备计时缺失或顺序不一致' : '设备单调计时；不包含端点前的尾静音'
}

export function voiceDiagnosticCompleteness(turn: VoiceTurn): string {
  if (turn.diagnosticCompleteness === 'COMPLETE') {
    return '完整阶段记录'
  }
  if (turn.diagnosticCompleteness === 'UNKNOWN' || turn.events.length === 0) {
    return '未知：缺少可用阶段记录'
  }
  return '部分阶段记录；缺失值保持未知'
}

export function voiceEndpointFeedbackLatency(turn: VoiceTurn): number | null {
  const events = turn.events.filter(event => event.source === 'DEVICE' && event.stage === 'SPEECH_CAPTURED')
  const latency = events[0]?.endpointToLocalFeedbackMs
  return events.length > 0 && boundedMilliseconds(latency) && events.every(
    event => event.measurementSource === 'DEVICE_MONOTONIC' && event.endpointToLocalFeedbackMs === latency,
  )
    ? latency
    : null
}

export function voiceCaptureLimitLabel(turn: VoiceTurn): string {
  const events = turn.events.filter(event => event.source === 'DEVICE' && event.stage === 'SPEECH_CAPTURED')
  const reached = events[0]?.captureLimitReached
  if (typeof reached !== 'boolean' || !events.every(event => event.captureLimitReached === reached)) {
    return '未知：设备未记录采音上限'
  }
  return reached ? '已达到采音上限，输入可能未说完' : '未达到采音上限'
}

export function formatVoiceDuration(value: number | null | undefined): string {
  return boundedMilliseconds(value) ? `${(value / 1000).toFixed(value < 10000 ? 2 : 1)} 秒` : '未知'
}

export function voiceQualityPercentiles(metric: VoiceQualityMetric): string {
  if (metric.measurementSource !== 'DEVICE_MONOTONIC') {
    return '未知：缺少设备单调计时样本'
  }
  if (!Number.isInteger(metric.applicableSamples) || !Number.isInteger(metric.minimumSamples) || metric.applicableSamples < Math.max(30, metric.minimumSamples)) {
    return '样本不足，暂不能判断'
  }
  if (!boundedMilliseconds(metric.p50Ms) || !boundedMilliseconds(metric.p95Ms) || metric.p95Ms < metric.p50Ms) {
    return '未知：有效百分位尚未获取'
  }
  return `p50 ${formatVoiceDuration(metric.p50Ms)} · p95 ${formatVoiceDuration(metric.p95Ms)}`
}

export function voiceStageTiming(event: VoiceTurnEvent): string {
  if (event.source === 'SERVER') {
    return event.diagnosticCode === 'deterministic_action' ? '本地指令' : `本阶段 ${formatVoiceDuration(event.durationMs)}`
  }
  return boundedMilliseconds(event.elapsedMs) ? `+${formatVoiceDuration(event.elapsedMs)}` : '未知'
}

export function voiceStageSource(event: VoiceTurnEvent): string {
  return event.source === 'DEVICE' ? '设备单调计时' : '服务端阶段耗时'
}
