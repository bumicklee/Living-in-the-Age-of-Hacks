import type { UmsMemberLevel } from '@/types/memberLevel'
import http from '@/utils/http'

/**
 * 모든 회원 등급 조회
 */
export function getMemberLevelListAPI(params: { defaultStatus: number }) {
  return http<UmsMemberLevel[]>({
    url: '/memberLevel/list',
    method: 'get',
    params: params,
  })
}
