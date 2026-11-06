import type { CommonPage } from '@/types/common'
import type { HomeSubjectQueryParam, SmsHomeRecommendSubject } from '@/types/homeSubject'
import http from '@/utils/http'

/**
 * 기획전 추천 페이지 조회
 */
export function getHomeRecommendSubjectListAPI(params: HomeSubjectQueryParam) {
  return http<CommonPage<SmsHomeRecommendSubject>>({
    url: '/home/recommendSubject/list',
    method: 'get',
    params: params,
  })
}

/**
 * 기획전 추천 상태 일괄 수정
 */
export function homeRecommendSubjectUpdateRecommendStatusAPI(params: {
  /** 추천ID */
  ids: string
  /** 추천 상태 */
  recommendStatus: number
}) {
  return http({
    url: '/home/recommendSubject/update/recommendStatus',
    method: 'post',
    params: params,
  })
}

/**
 * 기획전 추천 일괄 삭제
 */
export function homeRecommendSubjectDeleteByIdsAPI(params: { ids: string }) {
  return http({
    url: '/home/recommendSubject/delete',
    method: 'post',
    params: params,
  })
}

/**
 * 홈 기획전 추천 일괄 추가
 */
export function homeRecommendSubjectCreateAPI(data: SmsHomeRecommendSubject[]) {
  return http({
    url: '/home/recommendSubject/create',
    method: 'post',
    data: data,
  })
}

/**
 * 기획전 추천 정렬 수정
 */
export function homeRecommendSubjectUpdateSortAPI(params: { id: number; sort: number }) {
  return http({
    url: '/home/recommendSubject/update/sort/' + params.id,
    method: 'post',
    params: params,
  })
}
