import type { CommonPage, PageParam } from '@/types/common'
import type { OmsOrderReturnReason } from '@/types/returnReason'
import http from '@/utils/http'

/**
 * 반품 사유 페이지 조회
 */
export function getReturnReasonListAPI(params: PageParam) {
  return http<CommonPage<OmsOrderReturnReason>>({
    url: '/returnReason/list',
    method: 'get',
    params: params,
  })
}

/**
 * 반품 사유 일괄 삭제
 */
export function returnReasonDeleteByIdsAPI(params: { ids: string }) {
  return http({
    url: '/returnReason/delete',
    method: 'post',
    params: params,
  })
}

/**
 * 반품 사유 사용 상태 수정
 */
export function returnReasonUpdateStatusAPI(params: { ids: string; status: number }) {
  return http({
    url: '/returnReason/update/status',
    method: 'post',
    params: params,
  })
}

/**
 * 반품 사유 추가
 */
export function returnReasonCreateAPI(data: OmsOrderReturnReason) {
  return http({
    url: '/returnReason/create',
    method: 'post',
    data: data,
  })
}

/**
 * 단일 반품 사유 상세 정보 조회
 */
export function getReturnReasonByIdAPI(id: number) {
  return http<OmsOrderReturnReason>({
    url: '/returnReason/' + id,
    method: 'get',
  })
}

/**
 * 반품 사유 수정
 */
export function returnReasonUpdateAPI(id: number, data: OmsOrderReturnReason) {
  return http({
    url: '/returnReason/update/' + id,
    method: 'post',
    data: data,
  })
}
