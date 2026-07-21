import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiJson, responseError } from './client'

describe('API client session handling', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('notifies the application when an authenticated API request receives 401', async () => {
    const onExpired = vi.fn()
    window.addEventListener('stackchan:session-expired', onExpired)
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ error: 'authentication_failed' }), {
      status: 401,
      headers: { 'Content-Type': 'application/json' },
    })))

    await expect(apiJson('/api/v1/devices')).rejects.toThrow()

    expect(onExpired).toHaveBeenCalledOnce()
    window.removeEventListener('stackchan:session-expired', onExpired)
  })

  it('uses the typed server message and falls back safely', async () => {
    await expect(responseError(new Response(JSON.stringify({
      code: 'device_offline',
      message: '设备当前离线，无法接收安全停止命令。',
    }), { status: 409 }))).resolves.toEqual(new Error('设备当前离线，无法接收安全停止命令。'))

    await expect(responseError(new Response(JSON.stringify({
      error: 'provider_internal_detail',
    }), { status: 503 }))).resolves.toEqual(new Error('请求未能完成，请稍后重试。'))

    await expect(responseError(new Response(JSON.stringify({}), { status: 500 })))
      .resolves.toEqual(new Error('请求未能完成，请稍后重试。'))

    await expect(responseError(new Response(JSON.stringify({
      message: 503,
    }), { status: 500 }))).resolves.toEqual(new Error('请求未能完成，请稍后重试。'))

    await expect(responseError(new Response(JSON.stringify({
      code: 'provider_internal_detail',
      message: '   ',
    }), { status: 503 }))).resolves.toEqual(new Error('请求未能完成，请稍后重试。'))

    await expect(responseError(new Response('{', { status: 500 })))
      .resolves.toEqual(new Error('请求未能完成，请稍后重试。'))

    await expect(responseError(new Response(null, { status: 500 })))
      .resolves.toEqual(new Error('请求未能完成，请稍后重试。'))
  })

  it('consumes an error response once', async () => {
    const response = new Response(JSON.stringify({ error: 'internal_detail' }), { status: 500 })
    const json = vi.spyOn(response, 'json')

    await expect(responseError(response)).resolves.toEqual(new Error('请求未能完成，请稍后重试。'))

    expect(json).toHaveBeenCalledOnce()
    expect(response.bodyUsed).toBe(true)
  })

  it('returns undefined for an empty successful response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })))

    await expect(apiJson<void>('/api/v1/auth/logout', { method: 'POST' })).resolves.toBeUndefined()
  })
})
