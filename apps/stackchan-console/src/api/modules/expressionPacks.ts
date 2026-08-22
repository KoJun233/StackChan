import { apiJson, csrfHeaders, notifySessionExpired, responseError } from '../client'

export type ExpressionState = 'idle' | 'listening' | 'processing' | 'speaking' | 'success' | 'no_speech' | 'offline' | 'error'
export type LifecycleClip = 'boot_appear' | 'wake' | 'role_switch'

export interface ExpressionPackClip {
  frameCount: number
  frameDelayMs: number
  name: LifecycleClip
  sha256: string
  size: number
}

export interface ExpressionPack {
  artifactSha256: string
  artifactSize: number
  createdAt: string
  description: string | null
  formatVersion: number
  id: string
  name: string
  packType: 'STATIC_PNG' | 'LIFECYCLE_EAF'
  clips: ExpressionPackClip[]
  states: ExpressionState[]
}

export interface CreateLifecycleExpressionPackInput {
  clips: Partial<Record<LifecycleClip, { file: File, frameDelayMs: number }>>
  description: string
  name: string
}

export interface DeviceExpressionPack {
  deviceId: string
  enabled: boolean
  failureCode: string | null
  installedAt: string | null
  packId: string | null
  status: 'READY' | 'INSTALLING' | 'ACTIVE' | 'FAILED' | 'DISABLED'
  updatedAt: string
}

export interface CreateExpressionPackInput {
  description: string
  images: Record<ExpressionState, File>
  name: string
}

export type ExpressionFrameRateMode = 'FIXED' | 'ADAPTIVE'
export type ExpressionPreviewCategory = 'EMOTION' | 'SYSTEM' | 'BEHAVIOR'

export interface ExpressionFrameRateSettings {
  applied: boolean
  maxFps: number
  minFps: number
  mode: ExpressionFrameRateMode
}

export interface ExpressionPreviewInput {
  category: ExpressionPreviewCategory
  durationSeconds: number
  value: string
}

export const expressionStates: { label: string, value: ExpressionState }[] = [
  { label: '待机', value: 'idle' },
  { label: '聆听', value: 'listening' },
  { label: '处理中', value: 'processing' },
  { label: '播报', value: 'speaking' },
  { label: '成功', value: 'success' },
  { label: '没听清', value: 'no_speech' },
  { label: '离线', value: 'offline' },
  { label: '异常', value: 'error' },
]

export async function listExpressionPacks(): Promise<ExpressionPack[]> {
  return (await apiJson<{ packs: ExpressionPack[] }>('/api/v1/expression-packs')).packs
}

export async function createExpressionPack(input: CreateExpressionPackInput): Promise<ExpressionPack> {
  const form = new FormData()
  form.append('name', input.name)
  form.append('description', input.description)
  expressionStates.forEach(({ value }) => form.append(value, input.images[value]))
  const response = await fetch('/api/v1/expression-packs', {
    method: 'POST',
    credentials: 'same-origin',
    headers: { Accept: 'application/json', ...csrfHeaders() },
    body: form,
  })
  if (response.status === 401) {
    notifySessionExpired()
  }
  if (!response.ok) {
    throw await responseError(response, '资源包生成失败。')
  }
  return response.json() as Promise<ExpressionPack>
}

export async function createLifecycleExpressionPack(input: CreateLifecycleExpressionPackInput): Promise<ExpressionPack> {
  const form = new FormData()
  form.append('name', input.name)
  form.append('description', input.description)
  lifecycleClips.forEach(({ value }) => {
    const clip = input.clips[value]
    if (!clip) return
    form.append(value, clip.file)
    form.append(`${value}_frame_delay_ms`, String(clip.frameDelayMs))
  })
  const response = await fetch('/api/v1/expression-packs/lifecycle', {
    method: 'POST',
    credentials: 'same-origin',
    headers: { Accept: 'application/json', ...csrfHeaders() },
    body: form,
  })
  if (response.status === 401) notifySessionExpired()
  if (!response.ok) throw await responseError(response, '生命周期动画包生成失败。')
  return response.json() as Promise<ExpressionPack>
}

export function expressionPreviewUrl(packId: string, state: ExpressionState) {
  return `/api/v1/expression-packs/${encodeURIComponent(packId)}/states/${state}`
}

export function lifecycleClipUrl(packId: string, clip: LifecycleClip) {
  return `/api/v1/expression-packs/${encodeURIComponent(packId)}/clips/${clip}`
}

export const lifecycleClips: { label: string, value: LifecycleClip }[] = [
  { label: '开机出现', value: 'boot_appear' },
  { label: '苏醒', value: 'wake' },
  { label: '角色切换', value: 'role_switch' },
]

export function getDeviceExpressionPack(deviceId: string): Promise<DeviceExpressionPack> {
  return apiJson(`/api/v1/expression-packs/device?deviceId=${encodeURIComponent(deviceId)}`)
}

export function activateExpressionPack(packId: string, deviceId: string): Promise<DeviceExpressionPack> {
  return apiJson(`/api/v1/expression-packs/${encodeURIComponent(packId)}/activate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ deviceId }),
  })
}

export function deactivateExpressionPack(deviceId: string): Promise<DeviceExpressionPack> {
  return apiJson('/api/v1/expression-packs/deactivate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ deviceId }),
  })
}

export function deleteExpressionPack(packId: string): Promise<void> {
  return apiJson(`/api/v1/expression-packs/${encodeURIComponent(packId)}`, { method: 'DELETE' })
}

export function getExpressionFrameRate(deviceId: string): Promise<ExpressionFrameRateSettings> {
  return apiJson(`/api/v1/devices/${encodeURIComponent(deviceId)}/expression/frame-rate`)
}

export function updateExpressionFrameRate(
  deviceId: string,
  settings: Omit<ExpressionFrameRateSettings, 'applied'>,
): Promise<ExpressionFrameRateSettings> {
  return apiJson(`/api/v1/devices/${encodeURIComponent(deviceId)}/expression/frame-rate`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(settings),
  })
}

export async function previewExpression(deviceId: string, preview: ExpressionPreviewInput): Promise<void> {
  await apiJson(`/api/v1/devices/${encodeURIComponent(deviceId)}/expression/preview`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(preview),
  })
}
