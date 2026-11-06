import type { OmsOrderSetting } from '@/types/orderSetting'
import http from '@/utils/http'

/**
 * 지정한 주문 설정 조회
 */
export function getOrderSettingByIdAPI(id: number) {
  return http<OmsOrderSetting>({
    url: '/orderSetting/' + id,
    method: 'get',
  })
}

/**
 * 지정한 주문 설정 수정
 */
export function orderSettingUpdateByIdAPI(id: number, data: OmsOrderSetting) {
  return http({
    url: '/orderSetting/update/' + id,
    method: 'post',
    data: data,
  })
}
