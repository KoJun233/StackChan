import { describe, expect, it } from 'vitest'
import source from './index.vue?raw'

describe('account dropdown logout boundary', () => {
  it('consumes the asynchronous logout rejection from the synchronous dropdown handle', () => {
    expect(source).toMatch(/function handleLogout\(\)[\s\S]*void appAccountStore\.logout\([\s\S]*\.catch\(\(\) => \{\}\)/)
    expect(source).toContain('handle: handleLogout')
  })
})
