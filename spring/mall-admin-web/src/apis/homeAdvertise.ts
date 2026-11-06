import type { CommonPage } from '@/types/common'
import type { HomeAdvertiseQueryParam, SmsHomeAdvertise } from '@/types/homeAdvertist'
import http from '@/utils/http'

/**
 * 홈 광고 페이지 조회
 */
export function getHomeAdvertiseListAPI(params: HomeAdvertiseQueryParam) {
  return http<CommonPage<SmsHomeAdvertise>>({
    url: '/home/advertise/list',
    method: 'get',
    params: params,
  })
}

/**
 * 판매/판매 중지 상태 수정
 */
export function homeAdvertiseUpdateStatusAPI(params: { id: number; status: number }) {
  return http({
    url: '/home/advertise/update/status/' + params.id,
    method: 'post',
    params: params,
  })
}

/**
 * 광고 일괄 삭제
 */
export function deleteHomeAdvertiseAPI(params: { ids: string }) {
  return http({
    url: '/home/advertise/delete',
    method: 'post',
    params: params,
  })
}

/**
 * 광고 추가
 */
export function homeAdvertiseCreateAPI(data: SmsHomeAdvertise) {
  return http({
    url: '/home/advertise/create',
    method: 'post',
    data: data,
  })
}

/**
 * ID로 광고 상세 조회
 */
export function getHomeAdvertiseByIdAPI(id: number) {
  return http({
    url: '/home/advertise/' + id,
    method: 'get',
  })
}

/**
 * ID로 광고 수정
 */
export function homeAdvertiseUpdateAPI(id: number, data: SmsHomeAdvertise) {
  return http({
    url: '/home/advertise/update/' + id,
    method: 'post',
    data: data,
  })
}
