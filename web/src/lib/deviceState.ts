import type { Device } from './api'

export type DashboardDevice = Device

export function currentListDevices(devices: Device[]): DashboardDevice[] {
  return devices
}
