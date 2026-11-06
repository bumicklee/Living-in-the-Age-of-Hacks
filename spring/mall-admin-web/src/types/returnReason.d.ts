/** 반품 사유 */
export type OmsOrderReturnReason = {
  /** ID */
  id?: number
  /** 반품 유형 */
  name: string
  /** 정렬 */
  sort: number
  /** 상태:0->사용 안 함;1->사용 */
  status: number
  /** 추가 시간 */
  createTime?: string
}
