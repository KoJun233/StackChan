import { describe, expect, it, vi } from 'vitest'
import { changePassword, login } from './auth'

describe('administrator authentication API', () => {
  it('materializes CSRF and uses it for a JSON login request', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        headerName: 'X-XSRF-TOKEN',
        token: 'csrf-token',
      }), { headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await login({ username: 'admin', password: 'safe-password' })

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/auth/csrf', expect.objectContaining({
      credentials: 'same-origin',
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/auth/login', expect.objectContaining({
      body: JSON.stringify({ username: 'admin', password: 'safe-password' }),
      credentials: 'same-origin',
      headers: expect.objectContaining({ 'X-XSRF-TOKEN': 'csrf-token' }),
      method: 'POST',
    }))
  })

  it('changes the password with CSRF and an empty successful response', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        headerName: 'X-XSRF-TOKEN',
        token: 'csrf-token',
      }), { headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await changePassword({ currentPassword: 'old-password', newPassword: 'new-password-123' })

    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/auth/password', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ currentPassword: 'old-password', newPassword: 'new-password-123' }),
      headers: expect.objectContaining({ 'X-XSRF-TOKEN': 'csrf-token' }),
    }))
  })
})
