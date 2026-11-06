import type { CmsPrefrenceArea } from '@/types/prefrenceArea'
import http from '@/utils/http'

/**
 * 전체 엄선 상품 조회
 */
export function getPrefrenceAreaListAllAPI() {
  return http<CmsPrefrenceArea[]>({
    url: '/prefrenceArea/listAll',
    method: 'get',
  })
}
