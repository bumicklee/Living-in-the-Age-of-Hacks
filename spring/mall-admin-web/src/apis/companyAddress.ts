import type { OmsCompanyAddress } from '@/types/companyAddress'
import http from '@/utils/http'

/**
 * 전체 배송지 조회
 */
export function getCompanyAddressListAPI() {
  return http<OmsCompanyAddress[]>({
    url: '/companyAddress/list',
    method: 'get',
  })
}
