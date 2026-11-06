/** 주문 설정 정보 */
export type OmsOrderSetting = {
  /** ID */
  id: number
  /** 타임 세일 주문 만료 닫힘 시간(분) */
  flashOrderOvertime?: number
  /** 정상 주문 만료 시간(분) */
  normalOrderOvertime?: number
  /** 발송 후 자동 수령 확인 시간(일) */
  confirmOvertime?: number
  /** 자동 거래 완료 시간, 이후 A/S 신청 불가(일) */
  finishOvertime?: number
  /** 주문 완료 후 자동 리뷰 작성 시간(일) */
  commentOvertime?: number
}
