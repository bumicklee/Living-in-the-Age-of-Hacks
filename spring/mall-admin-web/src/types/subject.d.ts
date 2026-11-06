/** 기획전 엔티티 */
export type CmsSubject = {
  /** ID */
  id?: number
  /** 카테고리ID */
  categoryId?: number
  /** 제목 */
  title?: string
  /** 기획전 대표 이미지 */
  pic?: string
  /** 연관 제품 수 */
  productCount?: number
  /** 추천 상태 */
  recommendStatus?: number
  /** 생성 시간 */
  createTime?: string
  /** 즐겨찾기 수 */
  collectCount?: number
  /** 조회 수 */
  readCount?: number
  /** 댓글 수 */
  commentCount?: number
  /** 앨범 이미지는 쉼표로 구분 */
  albumPics?: string
  /** 설명 */
  description?: string
  /** 표시 상태:0->표시 안 함;1->표시 */
  showStatus?: number
  /** 공유 수 */
  forwardCount?: number
  /** 기획전 카테고리 이름 */
  categoryName?: string
  /** 내용 */
  content?: string
}
