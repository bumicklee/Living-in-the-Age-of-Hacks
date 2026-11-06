import type { PageParam } from './common'

/**
 * 홈 기획전 추천
 */
export type SmsHomeRecommendSubject = {
  /** ID */
  id?: number
  /** 기획전ID */
  subjectId: number
  /** 기획전 이름 */
  subjectName: string
  /** 추천 상태:0->미추천;1->추천 중 */
  recommendStatus?: number
  /** 정렬 */
  sort?: number
}

/** 홈 기획전 목록 조회 파라미터 */
export type HomeSubjectQueryParam = PageParam & {
  /** 브랜드명 */
  subjectName?: string
  /** 추천 상태:0->미추천;1->추천 중 */
  recommendStatus?: number
}
