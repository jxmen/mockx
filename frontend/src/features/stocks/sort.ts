import { toSortParam, type SortDirection, type StockSortField } from '../../api/stocks'

export type SortOption = {
  label: string
  field: StockSortField
  direction: SortDirection
}

/** 화면에서 고르는 정렬. 첫 번째는 서버 기본값(id 오름차순)과 같다 */
export const SORT_OPTIONS: SortOption[] = [
  { label: '등록순', field: 'id', direction: 'asc' },
  { label: '등록 역순', field: 'id', direction: 'desc' },
  { label: '티커 A→Z', field: 'symbol', direction: 'asc' },
  { label: '티커 Z→A', field: 'symbol', direction: 'desc' },
]

/** 선택한 정렬을 sort 쿼리 값들로 바꾼다. 기본 정렬이면 sort를 보내지 않는다 */
export function toSorts(option: SortOption): string[] {
  return option === SORT_OPTIONS[0] ? [] : [toSortParam(option.field, option.direction)]
}

/**
 * 개발 모드에서 직접 입력한 sort 값. 공백으로 나눠 여러 개를 보낸다. (예: "symbol id,desc" → sort=symbol&sort=id,desc)
 * 서버 정렬 검증(400 INVALID_INPUT)을 확인하는 용도라 값 자체는 검사하지 않는다.
 */
export function parseRawSorts(input: string): string[] {
  return input.split(/\s+/).filter((value) => value !== '')
}

/** 서버가 400 INVALID_INPUT으로 거절하는 sort 예시. StockE2ETest의 케이스와 같다 */
export const INVALID_SORT_EXAMPLES = ['createdAt', 'name', 'price', 'symbol,up', 'symbol id,desc']
