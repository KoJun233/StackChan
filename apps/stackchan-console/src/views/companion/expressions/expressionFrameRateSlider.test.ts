import { describe, expect, it } from 'vitest'
import { frameRateRangeToSlider, sliderToFrameRateRange } from './expressionFrameRateSlider'

describe('expression frame-rate slider', () => {
  it('preserves every integer frame rate in the supported range', () => {
    expect(frameRateRangeToSlider(17, 53)).toEqual([17, 53])
    expect(sliderToFrameRateRange([24, 57])).toEqual([24, 57])
  })

  it('rounds and clamps malformed values to 1 through 60', () => {
    expect(sliderToFrameRateRange([-3, 90])).toEqual([1, 60])
    expect(sliderToFrameRateRange([52.6, 12])).toEqual([53, 53])
  })
})
