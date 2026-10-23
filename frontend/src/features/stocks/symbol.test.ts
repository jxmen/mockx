import { describe, expect, it } from 'vitest'
import { stockPath } from '../../api/stocks'
import { isNormalizedByServer } from './symbol'

describe('isNormalizedByServer', () => {
  it.each([
    ['mu', 'MU', true],
    ['sKhY', 'SKHY', true],
    ['MU', 'MU', false],
  ])('%s로 요청해 %s를 받으면 %s', (requested, returned, expected) => {
    expect(isNormalizedByServer(requested, returned)).toBe(expected)
  })
})

describe('stockPath', () => {
  it.each([
    ['MU', '/api/stocks/MU'],
    ['mü', '/api/stocks/m%C3%BC'],
    ['ſpy', '/api/stocks/%C5%BFpy'],
    ['MU ', '/api/stocks/MU%20'],
    ['A/B', '/api/stocks/A%2FB'], // 경로 구분자도 심볼의 일부로 보낸다
  ])('%s → %s', (symbol, expected) => {
    expect(stockPath(symbol)).toBe(expected)
  })
})
