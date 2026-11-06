import type { PageParam } from './common'

/** 리소스 정보 */
export type UmsResource = {
  /** ID */
  id?: number
  /** 생성 시간 */
  createTime?: string
  /** 리소스 이름 */
  name: string
  /** 리소스URL */
  url: string
  /** 설명 */
  description?: string
  /** 리소스 카테고리ID */
  categoryId: number
  /** 선택 여부, 프런트엔드에서 사용 */
  checked?: boolean
}

/** 리소스 카테고리 정보 */
export type UmsResourceCategory = {
  /** ID */
  id?: number
  /** 생성 시간 */
  createTime?: string
  /** 카테고리 이름 */
  name: string
  /** 정렬 */
  sort: number
  /** 선택 여부, 프런트엔드에서 사용 */
  checked?: boolean
}

/** 리소스 목록 조회 파라미터 */
export type ResourceQueryParam = PageParam & {
  /** 리소스 이름 키워드 */
  nameKeyword?: string
  /** 리소스 경로 키워드 */
  urlKeyword?: string
  /** 리소스 카테고리ID */
  categoryId?: number
}
