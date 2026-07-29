import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  createMcpConnection,
  getAgentCapabilities,
  importAgentSkill,
  listAgentToolInvocations,
  deleteAgentSkill,
  deleteMcpConnection,
  updateAgentCapability,
  updateAgentRuntime,
  updateAgentSkill,
  updateMcpConnection,
} from './agent'

describe('agent management API', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('uses explicit read, refresh and mutation contracts', async () => {
    const fetchMock = vi.fn().mockImplementation(async () => new Response(JSON.stringify({}), {
      headers: { 'Content-Type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetchMock)

    await getAgentCapabilities(true)
    await updateAgentRuntime(false)
    await updateAgentCapability('MCP_TOOL', 'home/lookup', true)
    await listAgentToolInvocations(25)

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      '/api/v1/agent/capabilities?refreshMcp=true',
      expect.any(Object),
    )
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/agent/settings', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({ enabled: false }),
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/v1/agent/capabilities', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({ type: 'MCP_TOOL', capabilityId: 'home/lookup', enabled: true }),
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(
      4,
      '/api/v1/agent/tool-invocations?limit=25',
      expect.any(Object),
    )
  })

  it('uploads the complete Skill ZIP without setting a multipart boundary', async () => {
    const skill = { id: 'skill-id', name: 'daily-routine', enabled: false }
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(skill), {
      status: 201,
      headers: { 'Content-Type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetchMock)
    const archive = new File(['zip'], 'daily-routine.zip', { type: 'application/zip' })

    await expect(importAgentSkill(archive)).resolves.toEqual(skill)

    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(path).toBe('/api/v1/agent/skills')
    expect(init.headers).not.toHaveProperty('Content-Type')
    expect((init.body as FormData).get('archive')).toBe(archive)
  })

  it('enables and deletes one encoded managed Skill', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'skill/id', enabled: true }), {
        headers: { 'Content-Type': 'application/json' },
      }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await updateAgentSkill('skill/id', true)
    await deleteAgentSkill('skill/id')

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/agent/skills/skill%2Fid', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({ enabled: true }),
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/agent/skills/skill%2Fid', expect.objectContaining({
      method: 'DELETE',
    }))
  })

  it('creates, updates and deletes an encoded managed MCP connection', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'mcp/id' }), { status: 201, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'mcp/id' }), { headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)
    const input = {
      connectionName: 'my-coffee',
      url: 'https://mcp.example.com',
      endpoint: '/mcp',
      authType: 'BEARER' as const,
      bearerToken: 'new-secret',
    }

    await createMcpConnection(input)
    await updateMcpConnection('mcp/id', { ...input, bearerToken: '' })
    await deleteMcpConnection('mcp/id')

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/agent/mcp-connections', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify(input),
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/agent/mcp-connections/mcp%2Fid', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({ ...input, bearerToken: '' }),
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/v1/agent/mcp-connections/mcp%2Fid', expect.objectContaining({ method: 'DELETE' }))
  })
})
