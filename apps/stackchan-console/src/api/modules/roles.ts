import { apiJson } from '../client'
import type { PersonaProactivity, PersonaReplyLength, PersonaTone } from './personaMemory'

export interface CompanionRole {
  archivedAt: string | null
  backgroundInstructions: string
  createdAt: string
  defaultRole: boolean
  id: string
  name: string
  proactivity: PersonaProactivity
  replyLength: PersonaReplyLength
  taboos: string
  tone: PersonaTone
  topicBoundaries: string
  updatedAt: string
}

export type CompanionRoleInput = Pick<CompanionRole,
  'name' | 'tone' | 'replyLength' | 'proactivity' | 'backgroundInstructions' | 'topicBoundaries' | 'taboos'>

export function listRoles(): Promise<CompanionRole[]> {
  return apiJson('/api/v1/roles')
}

export function getRole(id: string): Promise<CompanionRole> {
  return apiJson(`/api/v1/roles/${encodeURIComponent(id)}`)
}

export function createRole(input: CompanionRoleInput): Promise<CompanionRole> {
  return apiJson('/api/v1/roles', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input) })
}

export function updateRole(id: string, input: CompanionRoleInput): Promise<CompanionRole> {
  return apiJson(`/api/v1/roles/${encodeURIComponent(id)}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input) })
}

export function archiveRole(id: string): Promise<CompanionRole> {
  return apiJson(`/api/v1/roles/${encodeURIComponent(id)}:archive`, { method: 'POST' })
}

export function restoreRole(id: string): Promise<CompanionRole> {
  return apiJson(`/api/v1/roles/${encodeURIComponent(id)}:restore`, { method: 'POST' })
}

export function getDeviceActiveRole(deviceId: string): Promise<CompanionRole> {
  return apiJson(`/api/v1/devices/${encodeURIComponent(deviceId)}/active-role`)
}

export function setDeviceActiveRole(deviceId: string, roleId: string): Promise<CompanionRole> {
  return apiJson(`/api/v1/devices/${encodeURIComponent(deviceId)}/active-role`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ roleId }),
  })
}
