import type { PageParam } from '@/types/common'
import type { SmsFlashPromotion } from '@/types/flash'
import http from '@/utils/http'

/**
 * 이벤트 이름으로 이벤트 페이징 조회
 */
export function getFlashListAPI(params: PageParam) {
  return http({
    url: '/flash/list',
    method: 'get',
    params: params,
  })
}
/**
 * 이벤트 판매/판매 중지 상태 수정
 */
export function flashUpdateStatusByIdAPI(id: number, params: { status: number }) {
  return http({
    url: '/flash/update/status/' + id,
    method: 'post',
    params: params,
  })
}
/**
 * ID로 이벤트 삭제
 */
export function flashDeleteByIdAPI(id: number) {
  return http({
    url: '/flash/delete/' + id,
    method: 'post',
  })
}
/**
 * 이벤트 추가
 */
export function flashCreateAPI(data: SmsFlashPromotion) {
  return http({
    url: '/flash/create',
    method: 'post',
    data: data,
  })
}
/**
 * ID로 이벤트 수정
 */
export function flashUpdateByIdAPI(id: number, data: SmsFlashPromotion) {
  return http({
    url: '/flash/update/' + id,
    method: 'post',
    data: data,
  })
}
