import type { CommonPage, PageParam } from '@/types/common'
import type { UmsMenu } from '@/types/menu'
import type { UmsRole } from '@/types/role'
import http from '@/utils/http'

/**
 * 전체 역할 목록 조회
 */
export function getRoleListAllAPI() {
  return http<UmsRole[]>({
    url: '/role/listAll',
    method: 'get',
  })
}

/**
 * 역할 이름으로 역할 목록 페이징 조회
 */
export function getRoleListAPI(params: PageParam) {
  return http<CommonPage<UmsRole>>({
    url: '/role/list',
    method: 'get',
    params: params,
  })
}

/**
 * 역할 추가
 */
export function roleCreateAPI(data: UmsRole) {
  return http({
    url: '/role/create',
    method: 'post',
    data: data,
  })
}

/**
 * ID로 역할 수정
 */
export function roleUpdateByIdAPI(id: number, data: UmsRole) {
  return http({
    url: '/role/update/' + id,
    method: 'post',
    data: data,
  })
}

/**
 * 역할 상태 사용 여부 수정
 */
export function roleUpdateStatusAPI(id: number, params: { status: number }) {
  return http({
    url: '/role/updateStatus/' + id,
    method: 'post',
    params: params,
  })
}

/**
 * ids로 역할 일괄 삭제
 */
export function roleDeleteByIdsAPI(params: { ids: string }) {
  return http({
    url: '/role/delete',
    method: 'post',
    params: params,
  })
}

/**
 * 역할 ID로 메뉴 목록 조회
 */
export function roleListMenuByRoleIdAPI(id: number) {
  return http<UmsMenu[]>({
    url: '/role/listMenu/' + id,
    method: 'get',
  })
}

/**
 * 역할에 메뉴 할당
 */
export function roleAllocMenuAPI(params: {
  /** 역할ID */
  roleId: number
  /** 메뉴 ID, 여러 개는 쉼표로 구분 */
  menuIds: string
}) {
  return http({
    url: '/role/allocMenu',
    method: 'post',
    params: params,
  })
}

/**
 * 역할 관련 리소스 조회
 */
export function roleListResourceById(id: number) {
  return http({
    url: '/role/listResource/' + id,
    method: 'get',
  })
}

/**
 * 역할에 리소스 할당
 */
export function roleAllocResourceAPI(params: {
  /** 역할ID */
  roleId: number
  /** 리소스 ID, 여러 개는 쉼표로 구분 */
  resourceIds: string
}) {
  return http({
    url: '/role/allocResource',
    method: 'post',
    params: params,
  })
}
