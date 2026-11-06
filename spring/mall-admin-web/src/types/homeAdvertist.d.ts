import type { PageParam } from './common'

/** 홈 광고 엔티티 */
export type SmsHomeAdvertise = {
  /** ID */
  id?: number
  /** 광고명 */
  name?: string
  /** 배너 위치:0->PC홈 배너;1->app홈 배너 */
  type?: number
  /** 이미지 */
  pic?: string
  /** 시작 시간 */
  startTime?: string
  /** 종료 시간 */
  endTime?: string
  /** 판매 상태:0->오프라인;1->온라인 */
  status?: number
  /** 클릭 수 */
  clickCount?: number
  /** 주문 수 */
  orderCount?: number
  /** 링크 주소 */
  url?: string
  /** 메모 */
  note?: string
  /** 정렬 */
  sort?: number
}

/** 홈 광고 목록 조회 파라미터 */
export type HomeAdvertiseQueryParam = PageParam & {
  /** 광고명 */
  name?: string
  /** 배너 위치:0->PC홈 배너;1->app홈 배너 */
  type?: number
  /** 종료 시간 */
  endTime?: string
}
