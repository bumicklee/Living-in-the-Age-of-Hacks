import type { PageParam } from './common'

/** 쿠폰 정보 */
export type SmsCoupon = {
  /** ID */
  id?: number
  /** 쿠폰 유형;0->전 상품 증정 쿠폰;1->회원 쿠폰 증정;2->구매 시 쿠폰 지급;3->가입 축하 쿠폰 */
  type: number
  /** 쿠폰 이름 */
  name: string
  /** 사용 플랫폼:0->전체;1->이동;2->PC */
  platform: number
  /** 수량 */
  count?: number
  /** 금액 */
  amount: number
  /** 1인당 발급 제한 매수 */
  perLimit: number
  /** 사용 조건; 0은 조건 없음을 의미 */
  minPoint?: number
  /** 시작 시간 */
  startTime?: string
  /** 종료 시간 */
  endTime?: string
  /** 사용 유형:0->전 상품 공용;1->지정 카테고리;2->지정 상품 */
  useType: number
  /** 메모 */
  note?: string
  /** 발행 수량 */
  publishCount?: number
  /** 사용 수량 */
  useCount?: number
  /** 수령 수량 */
  receiveCount?: number
  /** 수령 가능 날짜 */
  enableTime?: string
  /** 쿠폰 코드 */
  code?: string
  /** 수령 가능한 회원 유형:0->기간 제한 없음 */
  memberLevel?: number
}

/** 쿠폰 확장 */
export type SmsCouponExt = SmsCoupon & {
  /** 쿠폰에 바인딩된 상품 */
  productRelationList?: CouponSelectProductOptionVo[]
  /** 쿠폰에 바인딩된 상품 카테고리 */
  productCategoryRelationList?: CouponProductCateRelationVo[]
}

/** 쿠폰과 상품 카테고리 관계 */
export type CouponProductCateRelationVo = {
  /** 상품 카테고리ID */
  productCategoryId?: number
  /** 상품 카테고리명 */
  productCategoryName?: string
  /** 상위 카테고리 이름 */
  parentCategoryName?: string
}

/** 쿠폰 지정 상품 선택 객체 */
export type CouponSelectProductOptionVo = {
  /** 상품ID */
  productId?: number
  /** 상품명 */
  productName: string
  /** 상품 SN 코드 */
  productSn: string
}

/** 쿠폰 목록 조회 파라미터 */
export type CouponQueryParam = PageParam & {
  /** 조회 이름 */
  name?: string
  /** 쿠폰 유형 */
  type?: number
}

/** 쿠폰 내역 */
export type SmsCouponHistory = {
  /** ID */
  id?: number
  /** 쿠폰ID */
  couponId?: number
  /** 회원ID */
  memberId?: number
  /** 쿠폰 코드 */
  couponCode?: string
  /** 수령인 닉네임 */
  memberNickname?: string
  /** 조회 타입:0->관리자 지급;1->직접 조회 */
  getType?: number
  /** 생성 시간 */
  createTime?: string
  /** 사용 상태:0->미사용;1->사용 완료;2->만료됨 */
  useStatus?: number
  /** 사용 시간 */
  useTime?: string
  /** 주문ID */
  orderId?: number
  /** 주문 번호 */
  orderSn?: string
}

/** 쿠폰 내역 목록 조회 파라미터 */
export type CouponHistoryQueryParam = PageParam & {
  /** 사용 상태:0->미사용;1->사용 완료;2->만료됨 */
  useStatus?: number
  /** 주문 번호 */
  orderSn?: string
  /** 쿠폰ID */
  couponId?: number
}
