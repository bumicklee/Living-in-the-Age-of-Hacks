import type { CommonPage } from '@/types/common'
import type { HomeBrandQueryParam, SmsHomeBrand } from '@/types/homeBrand'
import http from '@/utils/http'

/**
 * 추천 브랜드 페이지 조회
 */
export function getHomeBrandListAPI(params: HomeBrandQueryParam) {
  return http<CommonPage<SmsHomeBrand>>({
    url: '/home/brand/list',
    method: 'get',
    params: params,
  })
}

/**
 * 추천 브랜드 상태 일괄 수정
 */
export function homeBrandUpdateRecommendStatusAPI(params: {
  /** 브랜드 추천ID */
  ids: string
  /** 브랜드 추천 상태 */
  recommendStatus: number
}) {
  return http({
    url: '/home/brand/update/recommendStatus',
    method: 'post',
    params: params,
  })
}

/**
 * 추천 브랜드 일괄 삭제
 */
export function homeBrandDeleteByIdsAPI(params: { ids: string }) {
  return http({
    url: '/home/brand/delete',
    method: 'post',
    params: params,
  })
}

/**
 * 홈 추천 브랜드 추가
 */
export function homeBrandCreateAPI(data: SmsHomeBrand[]) {
  return http({
    url: '/home/brand/create',
    method: 'post',
    data: data,
  })
}

/**
 * 추천 브랜드 정렬 수정
 */
export function homeBrandUpdateSortAPI(params: { id: number; sort: number }) {
  return http({
    url: '/home/brand/update/sort/' + params.id,
    method: 'post',
    params: params,
  })
}
