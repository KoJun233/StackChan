export interface WifiProvisioningRequest {
  pairingCode: string
  serverBaseUrl: string
  wifiPassword: string
  wifiSsid: string
}

export interface SerialPortLike {
  close: () => Promise<void>
  open: (options: { baudRate: number }) => Promise<void>
  readable: ReadableStream<Uint8Array> | null
  writable: WritableStream<Uint8Array> | null
}

export interface WebSerialApi {
  requestPort: () => Promise<SerialPortLike>
}

export type ProvisioningStatus =
  | 'started'
  | 'complete'
  | 'invalid_request'
  | 'identity_clear_failed'
  | 'wifi_configuration_failed'
  | 'wifi_connection_failed'
  | 'claim_failed'
  | 'identity_save_failed'

const STATUS_VALUES = new Set<ProvisioningStatus>([
  'started',
  'complete',
  'invalid_request',
  'identity_clear_failed',
  'wifi_configuration_failed',
  'wifi_connection_failed',
  'claim_failed',
  'identity_save_failed',
])

const FINAL_STATUSES = new Set<ProvisioningStatus>([
  'complete',
  'invalid_request',
  'identity_clear_failed',
  'wifi_configuration_failed',
  'wifi_connection_failed',
  'claim_failed',
  'identity_save_failed',
])

const MAX_PROVISIONING_LINE_BYTES = 511
const DEFAULT_TIMEOUT_MS = 45_000

function utf8Length(value: string): number {
  return new TextEncoder().encode(value).byteLength
}

export function getWebSerialApi(navigatorValue: Navigator = navigator): WebSerialApi | null {
  return (navigatorValue as Navigator & { serial?: WebSerialApi }).serial ?? null
}

export function validateServerBaseUrl(value: string): boolean {
  if (!value || utf8Length(value) > 191 || /\s/.test(value)) {
    return false
  }
  let url: URL
  try {
    url = new URL(value)
  }
  catch {
    return false
  }
  if (url.username || url.password || url.search || url.hash) {
    return false
  }
  if (url.protocol === 'https:') {
    return (url.pathname === '/' || url.pathname === '') && value.startsWith('https://')
  }
  if (url.protocol !== 'http:' || !value.startsWith('http://') || value.slice(7).includes('/')) {
    return false
  }
  const authority = value.slice(7)
  const match = authority.match(/^([^:]+)(?::([1-9]\d{0,4}))?$/)
  if (!match || (match[2] !== undefined && Number(match[2]) > 65535)) {
    return false
  }
  const parts = match[1].split('.')
  if (parts.length !== 4 || parts.some(part => !/^(0|[1-9]\d{0,2})$/.test(part))) {
    return false
  }
  const octets = parts.map(Number)
  if (octets.some(octet => octet > 255)) {
    return false
  }
  return octets[0] === 10
    || (octets[0] === 172 && octets[1] >= 16 && octets[1] <= 31)
    || (octets[0] === 192 && octets[1] === 168)
}

export function buildProvisioningPayload(request: WifiProvisioningRequest): string {
  if (!request.wifiSsid || utf8Length(request.wifiSsid) > 32) {
    throw new Error('Wi-Fi 名称不能为空且不能超过 32 字节。')
  }
  if (utf8Length(request.wifiPassword) > 63) {
    throw new Error('Wi-Fi 密码不能超过 63 字节。')
  }
  if (!validateServerBaseUrl(request.serverBaseUrl)) {
    throw new Error('服务器地址与固件支持的 HTTPS 或私有局域网 HTTP 格式不匹配。')
  }
  if (!/^[A-Za-z0-9_-]{1,12}$/.test(request.pairingCode)) {
    throw new Error('一次性配对码格式无效。')
  }
  const payload = JSON.stringify({
    type: 'provision',
    wifiSsid: request.wifiSsid,
    wifiPassword: request.wifiPassword,
    serverBaseUrl: request.serverBaseUrl,
    pairingCode: request.pairingCode,
  })
  if (utf8Length(payload) > MAX_PROVISIONING_LINE_BYTES) {
    throw new Error('配网信息过长，无法安全发送给机器人。')
  }
  return payload
}

export function parseProvisioningStatus(line: string): ProvisioningStatus | null {
  if (line.length > 128 || !line.startsWith('{')) {
    return null
  }
  try {
    const parsed = JSON.parse(line) as Record<string, unknown>
    const keys = Object.keys(parsed)
    if (keys.length !== 2 || parsed.type !== 'provisioning' || typeof parsed.status !== 'string') {
      return null
    }
    return STATUS_VALUES.has(parsed.status as ProvisioningStatus)
      ? parsed.status as ProvisioningStatus
      : null
  }
  catch {
    return null
  }
}

async function readWithTimeout(
  reader: ReadableStreamDefaultReader<Uint8Array>,
  timeoutMs: number,
): Promise<ReadableStreamReadResult<Uint8Array>> {
  let timeout: ReturnType<typeof setTimeout> | undefined
  try {
    return await Promise.race([
      reader.read(),
      new Promise<never>((_, reject) => {
        timeout = setTimeout(() => reject(new Error('等待机器人返回配网结果超时。')), timeoutMs)
      }),
    ])
  }
  finally {
    if (timeout !== undefined) {
      clearTimeout(timeout)
    }
  }
}

export async function provisionStackChan(
  port: SerialPortLike,
  request: WifiProvisioningRequest,
  onStatus?: (status: ProvisioningStatus) => void,
  timeoutMs = DEFAULT_TIMEOUT_MS,
): Promise<ProvisioningStatus> {
  const payload = buildProvisioningPayload(request)
  let reader: ReadableStreamDefaultReader<Uint8Array> | undefined
  let writer: WritableStreamDefaultWriter<Uint8Array> | undefined
  try {
    await port.open({ baudRate: 115200 })
    if (!port.readable || !port.writable) {
      throw new Error('所选串口不可读写，请重新连接机器人。')
    }
    writer = port.writable.getWriter()
    await writer.write(new TextEncoder().encode(`${payload}\n`))
    writer.releaseLock()
    writer = undefined

    reader = port.readable.getReader()
    const decoder = new TextDecoder()
    const deadline = Date.now() + timeoutMs
    let buffered = ''
    while (Date.now() < deadline) {
      const result = await readWithTimeout(reader, Math.max(1, deadline - Date.now()))
      buffered += decoder.decode(result.value, { stream: !result.done })
      const lines = buffered.split(/\r?\n/)
      buffered = result.done ? '' : lines.pop() ?? ''
      for (const line of lines) {
        const status = parseProvisioningStatus(line.trim())
        if (!status) {
          continue
        }
        onStatus?.(status)
        if (FINAL_STATUSES.has(status)) {
          return status
        }
      }
      if (result.done) {
        break
      }
    }
    throw new Error('机器人在返回最终配网结果前断开了连接。')
  }
  finally {
    try {
      await reader?.cancel()
    }
    catch {
      // The device can reset or disconnect immediately after provisioning.
    }
    reader?.releaseLock()
    writer?.releaseLock()
    try {
      await port.close()
    }
    catch {
      // Closing an already disconnected Web Serial port is harmless.
    }
  }
}
