import { describe, expect, it } from 'vitest'
import { stocksPath } from '../../api/stocks'
import { SORT_OPTIONS, parseRawSorts, toSorts } from './sort'

describe('toSorts', () => {
  it('기본 정렬(등록순)이면 sort를 보내지 않는다', () => {
    expect(toSorts(SORT_OPTIONS[0])).toEqual([])
  })

  it.each([
    ['등록 역순', 'id,desc'],
    ['티커 A→Z', 'symbol,asc'],
    ['티커 Z→A', 'symbol,desc'],
  ])('%s는 sort=%s 하나로 보낸다', (label, expected) => {
    const option = SORT_OPTIONS.find((item) => item.label === label)!
    expect(toSorts(option)).toEqual([expected])
  })
})

describe('parseRawSorts', () => {
  it.each([
    ['', []],
    ['   ', []],
    ['symbol,desc', ['symbol,desc']],
    ['  name ', ['name']],
    ['symbol id,desc', ['symbol', 'id,desc']],
  ])('"%s"는 %j로 나눈다', (input, expected) => {
    expect(parseRawSorts(input)).toEqual(expected)
  })
})

describe('stocksPath', () => {
  it('sort가 없으면 쿼리 없이 보낸다', () => {
    expect(stocksPath([])).toBe('/api/stocks')
  })

  it('sort 하나는 쉼표를 인코딩해 붙인다', () => {
    expect(stocksPath(['symbol,desc'])).toBe('/api/stocks?sort=symbol%2Cdesc')
  })

  it('sort 여러 개는 sort=를 반복한다', () => {
    expect(stocksPath(['symbol', 'id,desc'])).toBe('/api/stocks?sort=symbol&sort=id%2Cdesc')
  })
})
