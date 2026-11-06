import type { PageParam } from './common'
import type { PmsProduct } from './product'

/** 타임 세일 이벤트 정보 */
export type SmsFlashPromotion = {
  /** ID */
  id?: number
  /** 타임 세일 시간대 이름 */
  title: string
  /** 시작일 */
  startDate?: string
  /** 종료 날짜 */
  endDate?: string
  /** 판매 상태 */
  status: number
  /** 생성 시간 */
  createTime?: string
}

/** 타임 세일 시간대 정보 */
export type SmsFlashPromotionSession = {
  /** 번호 */
  id?: number
  /** 회차명 */
  name: string
  /** 일일 시작 시간 */
  startTime?: string
  /** 일일 종료 시간 */
  endTime?: string
  /** 사용 상태:0->사용 안 함;1->사용 */
  status: number
  /** 생성 시간 */
  createTime?: string
  /** 상품 수량 */
  productCount?: number
}

/** 타임 세일 이벤트 상품 관계 정보 */
export type SmsFlashPromotionProductRelation = {
  /** 번호 */
  id?: number
  /** 타임 세일 이벤트ID */
  flashPromotionId: number
  /** 타임 세일 시간대ID */
  flashPromotionSessionId: number
  /** 상품ID */
  productId: number
  /** 타임 세일 가격 */
  flashPromotionPrice?: number
  /** 타임 세일 수량 */
  flashPromotionCount?: number
  /** 1인당 구매 제한 수량 */
  flashPromotionLimit?: number
  /** 정렬 */
  sort?: number
  /** 연관 상품 */
  product?: PmsProduct
}

/** 타임 세일 상품 목록 조회 파라미터 */
export type FlashProductQueryParam = PageParam & {
  /** 타임 세일 이벤트ID */
  flashPromotionId?: number
  /** 타임 세일 회차ID */
  flashPromotionSessionId?: number
}
