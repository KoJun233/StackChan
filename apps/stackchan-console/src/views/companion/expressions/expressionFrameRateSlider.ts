export const expressionFpsMinimum = 1
export const expressionFpsMaximum = 60

function normalizeFps(value: number | undefined, fallback: number): number {
  return Math.max(expressionFpsMinimum, Math.min(expressionFpsMaximum, Math.round(value ?? fallback)))
}

export function frameRateRangeToSlider(minimum: number, maximum: number): number[] {
  const first = normalizeFps(minimum, expressionFpsMinimum)
  return [first, Math.max(first, normalizeFps(maximum, first))]
}

export function sliderToFrameRateRange(values: number[]): [number, number] {
  const first = normalizeFps(values[0], expressionFpsMinimum)
  return [first, Math.max(first, normalizeFps(values[1], first))]
}
