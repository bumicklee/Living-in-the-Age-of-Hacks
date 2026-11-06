/** 브랜드 정보 */
export type PmsBrand = {
  /** ID */
  id?: number
  /** 브랜드명 */
  name: string
  /** 초성 */
  firstLetter: string
  /** 정렬 */
  sort: number
  /** 브랜드 제조사 여부:0->아니요;1->예 */
  factoryStatus: number
  /** 표시 상태 */
  showStatus: number
  /** 제품 수 */
  productCount?: number
  /** 제품 리뷰 수 */
  productCommentCount?: number
  /** 브랜드logo */
  logo: string
  /** 전용관 대형 이미지 */
  bigPic: string
  /** 브랜드 스토리 */
  brandStory: string
}
