import type { SmsFlashPromotionSession } from '@/types/flash'
import http from '@/utils/http'

/**
 * 선택 가능한 전체 회차와 수량 조회
 */
export function getFlashSessionSelectListAPI(params: { flashPromotionId: number }) {
  return http<SmsFlashPromotionSession[]>({
    url: '/flashSession/selectList',
    method: 'get',
    params: params,
  })
}

/**
 * 전체 회차 조회
 */
export function getFlashSessionListAPI() {
  return http<SmsFlashPromotionSession[]>({
    url: '/flashSession/list',
    method: 'get',
  })
}

/**
 * ID로 회차 사용 상태 수정
 */
export function flashSessionUpdateStatusByIdAPI(id: number, params: { status: number }) {
  return http({
    url: '/flashSession/update/status/' + id,
    method: 'post',
    params: params,
  })
}

/**
 * ID로 회차 삭제
 * @param id
 * @returns
 */
export function flashSessionDeleteByIdAPI(id: number) {
  return http({
    url: '/flashSession/delete/' + id,
    method: 'post',
  })
}

/**
 * 회차 추가
 */
export function flashSessionCreateAPI(data: SmsFlashPromotionSession) {
  return http({
    url: '/flashSession/create',
    method: 'post',
    data: data,
  })
}

/**
 * 회차 수정
 */
export function flashSessionUpdateByIdAPI(id: number, data: SmsFlashPromotionSession) {
  return http({
    url: '/flashSession/update/' + id,
    method: 'post',
    data: data,
  })
}
