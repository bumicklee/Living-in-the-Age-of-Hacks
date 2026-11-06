/** 상품 카테고리별 속성 정보 */
export type ProductAttrInfo = {
  /** 상품 속성ID */
  attributeId: number
  /** 상품 속성 카테고리ID */
  attributeCategoryId: number
}

/** 상품 속성 엔티티 */
export type PmsProductAttribute = {
  /** ID */
  id?: number
  /** 상품 속성 카테고리ID */
  productAttributeCategoryId?: number
  /** 속성명 */
  name: string
  /** 속성 선택 유형:0->고유;1->단일 선택;2->다중 선택 */
  selectType?: number
  /** 속성 입력 방식:0->직접 입력;1->목록에서 선택 */
  inputType?: number
  /** 선택값 목록, 쉼표로 구분 */
  inputList?: string
  /** 정렬 필드: 가장 높은 항목은 이미지를 별도로 업로드 가능 */
  sort?: number
  /** 카테고리 필터 스타일:0->일반;1->색상 */
  filterType?: number
  /** 검색 유형;0->검색 불필요;1->키워드 검색;2->범위 검색 */
  searchType?: number
  /** 속성이 같은 상품 연관 여부;0->연관 안 함;1->연관 */
  relatedStatus?: number
  /** 직접 추가 지원 여부;0->지원 안 함;1->지원 */
  handAddStatus?: number
  /** 속성 유형;0->규격;1->파라미터 */
  type?: number
}

/** 상품 속성 카테고리 엔티티 */
export type PmsProductAttributeCategory = {
  /** 속성ID */
  id?: number
  /** 속성명 */
  name: string
  /** 속성 수량 */
  attributeCount?: number
  /** 파라미터 수량 */
  paramCount?: number
}

/** 상품 속성 카테고리 엔티티 확장 */
export type PmsProductAttributeCategoryExt = PmsProductAttributeCategory & {
  /** 상품 속성 카테고리 목록 */
  productAttributeList?: PmsProductAttribute[]
}

/** 상품 속성값 */
export type PmsProductAttributeValue = {
  /** ID */
  id?: number
  /** 상품ID */
  productId?: number
  /** 상품 속성ID */
  productAttributeId?: number
  /** 규격 또는 파라미터 값을 직접 추가, 파라미터는 단일 값, 규격이 여러 개면 쉼표로 구분 */
  value?: string
}

/** 상품 규격 정보 */
export interface ProductAttrVo extends Pick<
  PmsProductAttribute,
  'id' | 'name' | 'handAddStatus' | 'inputList'
> {
  /** 선택된 값 */
  values?: string[]
  /** 선택 항목 */
  options?: string[]
}

/** 상품 파라미터 정보 */
export interface ProductParamVo extends Pick<
  PmsProductAttribute,
  'id' | 'name' | 'inputType' | 'inputList'
> {
  /** 파라미터 값 */
  value?: string
}

/** 상품 속성 이미지 */
export type ProductAttrPicVo = {
  /** 이름 */
  name?: string
  /** 이미지 주소 */
  pic?: string
}
