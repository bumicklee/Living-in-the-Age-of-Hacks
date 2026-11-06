import type { CommonPage, PageParam } from '@/types/common'
import type { CmsSubject } from '@/types/subject'
import http from '@/utils/http'

/**
 * 전체 상품 기획전 조회
 */
export function getSubjectListAllAPI() {
  return http<CmsSubject[]>({
    url: '/subject/listAll',
    method: 'get',
  })
}

/**
 * 기획전 이름으로 상품 기획전 페이징 조회
 */
export function getSubjectListAPI(params: PageParam) {
  return http<CommonPage<CmsSubject>>({
    url: '/subject/list',
    method: 'get',
    params: params,
  })
}
