import { fireEvent, render, screen } from '@testing-library/vue'
import { describe, expect, it, vi } from 'vitest'
import DeviceRow from './DeviceRow.vue'
import * as api from '../lib/api'

vi.mock('../lib/api', () => ({ stopMotion: vi.fn() }))

const device = {
  id: '4e7e985e-8f4c-46f2-a691-d2ec792e8423',
  displayName: 'Studio StackChan',
  firmwareVersion: '1.4.2',
  safetyState: 'motion_enabled',
  lastSeenAt: null,
  online: true,
}

describe('DeviceRow', () => {
  it('keeps a 32-character firmware version available in its labelled cell', () => {
    const firmwareVersion = '1234567890ABCDEF1234567890ABCDEF'
    render(DeviceRow, { props: { device: { ...device, firmwareVersion } } })

    expect(screen.getByRole('cell', { name: `固件版本${firmwareVersion}` })).toBeVisible()
  })

  it('renders the explicit online state supplied by the current device list', () => {
    render(DeviceRow, { props: { device } })

    expect(screen.getByText('在线')).toBeVisible()
  })

  it('shows a pending state after a 202 stop command', async () => {
    vi.mocked(api.stopMotion).mockResolvedValue({ status: 'pending' })
    render(DeviceRow, { props: { device } })

    await fireEvent.click(screen.getByRole('button', { name: '停止 Studio StackChan 的动作' }))

    expect(await screen.findByText('停止动作指令已发送，等待设备确认')).toBeVisible()
  })

  it('shows an offline error after a 409 stop command', async () => {
    vi.mocked(api.stopMotion).mockResolvedValue({ status: 'offline' })
    render(DeviceRow, { props: { device } })

    await fireEvent.click(screen.getByRole('button', { name: '停止 Studio StackChan 的动作' }))

    expect(await screen.findByText('设备已离线，未能发送停止动作指令。')).toBeVisible()
  })
})
