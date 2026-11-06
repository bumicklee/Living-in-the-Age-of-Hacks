import type { CommonPage } from '@/types/common'
import type { NewProductQueryParam, SmsHomeNewProduct } from '@/types/newProduct'
import http from '@/utils/http'

/**
 * 홈 신상품 페이지 조회
 */
export function getHomeNewProductListAPI(params: NewProductQueryParam) {
  return http<CommonPage<SmsHomeNewProduct>>({
    url: '/home/newProduct/list',
    method: 'get',
    params: params,
  })
}

/**
 * 홈 신상품 상태 일괄 수정
 */
export function homeNewProductUpdateRecommendStatusAPI(params: {
  ids: string
  recommendStatus: number
}) {
  return http({
    url: '/home/newProduct/update/recommendStatus',
    method: 'post',
    params: params,
  })
}

/**
 * 홈 신상품 일괄 삭제
 */
export function homeNewProductDeleteByIdsAPI(params: { ids: string }) {
  return http({
    url: '/home/newProduct/delete',
    method: 'post',
    params: params,
  })
}

/**
 * 홈 신상품 일괄 추가
 */
export function homeNewProductCreateAPI(data: SmsHomeNewProduct[]) {
  return http({
    url: '/home/newProduct/create',
    method: 'post',
    data: data,
  })
}

/**
 * ID로 홈 신상품 정렬 수정
 */
export function homeNewProductUpdateSortByIdAPI(params: { id: number; sort: number }) {
  return http({
    url: '/home/newProduct/update/sort/' + params.id,
    method: 'post',
    params: params,
  })
}
