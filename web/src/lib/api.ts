export interface Device {
  id: string
  displayName: string
  firmwareVersion: string
  safetyState: string
  lastSeenAt: string | null
  online: boolean
}

interface DeviceListResponse {
  devices: Device[]
}

interface PairingCodeResponse {
  value: string
}

interface ApiErrorResponse {
  error?: string
  message?: string
}

export interface LlmSettings {
  baseUrl: string
  model: string
  systemPrompt: string
  apiKeyConfigured: boolean
  updatedAt: string | null
}

export interface SaveLlmSettingsInput {
  baseUrl: string
  model: string
  systemPrompt: string
  apiKey: string
}

export type StopMotionResult =
  | { status: 'pending' }
  | { status: 'offline' }

export async function listDevices(): Promise<Device[]> {
  const response = await fetch('/api/v1/devices', { headers: { accept: 'application/json' } })
  if (!response.ok) {
    throw new Error('无法加载设备。')
  }

  const body = await response.json() as DeviceListResponse
  if (!Array.isArray(body.devices) || !body.devices.every(isDevice)) {
    throw new Error('无法加载设备。')
  }
  return body.devices
}

function isDevice(value: unknown): value is Device {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const device = value as Partial<Device>
  return typeof device.id === 'string'
    && typeof device.displayName === 'string'
    && typeof device.firmwareVersion === 'string'
    && typeof device.safetyState === 'string'
    && (typeof device.lastSeenAt === 'string' || device.lastSeenAt === null)
    && typeof device.online === 'boolean'
}

export async function stopMotion(deviceId: string): Promise<StopMotionResult> {
  const response = await fetch(`/api/v1/devices/${encodeURIComponent(deviceId)}/commands/stop-motion`, {
    method: 'POST',
    headers: { accept: 'application/json' },
  })

  if (response.status === 202) {
    return { status: 'pending' }
  }
  if (response.status === 409) {
    return { status: 'offline' }
  }
  throw new Error('无法发送停止动作指令。')
}

export async function createPairingCode(createdBy: string): Promise<string> {
  const trimmedCreator = createdBy.trim()
  if (!trimmedCreator) {
    throw new Error('请填写创建人。')
  }

  const response = await fetch('/api/v1/pairing/codes', {
    method: 'POST',
    headers: {
      accept: 'application/json',
      'content-type': 'application/json',
    },
    body: JSON.stringify({ createdBy: trimmedCreator }),
  })

  if (!response.ok) {
    throw new Error(await responseMessage(response, '无法创建配对码。'))
  }

  const body = await response.json() as PairingCodeResponse
  if (!body.value) {
    throw new Error('无法创建配对码。')
  }
  return body.value
}

export async function getLlmSettings(): Promise<LlmSettings> {
  const response = await fetch('/api/v1/settings/llm', { headers: { accept: 'application/json' } })
  if (!response.ok) {
    throw new Error('无法读取 AI 配置。')
  }

  const body = await response.json() as Partial<LlmSettings>
  if (typeof body.baseUrl !== 'string'
    || typeof body.model !== 'string'
    || typeof body.systemPrompt !== 'string'
    || typeof body.apiKeyConfigured !== 'boolean') {
    throw new Error('AI 配置数据格式无效。')
  }
  return {
    baseUrl: body.baseUrl,
    model: body.model,
    systemPrompt: body.systemPrompt,
    apiKeyConfigured: body.apiKeyConfigured,
    updatedAt: typeof body.updatedAt === 'string' ? body.updatedAt : null,
  }
}

export async function saveLlmSettings(input: SaveLlmSettingsInput): Promise<LlmSettings> {
  const baseUrl = input.baseUrl.trim()
  const model = input.model.trim()
  if (!baseUrl) {
    throw new Error('请填写接口地址。')
  }
  if (!model) {
    throw new Error('请填写模型名称。')
  }

  const response = await fetch('/api/v1/settings/llm', {
    method: 'PUT',
    headers: {
      accept: 'application/json',
      'content-type': 'application/json',
    },
    body: JSON.stringify({
      baseUrl,
      model,
      systemPrompt: input.systemPrompt,
      apiKey: input.apiKey.trim(),
    }),
  })
  if (!response.ok) {
    throw new Error(await responseMessage(response, '无法保存 AI 配置。'))
  }
  return getLlmSettingsFromResponse(response)
}

async function getLlmSettingsFromResponse(response: Response): Promise<LlmSettings> {
  const body = await response.json() as Partial<LlmSettings>
  if (typeof body.baseUrl !== 'string'
    || typeof body.model !== 'string'
    || typeof body.systemPrompt !== 'string'
    || typeof body.apiKeyConfigured !== 'boolean') {
    throw new Error('AI 配置数据格式无效。')
  }
  return {
    baseUrl: body.baseUrl,
    model: body.model,
    systemPrompt: body.systemPrompt,
    apiKeyConfigured: body.apiKeyConfigured,
    updatedAt: typeof body.updatedAt === 'string' ? body.updatedAt : null,
  }
}

async function responseMessage(response: Response, fallback: string): Promise<string> {
  try {
    const body = await response.json() as ApiErrorResponse
    if (body.message) {
      return body.message
    }
    if (body.error === 'pairing_code_unavailable') {
      return '配对码暂时不可用。'
    }
    if (body.error === 'invalid_llm_settings') {
      return 'AI 配置无效，请检查接口地址和 API 密钥。'
    }
  } catch {
    // Use the stable local message when the server returns no JSON error body.
  }
  return fallback
}
