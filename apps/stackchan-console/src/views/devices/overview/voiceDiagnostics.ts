import type { VoiceTurn, VoiceTurnEvent } from '@/api/modules/devices'

function boundedMilliseconds(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0 && value <= 300000
}

export function voiceResponseLatency(turn: VoiceTurn): number | null {
  const captured = turn.events.find(event => event.source === 'DEVICE' && event.stage === 'SPEECH_CAPTURED')?.elapsedMs
  const playback = turn.events.find(event => event.source === 'DEVICE' && event.stage === 'PLAYBACK_STARTED')?.elapsedMs
  return boundedMilliseconds(captured) && boundedMilliseconds(playback) && playback >= captured
    ? playback - captured
    : null
}

export function formatVoiceDuration(value: number | null | undefined): string {
  return boundedMilliseconds(value) ? `${(value / 1000).toFixed(value < 10000 ? 2 : 1)} 秒` : '未知'
}

export function voiceStageTiming(event: VoiceTurnEvent): string {
  if (event.source === 'SERVER') {
    return event.diagnosticCode === 'deterministic_action' ? '本地指令' : `本阶段 ${formatVoiceDuration(event.durationMs)}`
  }
  return boundedMilliseconds(event.elapsedMs) ? `+${formatVoiceDuration(event.elapsedMs)}` : '未知'
}
