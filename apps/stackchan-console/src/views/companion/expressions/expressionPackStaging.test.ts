import { describe, expect, it } from 'vitest'
import { expressionStateFromFilename } from './expressionPackStaging'

describe('expression pack staging', () => {
  it.each([
    ['idle.png', 'idle'],
    ['LISTENING.PNG', 'listening'],
    ['no-speech.png', 'no_speech'],
    ['folder/no_speech.png', 'no_speech'],
    ['处理中.png', 'processing'],
    ['播报.png', 'speaking'],
    ['异常.png', 'error'],
  ])('maps %s to %s', (filename, expected) => {
    expect(expressionStateFromFilename(filename)).toBe(expected)
  })

  it('ignores unrelated files', () => {
    expect(expressionStateFromFilename('readme.txt')).toBeUndefined()
    expect(expressionStateFromFilename('happy.png')).toBeUndefined()
  })
})
