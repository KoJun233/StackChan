import { apiJson, csrfHeaders, notifySessionExpired, responseError } from '../client'

export type AgentCapabilityType = 'BUILTIN_TOOL' | 'SKILL' | 'MCP_SERVER' | 'MCP_TOOL'
export type AgentChannel = 'WEB' | 'VOICE'
export type AgentToolSource = 'BUILTIN' | 'SKILL' | 'MCP'
export type AgentToolOutcome = 'SUCCESS' | 'TOOL_FAILED' | 'RESULT_TRUNCATED' | 'RESULT_BUDGET_EXCEEDED'

export interface AgentRuntimeSettings {
  adminEnabled: boolean
  deploymentEnabled: boolean
  enabled: boolean
  updatedAt: string
}

export interface AgentLimits {
  maxToolCalls: number
  maxToolResultBytes: number
  maxTotalToolResultBytes: number
  timeoutSeconds: number
}

export interface AgentCapability {
  description: string
  enabled: boolean
  id: string
}

export interface AgentSkill {
  contentSha256: string
  createdAt: string
  description: string
  enabled: boolean
  fileCount: number
  files: string[]
  id: string
  name: string
  uncompressedBytes: number
  updatedAt: string
  version: string | null
}

export interface McpConnection {
  authType: 'NONE' | 'BEARER'
  authenticationConfigured: boolean
  connectionName: string
  discoveredToolCount: number
  enabled: boolean
  endpoint: string | null
  failureCode: string | null
  healthy: boolean
  id: string | null
  managed: boolean
  serverName: string
  serverVersion: string
  url: string | null
}

export interface McpConnectionInput {
  authType: 'NONE' | 'BEARER'
  bearerToken: string
  connectionName: string
  endpoint: string
  url: string
}

export interface McpTool {
  callbackName: string
  capabilityId: string
  connectionName: string
  description: string | null
  enabled: boolean
  originalName: string
  schemaSha256: string
  sourceIdentity: string
}

export interface AgentCapabilities {
  builtInTools: AgentCapability[]
  framework: string
  frameworkVersion: string
  limits: AgentLimits
  mcp: {
    connections: McpConnection[]
    discoveredAt: string
    tools: McpTool[]
  }
  runtime: AgentRuntimeSettings
  skills: AgentSkill[]
}

export interface AgentCapabilitySetting {
  capabilityId: string
  enabled: boolean
  schemaSha256: string | null
  sourceId: string | null
  type: AgentCapabilityType
  updatedAt: string
}

export interface AgentToolInvocation {
  channel: AgentChannel
  conversationId: string | null
  createdAt: string
  deviceId: string | null
  durationMs: number
  id: string
  outcome: AgentToolOutcome
  resultBytes: number
  skillId: string | null
  source: AgentToolSource
  sourceId: string | null
  toolName: string
  truncated: boolean
  turnId: string
}

export function getAgentCapabilities(refreshMcp = false): Promise<AgentCapabilities> {
  return apiJson(`/api/v1/agent/capabilities?refreshMcp=${refreshMcp}`)
}

export function updateAgentRuntime(enabled: boolean): Promise<AgentRuntimeSettings> {
  return apiJson('/api/v1/agent/settings', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ enabled }),
  })
}

export function updateAgentCapability(
  type: AgentCapabilityType,
  capabilityId: string,
  enabled: boolean,
): Promise<AgentCapabilitySetting> {
  return apiJson('/api/v1/agent/capabilities', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ type, capabilityId, enabled }),
  })
}

export function listAgentToolInvocations(limit = 50): Promise<AgentToolInvocation[]> {
  return apiJson(`/api/v1/agent/tool-invocations?limit=${encodeURIComponent(limit)}`)
}

export async function importAgentSkill(archive: File): Promise<AgentSkill> {
  const form = new FormData()
  form.append('archive', archive)
  const response = await fetch('/api/v1/agent/skills', {
    method: 'POST',
    credentials: 'same-origin',
    headers: { Accept: 'application/json', ...csrfHeaders() },
    body: form,
  })
  if (response.status === 401) {
    notifySessionExpired()
  }
  if (!response.ok) {
    throw await responseError(response, 'Skill 压缩包导入失败。')
  }
  return response.json() as Promise<AgentSkill>
}

export function updateAgentSkill(id: string, enabled: boolean): Promise<AgentSkill> {
  return apiJson(`/api/v1/agent/skills/${encodeURIComponent(id)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ enabled }),
  })
}

export function deleteAgentSkill(id: string): Promise<void> {
  return apiJson(`/api/v1/agent/skills/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

export function createMcpConnection(input: McpConnectionInput): Promise<McpConnection> {
  return apiJson('/api/v1/agent/mcp-connections', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export function updateMcpConnection(id: string, input: McpConnectionInput): Promise<McpConnection> {
  return apiJson(`/api/v1/agent/mcp-connections/${encodeURIComponent(id)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export function deleteMcpConnection(id: string): Promise<void> {
  return apiJson(`/api/v1/agent/mcp-connections/${encodeURIComponent(id)}`, { method: 'DELETE' })
}
