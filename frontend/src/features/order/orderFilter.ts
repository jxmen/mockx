import type { OrderSide, OrderStatus } from '../../api/orders'

/** 화면의 상태 필터 탭. ALL은 status 파라미터를 보내지 않는다 */
export type OrderStatusFilter = 'ALL' | OrderStatus

export const ORDER_STATUS_FILTERS: { value: OrderStatusFilter; label: string }[] = [
  { value: 'ALL', label: '전체' },
  { value: 'ACCEPTED', label: '접수' },
  { value: 'CANCELED', label: '취소' },
]

/**
 * 주문 목록 조회 쿼리. 같은 파라미터 안의 값은 쉼표로 이어 OR, side·status끼리는 AND로 서버가 해석한다.
 * 비어 있는 조건은 아예 빼서 빈 값(?status=, 서버는 400)을 보내지 않는다. 순서는 side 다음 status.
 * 쉼표는 서버(Spring)가 그대로 나눠 읽으므로 인코딩하지 않는다. 값은 enum 이름이라 인코딩할 문자가 없다.
 */
export function toOrdersQuery(filter: { statuses?: OrderStatus[]; sides?: OrderSide[] }): string {
  const params: string[] = []
  if (filter.sides !== undefined && filter.sides.length > 0) {
    params.push(`side=${filter.sides.join(',')}`)
  }
  if (filter.statuses !== undefined && filter.statuses.length > 0) {
    params.push(`status=${filter.statuses.join(',')}`)
  }
  return params.length === 0 ? '' : `?${params.join('&')}`
}

/** 상태 탭 하나를 쿼리로 바꾼다. (예: 'ALL' → '', 'ACCEPTED' → '?status=ACCEPTED') */
export function statusFilterToQuery(filter: OrderStatusFilter): string {
  return filter === 'ALL' ? '' : toOrdersQuery({ statuses: [filter] })
}
