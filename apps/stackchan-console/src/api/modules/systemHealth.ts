import type { Device } from './devices'
import { apiJson } from '../client'

export interface ProviderStatus {
  configured: boolean
  connectivity: {
    checkedAt: string | null
    failureCode: string | null
    status: 'HEALTHY' | 'FAILED' | 'UNKNOWN' | 'NOT_CONFIGURED'
  }
  provider: 'llm' | 'speech'
}

export interface BackupHealth {
  available: boolean
  dailyBackupCount: number
  lastAttemptAt: string | null
  lastFailureAt: string | null
  lastFailureCode: string | null
  lastRestoreVerificationAt: string | null
  lastRestoreVerificationFailureCode: string | null
  lastRestoreVerificationSuccessful: boolean | null
  lastSuccessfulBackupAt: string | null
  storageBytes: number
  weeklyBackupCount: number
}

export interface SafeError {
  category: 'VOICE_TURN' | 'FIRMWARE_UPDATE' | 'EXTERNAL_NOTIFICATION'
  deviceId: string
  failureCode: string
  occurredAt: string
  status: string
}

export interface SystemHealth {
  backup: BackupHealth
  checkedAt: string
  databaseMigration: string
  devices: (Device & { applicationOtaSupported: boolean, rssi: number | null })[]
  pendingJobs: {
    expressionPacks: number
    firmwareUpdates: number
    reminders: number
    wakeModels: number
  }
  notifications: {
    enabledIntegrations: number
    expiredLast24Hours: number
    failedLast24Hours: number
    queued: number
  }
  providers: ProviderStatus[]
  recentSafeErrors: SafeError[]
  serverVersion: string
}

export function getSystemHealth(): Promise<SystemHealth> {
  return apiJson('/api/v1/system/health')
}
