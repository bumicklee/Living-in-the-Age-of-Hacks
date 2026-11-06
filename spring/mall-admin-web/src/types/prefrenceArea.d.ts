/** 엄선 영역 정보 */
export type CmsPrefrenceArea = {
  /** ID */
  id?: number
  /** 이름 */
  name: string
  /** 부제목 */
  subTitle: string
  /** 정렬 */
  sort: number
  /** 표시 상태:0->표시 안 함;1->표시 */
  showStatus: number
  /** 표시 이미지 */
  pic?: string
}
