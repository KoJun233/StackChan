export interface ApiErrorBody {
  code?: string
  message?: string
}

export function notifySessionExpired() {
  window.dispatchEvent(new Event('stackchan:session-expired'))
}

export function csrfHeaders(): Record<string, string> {
  const token = document.cookie
    .split('; ')
    .find(item => item.startsWith('XSRF-TOKEN='))
    ?.split('=')
    .slice(1)
    .join('=')

  return token ? { 'X-XSRF-TOKEN': decodeURIComponent(token) } : {}
}

export async function responseError(response: Response, fallback = '请求未能完成，请稍后重试。'): Promise<Error> {
  try {
    const body = await response.json() as ApiErrorBody
    const message = typeof body.message === 'string' && body.message.trim() ? body.message : fallback
    return new Error(message)
  }
  catch {
    return new Error(fallback)
  }
}

export async function apiJson<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(path, {
    ...init,
    credentials: 'same-origin',
    headers: {
      Accept: 'application/json',
      ...csrfHeaders(),
      ...init.headers,
    },
  })
  if (response.status === 401) {
    notifySessionExpired()
  }
  if (!response.ok) {
    throw await responseError(response)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return response.json() as Promise<T>
}
