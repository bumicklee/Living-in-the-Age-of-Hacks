import type { PageParam } from './common'
import type { OmsCompanyAddress } from './companyAddress'

/** 주문 반품 신청 */
export type OmsOrderReturnApply = {
  /** 기본 키ID */
  id: number
  /** 주문id */
  orderId: number
  /** 배송지 테이블id */
  companyAddressId: number
  /** 반품 상품id */
  productId: number
  /** 주문 번호 */
  orderSn: string
  /** 신청 시간 */
  createTime: string
  /** 회원 사용자 이름 */
  memberUsername: string
  /** 환불 금액 */
  returnAmount: number
  /** 반품인 이름 */
  returnName: string
  /** 반품인 전화번호 */
  returnPhone: string
  /** 신청 상태:0->처리 대기;1->반품 중;2->완료;3->거절됨 */
  status: number
  /** 처리 시간 */
  handleTime: string
  /** 상품 이미지 */
  productPic: string
  /** 상품명 */
  productName: string
  /** 상품 브랜드 */
  productBrand: string
  /** 상품 판매 속성: 색상: 레드; 사이즈:xl; */
  productAttr: string
  /** 반품 수량 */
  productCount: number
  /** 상품 단가 */
  productPrice: number
  /** 상품 실결제 단가 */
  productRealPrice: number
  /** 사유 */
  reason: string
  /** 설명 */
  description: string
  /** 증빙 이미지, 쉼표로 구분 */
  proofPics: string
  /** 메모 처리 */
  handleNote: string
  /** 처리 담당자 */
  handleMan: string
  /** 수령인 */
  receiveMan: string
  /** 수령 시간 */
  receiveTime: string
  /** 배송 메모 */
  receiveNote: string
}

/** 반품 신청 조회 파라미터 */
export type ReturnApplyQueryParam = PageParam & {
  /** 서비스 요청 번호 */
  id?: number
  /** 수령인 이름/번호 */
  receiverKeyword?: string
  /** 신청 상태:0->처리 대기;1->반품 중;2->완료;3->거절됨 */
  status?: number
  /** 신청 시간 */
  createTime?: string
  /** 처리 담당자 */
  handleMan?: string
  /** 처리 시간 */
  handleTime?: string
}

/** 주문 반품 신청 결과 래퍼 */
export type OmsOrderReturnApplyResult = OmsOrderReturnApply & {
  /** 회사 수령지 */
  companyAddress: OmsCompanyAddress
}

/** 반품 신청 상태 업데이트 파라미터 */
export type OmsUpdateStatusParam = {
  /** 서비스 요청 번호 */
  id: number
  /** 배송지 연관id */
  companyAddressId: number
  /** 환불 금액 확인 */
  returnAmount: number
  /** 메모 처리 */
  handleNote: string
  /** 처리자 */
  handleMan: string
  /** 배송 메모 */
  receiveNote: string
  /** 수령인 */
  receiveMan: string
  /** 신청 상태:1->반품 중;2->완료;3->거절됨 */
  status: number
}
