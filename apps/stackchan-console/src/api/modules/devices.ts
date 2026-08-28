import { apiJson, csrfHeaders, notifySessionExpired, responseError } from '../client'

export interface Device {
  applicationOtaSupported: boolean
  body: DeviceBodyDiagnostics
  commandAvailable: boolean
  displayName: string
  dynamicExpressionSupported?: boolean
  lifecycleClipSupported?: boolean
  firmwareVersion: string
  id: string
  lastSeenAt: string | null
  online: boolean
  rssi: number | null
  safetyState: string
}

export type BodyMotion = 'DROWSY' | 'LOOK_USER' | 'NOD_SMALL' | 'THINK' | 'WAKE'

export interface DeviceBodyDiagnostics {
  ambientLight: 'BRIGHT' | 'DARK' | 'DIM' | 'NORMAL' | 'UNAVAILABLE'
  ambientLightSupported: boolean
  bodyMotionSupported: boolean
  bodyTouchSupported: boolean
  calibrated: boolean
  failureCount: number
  lastFailureCode: string
  motionState: 'ARMED' | 'DISABLED' | 'RUNNING'
  present: boolean
  proximitySupported: boolean
  servoFeedbackSupported: boolean
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
  await sendDeviceCommand(`/api/v1/devices/${encodeURIComponent(deviceId)}/commands/stop-motion`)
}

export async function configureDeviceBodyMotion(deviceId: string, enabled: boolean): Promise<void> {
  await sendDeviceCommand(`/api/v1/devices/${encodeURIComponent(deviceId)}/body-motion`, {
    method: 'PUT',
    body: JSON.stringify({ enabled }),
  })
}

export async function calibrateDeviceBody(deviceId: string): Promise<void> {
  await sendDeviceCommand(`/api/v1/devices/${encodeURIComponent(deviceId)}/commands/calibrate-body`)
}

export async function playDeviceBodyMotion(deviceId: string, motion: BodyMotion): Promise<void> {
  await sendDeviceCommand(`/api/v1/devices/${encodeURIComponent(deviceId)}/commands/body-motion`, {
    body: JSON.stringify({ motion }),
  })
}

async function sendDeviceCommand(path: string, options: RequestInit = {}): Promise<void> {
  const response = await fetch(path, {
    method: 'POST',
    ...options,
    credentials: 'same-origin',
    headers: {
      Accept: 'application/json',
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
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
