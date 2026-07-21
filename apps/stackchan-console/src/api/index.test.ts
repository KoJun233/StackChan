import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => {
  const handlers: {
    responseFulfilled?: (response: any) => Promise<any>
    responseRejected?: (error: any) => Promise<any>
  } = {}
  const api = {
    interceptors: {
      request: {
        use: vi.fn(),
      },
      response: {
        use: vi.fn((fulfilled, rejected) => {
          handlers.responseFulfilled = fulfilled
          handlers.responseRejected = rejected
        }),
      },
    },
  }
  return {
    accountStore: {
      isLogin: true,
      requestLogout: vi.fn(),
      token: 'session',
    },
    api,
    handlers,
    logoutPromise: {
      catch: vi.fn(),
    },
  }
})

vi.mock('axios', () => ({
  default: {
    create: vi.fn(() => mocks.api),
  },
}))

vi.mock('@/store/modules/app/account', () => ({
  useAppAccountStore: () => mocks.accountStore,
}))

vi.mock('@fantastic-admin/components', () => ({
  useFaToast: () => ({
    error: vi.fn(),
    warning: vi.fn(),
  }),
}))

import './index'

describe('legacy API session logout handling', () => {
  beforeEach(() => {
    mocks.accountStore.requestLogout.mockReset()
    mocks.logoutPromise.catch.mockReset()
    mocks.accountStore.requestLogout.mockReturnValue(mocks.logoutPromise)
  })

  it('consumes logout navigation rejection after an HTTP 401', async () => {
    const error = { config: undefined, status: 401 }

    await expect(mocks.handlers.responseRejected?.(error)).rejects.toBe(error)

    expect(mocks.accountStore.requestLogout).toHaveBeenCalledOnce()
    expect(mocks.logoutPromise.catch).toHaveBeenCalledOnce()
  })

  it('consumes logout navigation rejection after an unauthenticated payload', async () => {
    const response = { data: { status: 0 } }

    await expect(mocks.handlers.responseFulfilled?.(response)).resolves.toEqual(response.data)

    expect(mocks.accountStore.requestLogout).toHaveBeenCalledOnce()
    expect(mocks.logoutPromise.catch).toHaveBeenCalledOnce()
  })
})
