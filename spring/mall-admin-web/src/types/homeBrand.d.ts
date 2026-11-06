/** 홈 브랜드 */
export type SmsHomeBrand = {
  /** ID */
  id?: number
  /** 브랜드ID */
  brandId: number
  /** 브랜드명 */
  brandName: string
  /** 추천 상태:0->미추천;1->추천 중 */
  recommendStatus?: number
  /** 정렬 */
  sort?: number
}

/** 홈 브랜드 목록 조회 파라미터 */
export type HomeBrandQueryParam = PageParam & {
  /** 브랜드명 */
  brandName?: string
  /** 추천 상태:0->미추천;1->추천 중 */
  recommendStatus?: number
}
