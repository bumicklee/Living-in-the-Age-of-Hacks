/** 역할 정보 */
export type UmsRole = {
  /** ID */
  id?: number
  /** 이름 */
  name: string
  /** 설명 */
  description?: string
  /** 관리자 사용자 수 */
  adminCount?: number
  /** 생성 시간 */
  createTime?: string
  /** 사용 상태:0->사용 안 함;1->사용 */
  status: number
  /** 정렬 */
  sort?: number
}
