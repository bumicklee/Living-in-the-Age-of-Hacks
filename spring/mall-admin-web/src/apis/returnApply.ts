import type { CommonPage } from '@/types/common'
import type {
  OmsOrderReturnApply,
  OmsOrderReturnApplyResult,
  OmsUpdateStatusParam,
  ReturnApplyQueryParam,
} from '@/types/returnApply'
import http from '@/utils/http'

/**
 * 반품 신청 페이지 조회
 */
export function getReturnApplyListAPI(params: ReturnApplyQueryParam) {
  return http<CommonPage<OmsOrderReturnApply>>({
    url: '/returnApply/list',
    method: 'get',
    params: params,
  })
}

/**
 * 반품 신청 일괄 삭제
 */
export function returnApplyDeleteByIdsAPI(params: { ids: string }) {
  return http({
    url: '/returnApply/delete',
    method: 'post',
    params: params,
  })
}

/**
 * 반품 신청 상태 수정
 */
export function returnApplyUpdateStatusAPI(id: number, data: OmsUpdateStatusParam) {
  return http({
    url: '/returnApply/update/status/' + id,
    method: 'post',
    data: data,
  })
}

/**
 * 반품 신청 상세 조회
 */
export function getReturnApplyByIdAPI(id: number) {
  return http<OmsOrderReturnApplyResult>({
    url: '/returnApply/' + id,
    method: 'get',
  })
}
