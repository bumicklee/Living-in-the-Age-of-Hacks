import type { CommonPage, PageParam } from '@/types/common'
import type { PmsProductCategory, PmsProductCategoryExt } from '@/types/productCate'
import http from '@/utils/http'

/**
 * 모든 1차 카테고리 및 하위 카테고리 조회
 */
export function getProductCategoryListWithChildrenAPI() {
  return http<PmsProductCategoryExt[]>({
    url: '/productCategory/list/withChildren',
    method: 'get',
  })
}

/**
 * 상품 카테고리 페이지 조회
 */
export function getProductCategoryListAPI(parentId: number, params: PageParam) {
  return http<CommonPage<PmsProductCategory>>({
    url: '/productCategory/list/' + parentId,
    method: 'get',
    params: params,
  })
}

/**
 * ID로 상품 카테고리 삭제
 */
export function productCategoryDeleteByIdAPI(id: number) {
  return http({
    url: '/productCategory/delete/' + id,
    method: 'post',
  })
}

/**
 * 상품 카테고리 추가
 */
export function productCategoryCreateAPI(data: PmsProductCategory) {
  return http({
    url: '/productCategory/create',
    method: 'post',
    data: data,
  })
}

/**
 * 상품 카테고리 수정
 */
export function productCategoryUpdateByIdAPI(id: number, data: PmsProductCategory) {
  return http({
    url: '/productCategory/update/' + id,
    method: 'post',
    data: data,
  })
}

/**
 * ID로 상품 카테고리 조회
 */
export function getProductCategoryByIdAPI(id: number) {
  return http<PmsProductCategory>({
    url: '/productCategory/' + id,
    method: 'get',
  })
}

/**
 * 표시 상태 일괄 수정
 */
export function productCategoryUpdateShowStatusAPI(params: { ids: string; showStatus: number }) {
  return http({
    url: '/productCategory/update/showStatus',
    method: 'post',
    params: params,
  })
}

/**
 * 내비게이션 바 표시 상태 일괄 수정
 */
export function productCategoryUpdateNavStatusAPI(params: { ids: string; navStatus: number }) {
  return http({
    url: '/productCategory/update/navStatus',
    method: 'post',
    params: params,
  })
}
