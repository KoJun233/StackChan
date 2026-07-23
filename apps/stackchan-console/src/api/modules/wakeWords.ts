import { apiJson } from '../client'

export type WakeWordModelJobStatus =
  | 'FAILED'
  | 'GENERATING'
  | 'INSTALLED'
  | 'INSTALLING'
  | 'QUEUED'
  | 'READY'
  | 'ROLLED_BACK'

export interface WakeWordModelJob {
  artifactSha256: string | null
  artifactSize: number | null
  createdAt: string
  deviceId: string
  failureCode: string | null
  id: string
  installedAt: string | null
  modelName: string | null
  phrase: string
  status: WakeWordModelJobStatus
  updatedAt: string
}

export interface WakeWordModelOption {
  locale: string
  modelName: string
  phrase: string
}

interface WakeWordModelJobListResponse {
  jobs: WakeWordModelJob[]
}

interface WakeWordModelCatalogResponse {
  models: WakeWordModelOption[]
}

export function createWakeWordModelJob(deviceId: string, modelName: string): Promise<WakeWordModelJob> {
  return apiJson('/api/v1/wake-word-model-jobs', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ deviceId, modelName }),
  })
}

export async function listWakeWordModels(): Promise<WakeWordModelOption[]> {
  const response = await apiJson<WakeWordModelCatalogResponse>('/api/v1/wake-word-model-jobs/catalog')
  return response.models
}

export async function listWakeWordModelJobs(deviceId: string): Promise<WakeWordModelJob[]> {
  const response = await apiJson<WakeWordModelJobListResponse>(
    `/api/v1/wake-word-model-jobs?deviceId=${encodeURIComponent(deviceId)}`,
  )
  return response.jobs
}
