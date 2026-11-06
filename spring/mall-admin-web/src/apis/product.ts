import type { CommonPage } from '@/types/common'
import type { PmsProduct, PmsProductParam, ProductQueryParam } from '@/types/product'
import http from '@/utils/http'

/**
 * 상품 조회
 */
export function getProductListAPI(params: ProductQueryParam) {
  return http<CommonPage<PmsProduct>>({
    url: '/product/list',
    method: 'get',
    params: params,
  })
}

/**
 * 삭제 상태 일괄 수정
 */
export function productUpdateDeleteStatusAPI(params: { ids: string; deleteStatus: number }) {
  return http({
    url: '/product/update/deleteStatus',
    method: 'post',
    params: params,
  })
}

/**
 * 신상품으로 일괄 설정
 */
export function productUpdateNewStatusAPI(params: { ids: string; newStatus: number }) {
  return http({
    url: '/product/update/newStatus',
    method: 'post',
    params: params,
  })
}

/**
 * 상품 일괄 추천
 */
export function productUpdateRecommendStatusAPI(params: { ids: string; recommendStatus: number }) {
  return http({
    url: '/product/update/recommendStatus',
    method: 'post',
    params: params,
  })
}

/**
 * 상품 일괄 판매 시작/중지
 */
export function productUpdatePublishStatusAPI(params: { ids: string; publishStatus: number }) {
  return http({
    url: '/product/update/publishStatus',
    method: 'post',
    params: params,
  })
}

/**
 * 상품 생성
 */
export function productCreateAPI(data: PmsProductParam) {
  return http({
    url: '/product/create',
    method: 'post',
    data: data,
  })
}

/**
 * ID로 상품 정보 수정
 */
export function productUpdateByIdAPI(id: number, data: PmsProductParam) {
  return http({
    url: '/product/update/' + id,
    method: 'post',
    data: data,
  })
}

/**
 * 상품 id로 상품 편집 정보 조회
 */
export function getPruductUpdateInfoAPI(id: number) {
  return http<PmsProductParam>({
    url: '/product/updateInfo/' + id,
    method: 'get',
  })
}
