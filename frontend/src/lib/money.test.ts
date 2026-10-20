import { describe, expect, it } from 'vitest'
import { formatDollar } from './money'

describe('formatDollar', () => {
  it.each([
    [0, '$0'],
    [999, '$999'],
    [1000, '$1,000'],
    [123456789, '$123,456,789'],
  ])('%d를 "%s"로 표시한다', (amount, expected) => {
    expect(formatDollar(amount)).toBe(expected)
  })
})
