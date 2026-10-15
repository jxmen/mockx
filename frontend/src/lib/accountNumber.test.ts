import { describe, expect, it } from 'vitest'
import { formatAccountNumber } from './accountNumber'

describe('formatAccountNumber', () => {
  it('숫자 12자리를 4-4-4로 끊는다', () => {
    expect(formatAccountNumber('100000000016')).toBe('1000-0000-0016')
  })

  it.each(['10000000001', '1000000000160', '1000-0000-0016', ''])('형식이 다른 "%s"는 그대로 돌려준다', (raw) => {
    expect(formatAccountNumber(raw)).toBe(raw)
  })
})
