/** 메뉴 */
export type UmsMenu = {
  /** 고유ID */
  id?: number
  /** 상위ID */
  parentId: number
  /** 생성 시간 */
  createTime?: string
  /** 메뉴 이름 */
  title: string
  /** 메뉴 단계 */
  level?: number
  /** 메뉴 정렬 */
  sort: number
  /** 프런트엔드 이름 */
  name: string
  /** 프런트엔드 아이콘 */
  icon: string
  /** 프런트엔드에서 숨김 */
  hidden: number
}

/** 관리자 메뉴 노드 래핑 */
export type UmsMenuNode = UmsMenu & {
  /** 하위 메뉴 */
  children: UmsMenu[]
}
