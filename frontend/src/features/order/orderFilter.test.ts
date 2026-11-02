import { describe, expect, it } from 'vitest'
import { ORDER_STATUS_FILTERS, statusFilterToQuery, toOrdersQuery } from './orderFilter'

type OrdersFilter = Parameters<typeof toOrdersQuery>[0]

describe('toOrdersQuery', () => {
  it.each<[OrdersFilter, string]>([
    [{}, ''],
    [{ statuses: [] }, ''],
    [{ sides: [] }, ''],
    [{ sides: [], statuses: [] }, ''],
    [{ sides: undefined, statuses: undefined }, ''],
  ])('조건이 없거나 비어 있으면(%j) 빈 문자열을 돌려준다', (filter, expected) => {
    expect(toOrdersQuery(filter)).toBe(expected)
  })

  it.each<[OrdersFilter, string]>([
    [{ statuses: ['ACCEPTED'] }, '?status=ACCEPTED'],
    [{ statuses: ['CANCELED'] }, '?status=CANCELED'],
    [{ sides: ['BUY'] }, '?side=BUY'],
    [{ sides: ['BUY'], statuses: ['CANCELED'] }, '?side=BUY&status=CANCELED'],
  ])('%j는 %s로 바꾼다', (filter, expected) => {
    expect(toOrdersQuery(filter)).toBe(expected)
  })

  it('값이 여러 개면 쉼표로 잇고 쉼표를 인코딩하지 않는다', () => {
    expect(toOrdersQuery({ statuses: ['ACCEPTED', 'CANCELED'] })).toBe('?status=ACCEPTED,CANCELED')
    expect(toOrdersQuery({ sides: ['BUY', 'SELL'] })).toBe('?side=BUY,SELL')
    expect(toOrdersQuery({ sides: ['BUY', 'SELL'], statuses: ['ACCEPTED', 'CANCELED'] })).toBe(
      '?side=BUY,SELL&status=ACCEPTED,CANCELED',
    )
  })

  it('객체의 키 순서와 상관없이 side 다음 status 순서로 붙인다', () => {
    expect(toOrdersQuery({ statuses: ['CANCELED'], sides: ['BUY'] })).toBe('?side=BUY&status=CANCELED')
  })

  it('한쪽만 비어 있으면 그 파라미터는 빼서 빈 값(400)을 만들지 않는다', () => {
    expect(toOrdersQuery({ sides: [], statuses: ['ACCEPTED'] })).toBe('?status=ACCEPTED')
    expect(toOrdersQuery({ sides: ['BUY'], statuses: [] })).toBe('?side=BUY')
  })
})

describe('statusFilterToQuery', () => {
  it.each([
    ['ALL', ''],
    ['ACCEPTED', '?status=ACCEPTED'],
    ['CANCELED', '?status=CANCELED'],
  ] as const)('%s는 "%s"로 바꾼다', (filter, expected) => {
    expect(statusFilterToQuery(filter)).toBe(expected)
  })
})

describe('ORDER_STATUS_FILTERS', () => {
  it('전체 / 접수 / 취소 순서로 탭을 정의한다', () => {
    expect(ORDER_STATUS_FILTERS).toEqual([
      { value: 'ALL', label: '전체' },
      { value: 'ACCEPTED', label: '접수' },
      { value: 'CANCELED', label: '취소' },
    ])
  })

  it('모든 탭 값이 서버가 받는 쿼리로 바뀐다', () => {
    for (const { value } of ORDER_STATUS_FILTERS) {
      const expected = value === 'ALL' ? '' : toOrdersQuery({ statuses: [value] })
      expect(statusFilterToQuery(value)).toBe(expected)
    }
  })
})
