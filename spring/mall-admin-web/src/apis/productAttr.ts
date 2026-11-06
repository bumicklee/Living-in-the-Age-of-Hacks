import type { CommonPage, PageParam } from '@/types/common'
import type { PmsProductAttribute, ProductAttrInfo } from '@/types/productAttr'
import http from '@/utils/http'

/**
 * 상품 카테고리 ID로 상품 속성 및 속성 카테고리 조회ID
 */
export function getProductAttrInfoByCateIdAPI(cateId: number) {
  return http<ProductAttrInfo[]>({
    url: '/productAttribute/attrInfo/' + cateId,
    method: 'get',
  })
}

/**
 * 카테고리 ID로 속성 목록 또는 파라미터 목록 조회
 */
export function getProductAttributeListAPI(cid: number, params: PageParam & { type: number }) {
  return http<CommonPage<PmsProductAttribute>>({
    url: '/productAttribute/list/' + cid,
    method: 'get',
    params: params,
  })
}

/**
 * 상품 속성 일괄 삭제
 */
export function productAttributeDeleteByIds(params: { ids: string }) {
  return http({
    url: '/productAttribute/delete',
    method: 'post',
    params: params,
  })
}

/**
 * 상품 속성 정보 추가
 */
export function productAttributeCreateAPI(data: PmsProductAttribute) {
  return http({
    url: '/productAttribute/create',
    method: 'post',
    data: data,
  })
}

/**
 * 상품 속성 정보 수정
 */
export function productAttributeUpdateAPI(id: number, data: PmsProductAttribute) {
  return http({
    url: '/productAttribute/update/' + id,
    method: 'post',
    data: data,
  })
}

/**
 * ID로 상품 속성 조회
 */
export function getProductAttributeByIdAPI(id: number) {
  return http<PmsProductAttribute>({
    url: '/productAttribute/' + id,
    method: 'get',
  })
}
