import { afterEach, describe, expect, it, vi } from 'vitest'
import { deleteRole } from './roles'

describe('companion roles API', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('permanently deletes an eligible archived role', async () => {
    const roleId = '8f1ee5df-7d93-4b31-b57e-fce29c4c661b'
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('document', { cookie: '' })
    vi.stubGlobal('fetch', fetchMock)

    await deleteRole(roleId)

    expect(fetchMock).toHaveBeenCalledWith(`/api/v1/roles/${roleId}`, expect.objectContaining({ method: 'DELETE' }))
  })
})
