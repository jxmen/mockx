import { describe, expect, it } from 'vitest'
import { formatWon } from './money'

describe('formatWon', () => {
  it.each([
    [0, '0원'],
    [999, '999원'],
    [1000, '1,000원'],
    [123456789, '123,456,789원'],
  ])('%d를 "%s"로 표시한다', (amount, expected) => {
    expect(formatWon(amount)).toBe(expected)
  })
})
