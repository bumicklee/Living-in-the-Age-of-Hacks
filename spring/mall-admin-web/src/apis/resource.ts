import type { CommonPage } from '@/types/common'
import type { ResourceQueryParam, UmsResource } from '@/types/resource'
import http from '@/utils/http'

/**
 * 모든 관리자 리소스 조회
 */
export function fetchAllResourceList() {
  return http<UmsResource[]>({
    url: '/resource/listAll',
    method: 'get',
  })
}

/**
 * 관리자 리소스 페이지 단위 모호 검색
 */
export function getResourceListAPI(params: ResourceQueryParam) {
  return http<CommonPage<UmsResource>>({
    url: '/resource/list',
    method: 'get',
    params: params,
  })
}

/**
 * 관리자 리소스 추가
 */
export function resourceCreateAPI(data: UmsResource) {
  return http({
    url: '/resource/create',
    method: 'post',
    data: data,
  })
}

/**
 * 관리자 리소스 수정
 */
export function resourceUpdateAPI(id: number, data: UmsResource) {
  return http({
    url: '/resource/update/' + id,
    method: 'post',
    data: data,
  })
}

/**
 * ID로 관리자 리소스 삭제
 */
export function resourceDeleteByIdAPI(id: number) {
  return http({
    url: '/resource/delete/' + id,
    method: 'post',
  })
}
