import type { PageParam } from './common'

/** 주문 정보 */
export type OmsOrder = {
  /** 주문id */
  id: number
  /** 회원id */
  memberId?: number
  /** 쿠폰id */
  couponId?: number
  /** 주문 번호 */
  orderSn?: string
  /** 제출 시간 */
  createTime: string
  /** 사용자 계정 */
  memberUsername?: string
  /** 주문 총 금액 */
  totalAmount: number
  /** 결제 예정 금액(실제 결제 금액) */
  payAmount: number
  /** 배송비 금액 */
  freightAmount: number
  /** 프로모션 할인 금액(프로모션가, 금액 할인, 단계별 가격) */
  promotionAmount?: number
  /** 포인트 차감 금액 */
  integrationAmount?: number
  /** 쿠폰 할인 금액 */
  couponAmount?: number
  /** 관리자가 관리자 화면에서 주문에 적용한 할인 금액 조정 */
  discountAmount: number
  /** 결제 수단:0->미결제;1->알리페이;2->위챗 */
  payType: number
  /** 주문 출처:0->PC주문;1->app주문 */
  sourceType: number
  /** 주문 상태:0->결제 대기;1->배송 대기;2->발송됨;3->완료;4->닫힘;5->무효 주문 */
  status: number
  /** 주문 유형:0->정상 주문;1->타임 세일 주문 */
  orderType: number
  /** 택배사(배송 방식) */
  deliveryCompany?: string
  /** 운송장 번호 */
  deliverySn?: string
  /** 자동 확인 시간(일) */
  autoConfirmDay?: number
  /** 획득 가능한 포인트 */
  integration?: number
  /** 획득 가능한 성장 포인트 */
  growth?: number
  /** 이벤트 정보 */
  promotionInfo: string
  /** 영수증 유형:0->인보이스 미발행;1->전자 영수증;2->종이 영수증 */
  billType?: number
  /** 영수증 발행 대상 */
  billHeader?: string
  /** 영수증 내용 */
  billContent?: string
  /** 세금계산서 수령인 전화번호 */
  billReceiverPhone?: string
  /** 세금계산서 수령인 이메일 */
  billReceiverEmail?: string
  /** 수령인 이름 */
  receiverName?: string
  /** 수령인 전화번호 */
  receiverPhone?: string
  /** 수령인 우편번호 */
  receiverPostCode?: string
  /** 시/도, 직할시 */
  receiverProvince: string
  /** 도시 */
  receiverCity?: string
  /** 구 */
  receiverRegion?: string
  /** 상세 주소 */
  receiverDetailAddress?: string
  /** 주문 메모 */
  note?: string
  /** 수취 확인 상태:0->미확인;1->확인됨 */
  confirmStatus?: number
  /** 삭제 상태:0->삭제 안 됨;1->삭제됨 */
  deleteStatus?: number
  /** 주문 시 사용한 포인트 */
  useIntegration?: number
  /** 결제 시간 */
  paymentTime: string
  /** 발송 시간 */
  deliveryTime: string
  /** 수취 확인 시간 */
  receiveTime: string
  /** 리뷰 작성 시간 */
  commentTime: string
  /** 수정 시간 */
  modifyTime?: string
}

/** 주문 상품 항목 */
export type OmsOrderItem = {
  /** 상품 항목id */
  id: number
  /** 주문id */
  orderId: number
  /** 주문 번호 */
  orderSn: string
  /** 상품id */
  productId: number
  /** 상품 이미지 */
  productPic: string
  /** 상품명 */
  productName: string
  /** 상품 브랜드 */
  productBrand: string
  /** 상품 코드 */
  productSn: string
  /** 판매 가격 */
  productPrice: number
  /** 구매 수량 */
  productQuantity: number
  /** 상품 sku 번호 */
  productSkuId: number
  /** 상품 sku 바코드 */
  productSkuCode: string
  /** 상품 카테고리id */
  productCategoryId: number
  /** 상품 프로모션명 */
  promotionName: string
  /** 상품 프로모션 분배 금액 */
  promotionAmount: number
  /** 쿠폰 할인 분배 금액 */
  couponAmount: number
  /** 포인트 할인 분배 금액 */
  integrationAmount: number
  /** 할인 적용 후 해당 상품의 분할 금액 */
  realAmount: number
  /** 포인트 지급 */
  giftIntegration: number
  /** 성장 포인트 지급 */
  giftGrowth: number
  /** 상품 판매 속성 */
  productAttr: string
}

/** 주문 작업 이력 */
export type OmsOrderOperateHistory = {
  /** 이력id */
  id: number
  /** 주문id */
  orderId: number
  /** 작업자: 사용자; 시스템; 관리자 */
  operateMan: string
  /** 작업 시간 */
  createTime: string
  /** 주문 상태:0->결제 대기;1->배송 대기;2->발송됨;3->완료;4->닫힘;5->무효 주문 */
  orderStatus: number
  /** 메모 */
  note: string
}

/** 주문 목록 조회 파라미터 */
export type OrderQueryParam = PageParam & {
  /** 주문 번호 */
  orderSn?: string
  /** 수령인 이름/번호 */
  receiverKeyword?: string
  /** 주문 상태:0->결제 대기;1->배송 대기;2->발송됨;3->완료;4->닫힘;5->무효 주문 */
  status?: number
  /** 주문 유형:0->정상 주문;1->타임 세일 주문 */
  orderType?: number
  /** 주문 출처:0->PC주문;1->app주문 */
  sourceType?: number
  /** 주문 제출 시간 */
  createTime?: string
}

/** 주문 발송 파라미터 */
export type OmsOrderDeliveryParam = {
  /** 주문id */
  orderId: number
  /** 택배사 */
  deliveryCompany?: string
  /** 운송장 번호 */
  deliverySn?: string
}

/** 주문 상세 정보 */
export type OmsOrderDetail = OmsOrder & {
  /** 주문 상품 목록 */
  orderItemList: OmsOrderItem[]
  /** 주문 작업 기록 목록 */
  historyList: OmsOrderOperateHistory[]
}

/** 주문 수령인 정보 파라미터 */
export type OmsReceiverInfoParam = {
  /** 주문ID */
  orderId: number
  /** 수령인 이름 */
  receiverName?: string
  /** 수령인 전화번호 */
  receiverPhone?: string
  /** 수령인 우편번호 */
  receiverPostCode?: string
  /** 상세 주소 */
  receiverDetailAddress?: string
  /** 시/도, 직할시 */
  receiverProvince?: string
  /** 도시 */
  receiverCity?: string
  /** 구 */
  receiverRegion?: string
  /** 주문 상태:0->결제 대기;1->배송 대기;2->발송됨;3->완료;4->닫힘;5->무효 주문 */
  status: number
}

/** 주문 비용 정보 파라미터 */
export type OmsMoneyInfoParam = {
  /** 주문ID */
  orderId: number
  /** 배송비 금액 */
  freightAmount: number
  /** 관리자가 관리자 화면에서 주문에 적용한 할인 금액 조정 */
  discountAmount: number
  /** 주문 상태:0->결제 대기;1->배송 대기;2->발송됨;3->완료;4->닫힘;5->무효 주문 */
  status: number
}
