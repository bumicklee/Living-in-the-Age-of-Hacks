/** 회원 등급 정보 */
export type UmsMemberLevel = {
  /** ID */
  id?: number
  /** 등급 이름 */
  name: string
  /** 성장 포인트 */
  growthPoint: number
  /** 기본 등급 여부:0->아니요;1->예 */
  defaultStatus: number
  /** 무료 배송 기준 */
  freeFreightPoint: number
  /** 리뷰 1건당 획득 성장 포인트 */
  commentGrowthPoint: number
  /** 무료 배송 혜택 여부 */
  priviledgeFreeFreight: number
  /** 출석 체크 혜택 여부 */
  priviledgeSignIn: number
  /** 리뷰 작성 보상 혜택 여부 */
  priviledgeComment: number
  /** 전용 이벤트 혜택 여부 */
  priviledgePromotion: number
  /** 회원 가격 혜택 여부 */
  priviledgeMemberPrice: number
  /** 생일 혜택 여부 */
  priviledgeBirthday: number
  /** 메모 */
  note: string
}
