import { apiJson, csrfHeaders, notifySessionExpired, responseError } from '../client'

export interface Device {
  commandAvailable: boolean
  displayName: string
  firmwareVersion: string
  id: string
  lastSeenAt: string | null
  online: boolean
  safetyState: string
}

export interface PairingCode {
  expiresAt: string
  value: string
}

interface DeviceListResponse {
  devices: Device[]
}

export async function listDevices(): Promise<Device[]> {
  return (await apiJson<DeviceListResponse>('/api/v1/devices')).devices
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
