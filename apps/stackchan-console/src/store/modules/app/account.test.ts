import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const authApi = vi.hoisted(() => ({
  changePassword: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

const router = vi.hoisted(() => ({
  currentRoute: {
    value: {
      fullPath: '/settings/account',
      name: 'account',
    },
  },
  push: vi.fn(() => Promise.resolve()),
}))

vi.mock('@/api/modules/auth', () => authApi)
vi.mock('@/router', () => ({ default: router }))

import { useAppAccountStore } from './account'
import { useAppMenuStore } from './menu'
import { useAppRouteStore } from './route'
import { useAppSettingsStore } from './settings'
import { useAppTabbarStore } from './tabbar'

describe('app account store', () => {
  beforeEach(() => {
    sessionStorage.clear()
    setActivePinia(createPinia())
    authApi.changePassword.mockReset()
    authApi.logout.mockReset()
    router.push.mockReset()
    router.push.mockResolvedValue(undefined)
  })

  it('waits for password-change navigation before clearing the complete browser session', async () => {
    sessionStorage.setItem('authenticated', 'true')
    sessionStorage.setItem('account', 'admin')
    authApi.changePassword.mockResolvedValue(undefined)
    const navigation = deferred<void>()
    router.push.mockReturnValue(navigation.promise)
    const store = useAppAccountStore()
    await store.getPermissions()
    const cleanup = cleanupSpies()
    let settled = false

    const pending = store.editPassword({ password: 'old-password', newPassword: 'new-password-123' })
      .then(() => settled = true)

    expect(authApi.changePassword).toHaveBeenCalledWith({
      currentPassword: 'old-password',
      newPassword: 'new-password-123',
    })
    await vi.waitFor(() => expect(router.push).toHaveBeenCalled())
    expect(settled).toBe(false)
    expect(store.token).toBe('')
    expect(sessionStorage.getItem('authenticated')).toBeNull()
    expect(sessionStorage.getItem('account')).toBe('admin')
    expect(store.permissions).toEqual(['admin'])
    expect(cleanup.cleanTabbar).not.toHaveBeenCalled()

    navigation.resolve()
    await pending

    expect(sessionStorage.getItem('account')).toBeNull()
    expect(sessionStorage.getItem('authenticated')).toBeNull()
    expect(store.account).toBe('')
    expect(store.token).toBe('')
    expect(store.permissions).toEqual([])
    expectCompleteCleanup(cleanup)
  })

  it('cleans the complete browser session when password-change navigation rejects', async () => {
    sessionStorage.setItem('authenticated', 'true')
    sessionStorage.setItem('account', 'admin')
    authApi.changePassword.mockResolvedValue(undefined)
    router.push.mockRejectedValue(new Error('navigation failed'))
    const store = useAppAccountStore()
    await store.getPermissions()
    const cleanup = cleanupSpies()

    await expect(store.editPassword({ password: 'old-password', newPassword: 'new-password-123' }))
      .rejects.toThrow('navigation failed')

    expect(sessionStorage.getItem('account')).toBeNull()
    expect(sessionStorage.getItem('authenticated')).toBeNull()
    expect(store.permissions).toEqual([])
    expectCompleteCleanup(cleanup)
  })

  it('waits for manual logout navigation and always runs complete cleanup', async () => {
    sessionStorage.setItem('authenticated', 'true')
    sessionStorage.setItem('account', 'admin')
    authApi.logout.mockResolvedValue(undefined)
    const navigation = deferred<void>()
    router.push.mockReturnValue(navigation.promise)
    const store = useAppAccountStore()
    await store.getPermissions()
    const cleanup = cleanupSpies()
    let settled = false

    const pending = store.logout().then(() => settled = true)

    await vi.waitFor(() => expect(router.push).toHaveBeenCalled())
    expect(settled).toBe(false)
    expect(store.token).toBe('')
    expect(sessionStorage.getItem('authenticated')).toBeNull()
    expect(sessionStorage.getItem('account')).toBe('admin')
    expect(store.permissions).toEqual(['admin'])
    expect(cleanup.cleanTabbar).not.toHaveBeenCalled()
    navigation.resolve()
    await pending

    expect(sessionStorage.getItem('account')).toBeNull()
    expect(store.permissions).toEqual([])
    expectCompleteCleanup(cleanup)
  })

  it('still navigates and cleans up when the server logout request fails', async () => {
    sessionStorage.setItem('authenticated', 'true')
    sessionStorage.setItem('account', 'admin')
    authApi.logout.mockRejectedValue(new Error('server logout failed'))
    const store = useAppAccountStore()
    await store.getPermissions()
    const cleanup = cleanupSpies()

    await expect(store.logout()).rejects.toThrow('server logout failed')

    expect(router.push).toHaveBeenCalled()
    expect(sessionStorage.getItem('authenticated')).toBeNull()
    expect(sessionStorage.getItem('account')).toBeNull()
    expect(store.token).toBe('')
    expect(store.permissions).toEqual([])
    expectCompleteCleanup(cleanup)
  })
})

function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, reject, resolve }
}

function cleanupSpies() {
  return {
    cleanTabbar: vi.spyOn(useAppTabbarStore(), 'clean'),
    removeRoutes: vi.spyOn(useAppRouteStore(), 'removeRoutes'),
    resetMenu: vi.spyOn(useAppMenuStore(), 'setActived'),
    resetSettings: vi.spyOn(useAppSettingsStore(), 'updateSettings'),
  }
}

function expectCompleteCleanup(cleanup: ReturnType<typeof cleanupSpies>) {
  expect(cleanup.cleanTabbar).toHaveBeenCalled()
  expect(cleanup.removeRoutes).toHaveBeenCalled()
  expect(cleanup.resetMenu).toHaveBeenCalledWith(0)
  expect(cleanup.resetSettings).toHaveBeenCalledWith({}, true)
}
