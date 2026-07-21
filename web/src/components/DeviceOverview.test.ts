import { fireEvent, render, screen, within } from '@testing-library/vue'
import { describe, expect, it, vi } from 'vitest'
import DeviceOverview from './DeviceOverview.vue'
import * as api from '../lib/api'

vi.mock('../lib/api', () => ({ listDevices: vi.fn(), stopMotion: vi.fn() }))

const onlineDevice = {
  id: '4e7e985e-8f4c-46f2-a691-d2ec792e8423',
  displayName: 'Studio StackChan',
  firmwareVersion: '1.4.2',
  safetyState: 'motion_enabled',
  lastSeenAt: '2026-07-17T14:59:30Z',
  online: true,
}

const offlineDevice = { ...onlineDevice, online: false }

describe('DeviceOverview', () => {
  it('shows a loading state while devices are requested', () => {
    vi.mocked(api.listDevices).mockReturnValue(new Promise(() => {}))
    render(DeviceOverview)

    expect(screen.getByText('正在加载设备…')).toBeVisible()
  })

  it('shows an empty state when no devices are registered', async () => {
    vi.mocked(api.listDevices).mockResolvedValue([])
    render(DeviceOverview)

    expect(await screen.findByText('尚未配对任何设备')).toBeVisible()
  })

  it('shows a retryable failure state when the API fails', async () => {
    vi.mocked(api.listDevices).mockRejectedValue(new Error('无法加载设备。'))
    render(DeviceOverview)

    expect(await screen.findByText('无法加载设备。')).toBeVisible()
    expect(screen.getByRole('button', { name: '重新加载设备' })).toBeVisible()
  })

  it('uses the offline connection state returned by the server', async () => {
    vi.mocked(api.listDevices).mockResolvedValue([offlineDevice])
    render(DeviceOverview)

    expect(await screen.findByText('离线')).toBeVisible()
    const table = screen.getByRole('table', { name: '已配对设备' })
    expect(within(table).getByRole('columnheader', { name: '固件版本' })).toHaveAttribute('scope', 'col')
    expect(within(table).getByRole('columnheader', { name: '安全状态' })).toHaveAttribute('scope', 'col')
    expect(within(table).getByRole('cell', { name: /固件版本.*1\.4\.2/ })).toBeVisible()
    expect(within(table).getByRole('cell', { name: /安全状态.*动作已启用/ })).toBeVisible()
  })

  it('marks a device offline after a 409 stop command', async () => {
    vi.mocked(api.listDevices).mockResolvedValue([onlineDevice])
    vi.mocked(api.stopMotion).mockResolvedValue({ status: 'offline' })
    render(DeviceOverview)

    await fireEvent.click(await screen.findByRole('button', { name: '停止 Studio StackChan 的动作' }))

    expect(await screen.findByText('离线')).toBeVisible()
    expect(screen.getByText('设备已离线，未能发送停止动作指令。')).toBeVisible()
    expect(screen.getByRole('button', { name: '停止 Studio StackChan 的动作' })).toBeDisabled()
  })

  it('uses the state returned by a refresh instead of overwriting it locally', async () => {
    vi.mocked(api.listDevices).mockResolvedValueOnce([onlineDevice]).mockResolvedValueOnce([offlineDevice])
    vi.mocked(api.stopMotion).mockResolvedValue({ status: 'offline' })
    render(DeviceOverview)

    await fireEvent.click(await screen.findByRole('button', { name: '停止 Studio StackChan 的动作' }))
    expect(await screen.findByText('离线')).toBeVisible()

    await fireEvent.click(screen.getByRole('button', { name: '刷新设备状态' }))

    expect(await screen.findByText('离线')).toBeVisible()
    expect(screen.getByRole('button', { name: '停止 Studio StackChan 的动作' })).toBeDisabled()
  })
})
