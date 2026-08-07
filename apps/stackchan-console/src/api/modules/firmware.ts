import { apiJson, csrfHeaders, notifySessionExpired, responseError } from '../client'

export interface FirmwareRelease {
  artifactSha256: string
  artifactSize: number
  createdAt: string
  id: string
  projectName: string
  version: string
}

export interface FirmwareUpdateJob {
  completedAt: string | null
  createdAt: string
  deviceId: string
  failureCode: string | null
  fromVersion: string
  id: string
  releaseId: string
  status: 'READY' | 'INSTALLING' | 'INSTALLED' | 'FAILED' | 'ROLLED_BACK'
  targetVersion: string
  updatedAt: string
}

export async function listFirmwareReleases(): Promise<FirmwareRelease[]> {
  return (await apiJson<{ releases: FirmwareRelease[] }>('/api/v1/firmware/releases')).releases
}

export async function importFirmwareRelease(file: File, version: string): Promise<FirmwareRelease> {
  const form = new FormData()
  form.append('artifact', file)
  const response = await fetch(`/api/v1/firmware/releases?version=${encodeURIComponent(version)}`, {
    method: 'POST',
    credentials: 'same-origin',
    headers: { Accept: 'application/json', ...csrfHeaders() },
    body: form,
  })
  if (response.status === 401) {
    notifySessionExpired()
  }
  if (!response.ok) {
    throw await responseError(response, '固件制品导入失败。')
  }
  return response.json() as Promise<FirmwareRelease>
}

export async function listFirmwareUpdateJobs(deviceId: string): Promise<FirmwareUpdateJob[]> {
  return (await apiJson<{ jobs: FirmwareUpdateJob[] }>(
    `/api/v1/firmware/jobs?deviceId=${encodeURIComponent(deviceId)}`,
  )).jobs
}

export function createFirmwareUpdateJob(input: {
  confirmedCurrentVersion: string
  deviceId: string
  releaseId: string
}): Promise<FirmwareUpdateJob> {
  return apiJson('/api/v1/firmware/jobs', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}
