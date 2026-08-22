import type { ExpressionState } from '@/api/modules/expressionPacks'

const aliases: Record<string, ExpressionState> = {
  error: 'error',
  failed: 'error',
  idle: 'idle',
  listening: 'listening',
  no_speech: 'no_speech',
  nospeech: 'no_speech',
  offline: 'offline',
  processing: 'processing',
  speaking: 'speaking',
  success: 'success',
  异常: 'error',
  待机: 'idle',
  成功: 'success',
  没听清: 'no_speech',
  离线: 'offline',
  聆听: 'listening',
  处理: 'processing',
  处理中: 'processing',
  说话: 'speaking',
  播报: 'speaking',
}

export function expressionStateFromFilename(filename: string): ExpressionState | undefined {
  const basename = filename.split(/[\\/]/).at(-1) ?? filename
  const normalized = basename
    .replace(/\.[^.]+$/, '')
    .trim()
    .toLocaleLowerCase()
    .replace(/[\s-]+/g, '_')
  return aliases[normalized]
}
