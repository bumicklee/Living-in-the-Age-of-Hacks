import type { CommonPage } from '@/types/common'
import type { RecommendProductQueryParam, SmsHomeRecommendProduct } from '@/types/recommendProduct'
import http from '@/utils/http'

/**
 * 홈 신상품 페이지 조회
 */
export function getHomeRecommendProductListAPI(params: RecommendProductQueryParam) {
  return http<CommonPage<SmsHomeRecommendProduct>>({
    url: '/home/recommendProduct/list',
    method: 'get',
    params: params,
  })
}

/**
 * 홈 신상품 상태 일괄 수정
 */
export function homeRecommendProductUpdateRecommendStatusAPI(params: {
  ids: string
  recommendStatus: number
}) {
  return http({
    url: '/home/recommendProduct/update/recommendStatus',
    method: 'post',
    params: params,
  })
}

/**
 * 홈 신상품 일괄 삭제
 */
export function homeRecommendProductDeleteByIdsAPI(params: { ids: string }) {
  return http({
    url: '/home/recommendProduct/delete',
    method: 'post',
    params: params,
  })
}

/**
 * 홈 신상품 일괄 추가
 */
export function homeRecommendProductCreateAPI(data: SmsHomeRecommendProduct[]) {
  return http({
    url: '/home/recommendProduct/create',
    method: 'post',
    data: data,
  })
}

/**
 * ID로 홈 신상품 정렬 수정
 */
export function homeRecommendProductUpdateSortByIdAPI(params: { id: number; sort: number }) {
  return http({
    url: '/home/recommendProduct/update/sort/' + params.id,
    method: 'post',
    params: params,
  })
}
