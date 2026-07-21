import { responseError } from '../client'

interface CsrfTokenResponse {
  headerName: string
  token: string
}

export interface LoginInput {
  username: string
  password: string
}

export interface ChangePasswordInput {
  currentPassword: string
  newPassword: string
}

export async function materializeCsrfToken(): Promise<CsrfTokenResponse> {
  const response = await fetch('/api/v1/auth/csrf', {
    credentials: 'same-origin',
    headers: {
      Accept: 'application/json',
    },
  })
  if (!response.ok) {
    throw await responseError(response, '无法建立安全会话，请刷新页面后重试。')
  }
  const csrf = await response.json() as CsrfTokenResponse
  if (!csrf.token || !csrf.headerName) {
    throw new Error('无法建立安全会话，请刷新页面后重试。')
  }
  return csrf
}

export async function login(input: LoginInput): Promise<void> {
  const csrf = await materializeCsrfToken()
  const response = await fetch('/api/v1/auth/login', {
    method: 'POST',
    credentials: 'same-origin',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      [csrf.headerName]: csrf.token,
    },
    body: JSON.stringify(input),
  })
  if (!response.ok) {
    throw await responseError(response, '用户名或密码不正确。')
  }
}

export async function logout(): Promise<void> {
  const csrf = await materializeCsrfToken()
  const response = await fetch('/api/v1/auth/logout', {
    method: 'POST',
    credentials: 'same-origin',
    headers: {
      Accept: 'application/json',
      [csrf.headerName]: csrf.token,
    },
  })
  if (!response.ok) {
    throw await responseError(response, '退出登录失败，请稍后重试。')
  }
}

export async function changePassword(input: ChangePasswordInput): Promise<void> {
  const csrf = await materializeCsrfToken()
  const response = await fetch('/api/v1/auth/password', {
    method: 'POST',
    credentials: 'same-origin',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      [csrf.headerName]: csrf.token,
    },
    body: JSON.stringify(input),
  })
  if (!response.ok) {
    throw await responseError(response, '修改密码失败，请确认原密码后重试。')
  }
}
