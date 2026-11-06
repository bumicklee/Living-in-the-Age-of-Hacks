/** 회사 배송지 */
export type OmsCompanyAddress = {
  /** ID */
  id?: number
  /** 주소명 */
  addressName: string
  /** 기본 발송지:0->아니요;1->예 */
  sendStatus: number
  /** 기본 배송지 여부:0->아니요;1->예 */
  receiveStatus: number
  /** 발송인/수령인 이름 */
  name: string
  /** 수령인 전화번호 */
  phone: string
  /** 시/직할시 */
  province: string
  /** 시 */
  city: string
  /** 구 */
  region: string
  /** 상세 주소 */
  detailAddress: string
}
