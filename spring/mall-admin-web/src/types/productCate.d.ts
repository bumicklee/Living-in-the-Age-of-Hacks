/** 상품 카테고리 정보 */
export type PmsProductCategory = {
  /** ID */
  id?: number
  /** 상위 카테고리 번호: 0은 1단계 카테고리를 의미 */
  parentId: number
  /** 카테고리 이름 */
  name: string
  /** 카테고리 레벨:0->1급;1->2급 */
  level?: number
  /** 상품 수량 */
  productCount?: number
  /** 상품 단위 */
  productUnit?: string
  /** 내비게이션 바에 표시 여부:0->표시 안 함;1->표시 */
  navStatus: number
  /** 표시 상태:0->표시 안 함;1->표시 */
  showStatus: number
  /** 정렬 */
  sort?: number
  /** 아이콘 */
  icon?: string
  /** 키워드 */
  keywords?: string
  /** 설명 */
  description?: string
  /** 필터 속성 ID 목록, 이후 추가됨 */
  productAttributeIdList?: number[]
}

/** 상품 카테고리 정보 확장 */
export type PmsProductCategoryExt = PmsProductCategory & {
  /** 하위 카테고리 */
  children?: PmsProductCategory[]
}
