import type { CommonPage } from '@/types/common'
import type {
  OmsMoneyInfoParam,
  OmsOrder,
  OmsOrderDeliveryParam,
  OmsOrderDetail,
  OmsReceiverInfoParam,
  OrderQueryParam,
} from '@/types/order'
import http from '@/utils/http'

/**
 * 주문 목록 페이지 조회
 */
export function getOrderListAPI(params: OrderQueryParam) {
  return http<CommonPage<OmsOrder>>({
    url: '/order/list',
    method: 'get',
    params: params,
  })
}

/**
 * 주문 일괄 닫기
 */
export function orderUpdateCloseAPI(params: { ids: string; note: string }) {
  return http({
    url: '/order/update/close',
    method: 'post',
    params: params,
  })
}

/**
 * 주문 일괄 삭제
 */
export function orderDeleteByIdsAPI(params: { ids: string }) {
  return http({
    url: '/order/delete',
    method: 'post',
    params: params,
  })
}

/**
 * 일괄 발송
 */
export function orderUpdateDeliveryAPI(data: OmsOrderDeliveryParam[]) {
  return http({
    url: '/order/update/delivery',
    method: 'post',
    data: data,
  })
}

/**
 * 주문 상세 조회: 주문 정보, 상품 정보, 작업 기록
 */
export function getOrderDetailByIdAPI(id: number) {
  return http<OmsOrderDetail>({
    url: '/order/' + id,
    method: 'get',
  })
}

/**
 * 수령인 정보 수정
 */
export function orderUpdateReceiverInfoAPI(data: OmsReceiverInfoParam) {
  return http({
    url: '/order/update/receiverInfo',
    method: 'post',
    data: data,
  })
}

/**
 * 주문 비용 정보 수정
 */
export function orderUpdateMoneyInfoAPI(data: OmsMoneyInfoParam) {
  return http({
    url: '/order/update/moneyInfo',
    method: 'post',
    data: data,
  })
}

/**
 * 주문 메모
 */
export function orderUpdateNoteAPI(params: { id: number; note: string; status: number }) {
  return http({
    url: '/order/update/note',
    method: 'post',
    params: params,
  })
}
