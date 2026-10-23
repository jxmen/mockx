import { describe, expect, it } from 'vitest'
import { pageTitle } from './pageTitle'

describe('pageTitle', () => {
  it.each([
    ['회원가입', 'mockx | 회원가입'],
    ['가입 완료', 'mockx | 가입 완료'],
    ['내 정보', 'mockx | 내 정보'],
    ['입금', 'mockx | 입금'],
    ['종목', 'mockx | 종목'],
  ])('"%s" 화면은 "%s"로 보여 준다', (page, expected) => {
    expect(pageTitle(page)).toBe(expected)
  })
})
