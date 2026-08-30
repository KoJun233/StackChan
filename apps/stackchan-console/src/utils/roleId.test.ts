import { describe, expect, it } from 'vitest'
import { isCompanionRoleId } from './roleId'

describe('companion role ID validation', () => {
  it('accepts the reserved default role ID', () => {
    expect(isCompanionRoleId('00000000-0000-0000-0000-000000000001')).toBe(true)
  })

  it('accepts generated UUID role IDs', () => {
    expect(isCompanionRoleId('b1e75281-5890-4534-a559-e8d905272650')).toBe(true)
  })

  it.each(['', 'role-id', '00000000-0000-0000-0000-00000000001'])(
    'rejects invalid role ID %j',
    (value) => {
      expect(isCompanionRoleId(value)).toBe(false)
    },
  )
})
