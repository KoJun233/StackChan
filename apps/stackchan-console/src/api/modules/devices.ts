import { apiJson, csrfHeaders, notifySessionExpired, responseError } from '../client'

export interface Device {
  applicationOtaSupported: boolean
  commandAvailable: boolean
  displayName: string
  firmwareVersion: string
  id: string
  lastSeenAt: string | null
  online: boolean
  rssi: number | null
  safetyState: string
}

export interface PairingCode {
  expiresAt: string
  value: string
}

export type VoiceTurnStatus = 'IN_PROGRESS' | 'RESPONSE_READY' | 'COMPLETED' | 'CANCELLED' | 'FAILED'

export interface VoiceTurnEvent {
  elapsedMs: number | null
  failureCode: string | null
  occurredAt: string
  source: 'DEVICE' | 'SERVER'
  stage: string
}

export interface VoiceTurn {
  events: VoiceTurnEvent[]
  failureCode: string | null
  startedAt: string
  status: VoiceTurnStatus
  turnId: string
  updatedAt: string
}

interface DeviceListResponse {
  devices: Device[]
}

interface VoiceTurnListResponse {
  turns: VoiceTurn[]
}

export async function listDevices(): Promise<Device[]> {
  return (await apiJson<DeviceListResponse>('/api/v1/devices')).devices
}

export async function listDeviceVoiceTurns(deviceId: string, limit = 10): Promise<VoiceTurn[]> {
  const response = await apiJson<VoiceTurnListResponse>(
    `/api/v1/devices/${encodeURIComponent(deviceId)}/voice-turns?limit=${limit}`,
  )
  return response.turns
}

export function createPairingCode(createdBy: string): Promise<PairingCode> {
  return apiJson('/api/v1/pairing/codes', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ createdBy }),
  })
}

export async function stopDeviceMotion(deviceId: string): Promise<void> {
  const response = await fetch(`/api/v1/devices/${deviceId}/commands/stop-motion`, {
    method: 'POST',
    credentials: 'same-origin',
    headers: {
      Accept: 'application/json',
      ...csrfHeaders(),
    },
  })
  if (response.status === 401) {
    notifySessionExpired()
  }
  if (!response.ok) {
    throw await responseError(response, '设备当前无法接收安全停止命令。')
  }
}

export function isPairingCodeExpired(pairingCode: PairingCode, now = Date.now()): boolean {
  const expiresAt = Date.parse(pairingCode.expiresAt)
  return !Number.isFinite(expiresAt) || now >= expiresAt
}
