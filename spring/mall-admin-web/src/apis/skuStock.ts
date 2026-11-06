import type { PmsSkuStock } from '@/types/skuStock'
import http from '@/utils/http'

/**
 * 상품 ID 및 sku 코드로 sku 재고 모호 검색
 */
export function getSkuListByPidAPI(pid: number, params: { keyword?: string }) {
  return http<PmsSkuStock[]>({
    url: '/sku/' + pid,
    method: 'get',
    params: params,
  })
}

/**
 * 상품 ID로 sku 재고 정보 일괄 수정
 */
export function skuUpdateByPidAPI(pid: number, data: PmsSkuStock[]) {
  return http({
    url: '/sku/update/' + pid,
    method: 'post',
    data: data,
  })
}
