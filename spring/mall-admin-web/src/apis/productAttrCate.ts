import type { CommonPage, PageParam } from '@/types/common'
import type {
  PmsProductAttributeCategory,
  PmsProductAttributeCategoryExt,
} from '@/types/productAttr'
import http from '@/utils/http'

/**
 * 전체 상품 속성 카테고리 및 하위 속성 조회
 */
export function productAttributeCategoryListWithAttrAPI() {
  return http<PmsProductAttributeCategoryExt[]>({
    url: '/productAttribute/category/list/withAttr',
    method: 'get',
  })
}

/**
 * 모든 상품 속성 카테고리 페이지 조회
 */
export function getProductAttributeCategoryListAPI(params: PageParam) {
  return http<CommonPage<PmsProductAttributeCategory>>({
    url: '/productAttribute/category/list',
    method: 'get',
    params: params,
  })
}

/**
 * 상품 속성 카테고리 추가
 */
export function productAttributeCategoryCreateAPI(name: string) {
  return http({
    url: '/productAttribute/category/create',
    method: 'post',
    params: { name: name },
  })
}

/**
 * 단일 상품 속성 카테고리 삭제
 */
export function productAttributeCategoryDeleteById(id: number) {
  return http({
    url: '/productAttribute/category/delete/' + id,
    method: 'get',
  })
}

/**
 * 상품 속성 카테고리 수정
 */
export function productAttributeCategoryUpdateAPI(id: number, name: string) {
  return http({
    url: '/productAttribute/category/update/' + id,
    method: 'post',
    params: { name: name },
  })
}
