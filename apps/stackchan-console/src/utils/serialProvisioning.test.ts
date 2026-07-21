import { describe, expect, it, vi } from 'vitest'
import {
  buildProvisioningPayload,
  parseProvisioningStatus,
  provisionStackChan,
  validateServerBaseUrl,
} from './serialProvisioning'

describe('StackChan Web Serial provisioning', () => {
  it('builds exactly one compact firmware provisioning object', () => {
    const payload = buildProvisioningPayload({
      wifiSsid: 'StackChan-WiFi',
      wifiPassword: 'wifi-secret',
      serverBaseUrl: 'http://192.168.137.1:8080',
      pairingCode: 'ABCD_123',
    })

    expect(payload).toBe('{"type":"provision","wifiSsid":"StackChan-WiFi","wifiPassword":"wifi-secret","serverBaseUrl":"http://192.168.137.1:8080","pairingCode":"ABCD_123"}')
    expect(payload).not.toContain('\n')
  })

  it('accepts secure origins and private LAN HTTP origins only', () => {
    expect(validateServerBaseUrl('https://stackchan.example')).toBe(true)
    expect(validateServerBaseUrl('https://stackchan.example/')).toBe(true)
    expect(validateServerBaseUrl('http://10.0.0.5:8080')).toBe(true)
    expect(validateServerBaseUrl('http://172.31.255.254')).toBe(true)
    expect(validateServerBaseUrl('http://192.168.137.1:8080')).toBe(true)
    expect(validateServerBaseUrl('http://8.8.8.8:8080')).toBe(false)
    expect(validateServerBaseUrl('http://localhost:8080')).toBe(false)
    expect(validateServerBaseUrl('http://192.168.137.1:8080/')).toBe(false)
    expect(validateServerBaseUrl('https://stackchan.example/api')).toBe(false)
  })

  it('parses only strict non-secret firmware status lines', () => {
    expect(parseProvisioningStatus('{"type":"provisioning","status":"started"}')).toBe('started')
    expect(parseProvisioningStatus('{"type":"provisioning","status":"complete"}')).toBe('complete')
    expect(parseProvisioningStatus('{"type":"provisioning","status":"unknown"}')).toBeNull()
    expect(parseProvisioningStatus('{"type":"provisioning","status":"complete","token":"secret"}')).toBeNull()
    expect(parseProvisioningStatus('I (123) device: connected')).toBeNull()
  })

  it('writes credentials only to the selected serial port and returns the final status', async () => {
    const chunks: Uint8Array[] = []
    const writable = new WritableStream<Uint8Array>({
      write(chunk) {
        chunks.push(chunk)
      },
    })
    const readable = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(new TextEncoder().encode('boot log\n{"type":"provisioning","status":"started"}\n'))
        controller.enqueue(new TextEncoder().encode('{"type":"provisioning","status":"complete"}\n'))
        controller.close()
      },
    })
    const port = {
      open: vi.fn().mockResolvedValue(undefined),
      close: vi.fn().mockResolvedValue(undefined),
      readable,
      writable,
    }
    const statuses: string[] = []

    await expect(provisionStackChan(port, {
      wifiSsid: 'StackChan-WiFi',
      wifiPassword: 'wifi-secret',
      serverBaseUrl: 'https://stackchan.example',
      pairingCode: 'ABCD_123',
    }, status => statuses.push(status))).resolves.toBe('complete')

    expect(port.open).toHaveBeenCalledWith({ baudRate: 115200 })
    expect(port.close).toHaveBeenCalledOnce()
    expect(statuses).toEqual(['started', 'complete'])
    expect(new TextDecoder().decode(chunks[0])).toBe('{"type":"provision","wifiSsid":"StackChan-WiFi","wifiPassword":"wifi-secret","serverBaseUrl":"https://stackchan.example","pairingCode":"ABCD_123"}\n')
  })
})
