import type { PageParam } from './common'

/** 홈 신상품 추천 엔티티 */
export type SmsHomeNewProduct = {
  /** ID */
  id?: number
  /** 상품ID */
  productId: number
  /** 상품명 */
  productName: string
  /** 추천 상태:0->미추천;1->추천 중 */
  recommendStatus?: number
  /** 정렬 */
  sort?: number
}

/** 홈 신상품 추천 목록 조회 파라미터 */
export type NewProductQueryParam = PageParam & {
  /** 상품명 */
  productName?: string
  /** 추천 상태:0->미추천;1->추천 중 */
  recommendStatus?: number
}
