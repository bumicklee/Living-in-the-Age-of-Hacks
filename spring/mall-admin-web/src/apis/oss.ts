import type { OssPolicyResult } from '@/types/oss'
import http from '@/utils/http'

/**
 * Oss업로드 서명 생성
 */
export function ossPolicyAPI() {
  return http<OssPolicyResult>({
    url: '/aliyun/oss/policy',
    method: 'get',
  })
}
