import { apiJson, csrfHeaders, notifySessionExpired, responseError } from '../client'

export type ExpressionState = 'idle' | 'listening' | 'processing' | 'speaking' | 'success' | 'no_speech' | 'offline' | 'error'

export interface ExpressionPack {
  artifactSha256: string
  artifactSize: number
  createdAt: string
  description: string | null
  formatVersion: number
  id: string
  name: string
  states: ExpressionState[]
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

export function expressionPreviewUrl(packId: string, state: ExpressionState) {
  return `/api/v1/expression-packs/${encodeURIComponent(packId)}/states/${state}`
}

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
