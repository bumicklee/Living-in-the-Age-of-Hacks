import type { PageParam } from './common'
import type { PmsProductAttributeValue } from './productAttr'
import type { PmsSkuStock } from './skuStock'

export type PmsProduct = {
  /** ID */
  id?: number
  /** 브랜드ID */
  brandId?: number
  /** 제품 카테고리ID */
  productCategoryId?: number
  /** 배송비 템플릿ID */
  feightTemplateId?: number
  /** 속성 카테고리ID */
  productAttributeCategoryId?: number
  /** 이름 */
  name: string
  /** 이미지 */
  pic?: string
  /** 상품 번호 */
  productSn: string
  /** 삭제 상태:0->삭제 안 됨;1->삭제됨 */
  deleteStatus?: number
  /** 판매 상태:0->판매 중지;1->판매 중 */
  publishStatus?: number
  /** 신상품 상태:0->신상품 아님;1->신상품 */
  newStatus?: number
  /** 추천 상태;0->추천 안 함;1->추천 */
  recommandStatus?: number
  /** 심사 상태:0->미심사;1->심사 통과 */
  verifyStatus?: number
  /** 정렬 */
  sort?: number
  /** 판매량 */
  sale?: number
  /** 가격 */
  price?: number
  /** 프로모션 가격 */
  promotionPrice?: number
  /** 지급된 성장 포인트 */
  giftGrowth?: number
  /** 지급된 포인트 */
  giftPoint?: number
  /** 사용 가능한 포인트 수 제한 */
  usePointLimit?: number
  /** 부제목 */
  subTitle?: string
  /** 시장가 */
  originalPrice?: number
  /** 재고 */
  stock?: number
  /** 재고 경고 기준값 */
  lowStock?: number
  /** 단위 */
  unit?: string
  /** 상품 중량, 기본 단위는 그램 */
  weight?: number
  /** 예고 상품 여부:0->아니요;1->예 */
  previewStatus?: number
  /** 쉼표로 구분된 제품 서비스:1->걱정 없는 반품;2->빠른 환불;3->무료 배송 */
  serviceIds?: string
  /** 키워드 */
  keywords?: string
  /** 메모 */
  note?: string
  /** 앨범 이미지, 상품 이미지 포함 최대 5장, 쉼표로 구분 */
  albumPics?: string
  /** 상세 제목 */
  detailTitle?: string
  /** 프로모션 시작 시간 */
  promotionStartTime?: string
  /** 프로모션 종료 시간 */
  promotionEndTime?: string
  /** 이벤트 구매 제한 수량 */
  promotionPerLimit?: number
  /** 프로모션 유형:0->프로모션이 없으면 정가 사용;1->프로모션 가격 적용;2->회원 가격 적용;3->단계별 가격 적용;4->금액 할인 가격 적용;5->타임 세일 */
  promotionType?: number
  /** 브랜드명 */
  brandName?: string
  /** 상품 카테고리명 */
  productCategoryName?: string
  /** 상품 설명 */
  description?: string
  /** 상세 설명 */
  detailDesc?: string
  /** 제품 상세 웹 페이지 내용 */
  detailHtml?: string
  /** 모바일 웹 상세 */
  detailMobileHtml?: string
}

/** 상품 목록 조회 파라미터 */
export type ProductQueryParam = PageParam & {
  /** 판매 상태 */
  publishStatus?: number
  /** 심사 상태 */
  verifyStatus?: number
  /** 상품 코드 */
  productSn?: string
  /** 상품 카테고리 번호 */
  productCategoryId?: number
  /** 상품 브랜드 번호 */
  brandId?: number
}

/** 상품 생성·수정 요청 파라미터 */
export type PmsProductParam = PmsProduct & {
  /** 상품 수량별 가격 설정 */
  productLadderList?: PmsProductLadder[]
  /** 상품 금액 할인 가격 설정 */
  productFullReductionList?: PmsProductFullReduction[]
  /** 상품 회원 가격 설정 */
  memberPriceList?: PmsMemberPrice[]
  /** 상품의 sku 재고 정보 */
  skuStockList?: PmsSkuStock[]
  /** 상품 파라미터 및 사용자 정의 규격 속성 */
  productAttributeValueList?: PmsProductAttributeValue[]
  /** 기획전과 상품 관계  */
  subjectProductRelationList?: CmsSubjectProductRelation[]
  /** 엄선 전용관과 상품의 관계  */
  prefrenceAreaProductRelationList?: CmsPrefrenceAreaProductRelation[]
  /** 상품이 선택한 카테고리의 부모id */
  cateParentId?: number
  /** 타임 세일 상품 수 */
  flashPromotionCount: number
  /** 타임 세일 이벤트ID */
  flashPromotionId: number
  /** 타임 세일 상품 가격 */
  flashPromotionPrice: number
  /** 타임 세일 정렬 가격 */
  flashPromotionSort: number
}

/** 상품 수량별 가격 */
export type PmsProductLadder = {
  /** ID */
  id?: number
  /** 상품ID */
  productId?: number
  /** 충족한 상품 수량 */
  count: number
  /** 할인 */
  discount?: number
  /** 할인 후 가격 */
  price?: number
}

/** 상품 금액 할인 가격 설정 */
export type PmsProductFullReduction = {
  /** ID */
  id?: number
  /** 상품ID */
  productId?: number
  /** 충족 금액 */
  fullPrice?: number
  /** 할인 금액 */
  reducePrice?: number
}

/** 상품 회원 가격 */
export type PmsMemberPrice = {
  /** ID */
  id?: number
  /** 상품ID */
  productId?: number
  /** 회원 등급ID */
  memberLevelId?: number
  /** 회원 가격 */
  memberPrice?: number
  /** 회원 등급 이름 */
  memberLevelName?: string
}

/** 기획전과 상품 관계 */
export type CmsSubjectProductRelation = {
  /** ID */
  id?: number
  /** 기획전ID */
  subjectId?: number
  /** 상품ID */
  productId?: number
}

/** 엄선 전용관과 상품의 관계 */
export type CmsPrefrenceAreaProductRelation = {
  /** ID */
  id?: number
  /** 엄선 전용관ID */
  prefrenceAreaId?: number
  /** 상품ID */
  productId?: number
}
