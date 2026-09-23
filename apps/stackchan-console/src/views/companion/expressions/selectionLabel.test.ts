import type { DeviceExpressionPack, ExpressionPack } from '@/api/modules/expressionPacks'
import { describe, expect, it } from 'vitest'
import { currentExpressionLabel } from './selectionLabel'

const selection: DeviceExpressionPack = {
  deviceId: 'robot',
  enabled: true,
  failureCode: null,
  installedAt: null,
  packId: 'pack',
  status: 'READY',
  updatedAt: '2026-09-20T00:00:00Z',
}
const pack = { id: 'pack', name: '小伙伴', packType: 'STATIC_PNG' } as ExpressionPack

describe('device-reported expression label', () => {
  it('does not invent a default face before the selection is available', () => {
    expect(currentExpressionLabel(null, undefined, true)).toBe('未获取')
  })
  it.each(['READY', 'INSTALLING', 'FAILED'] as const)('does not present the requested pack as active when %s', (status) => {
    expect(currentExpressionLabel({ ...selection, status }, pack, true)).not.toContain(pack.name)
  })
  it('shows the pack only after the device reports activation', () => {
    expect(currentExpressionLabel({ ...selection, status: 'ACTIVE' }, pack, true)).toBe(pack.name)
    expect(currentExpressionLabel({ ...selection, enabled: false, status: 'DISABLED' }, pack, true)).toBe('内置动态球体')
  })
})
