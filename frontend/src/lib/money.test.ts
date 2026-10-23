import { describe, expect, it } from 'vitest'
import { formatCents } from './money'

describe('formatCents', () => {
  it.each([
    [0, '$0.00'],
    [1, '$0.01'],
    [99, '$0.99'],
    [100, '$1.00'],
    [187430, '$1,874.30'],
    [1_000_000, '$10,000.00'],
    [12_345_678_901, '$123,456,789.01'],
    [-150, '-$1.50'],
  ])('%d센트를 "%s"로 표시한다', (cents, expected) => {
    expect(formatCents(cents)).toBe(expected)
  })
})
