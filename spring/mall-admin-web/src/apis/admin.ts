import type { LoginParam, LoginResult, UmsAdmin, UserInfoResult } from '@/types/admin'
import type { CommonPage, PageParam } from '@/types/common'
import type { UmsRole } from '@/types/role'
import http from '@/utils/http'

/**
 * 관리자 로그인 후 반환token
 */
export function adminLoginAPI(data: LoginParam) {
  return http<LoginResult>({
    method: 'POST',
    url: '/admin/login',
    data: data,
  })
}

/**
 * 관리자 로그아웃
 */
export function adminLogoutAPI() {
  return http({
    method: 'POST',
    url: '/admin/logout',
  })
}

/**
 * 현재 로그인한 사용자 정보 조회
 */
export function getAdminInfoAPI() {
  return http<UserInfoResult>({
    method: 'GET',
    url: '/admin/info',
  })
}

/**
 * 사용자 이름 또는 성명으로 사용자 목록 페이징 조회
 */
export function getAdminListAPI(params: PageParam) {
  return http<CommonPage<UmsAdmin>>({
    url: '/admin/list',
    method: 'get',
    params: params,
  })
}

/**
 * 관리자 사용자 회원가입
 */
export function adminRegisterAPI(data: UmsAdmin) {
  return http({
    url: '/admin/register',
    method: 'post',
    data: data,
  })
}

/**
 * ID로 지정한 사용자 정보 수정
 */
export function adminUpdateByIdAPI(id: number, data: UmsAdmin) {
  return http({
    url: '/admin/update/' + id,
    method: 'post',
    data: data,
  })
}

/**
 * 사용자 ID로 계정 사용 상태 수정
 */
export function adminUpdateStatusByIdAPI(id: number, params: { status: number }) {
  return http({
    url: '/admin/updateStatus/' + id,
    method: 'post',
    params: params,
  })
}

/**
 * ID로 사용자 삭제
 */
export function adminDeleteByIdAPI(id: number) {
  return http({
    url: '/admin/delete/' + id,
    method: 'post',
  })
}

/**
 * 사용자 ID로 할당된 역할 조회
 */
export function getRoleByAdminIdAPI(id: number) {
  return http<UmsRole[]>({
    url: '/admin/role/' + id,
    method: 'get',
  })
}

/**
 * 지정한 ID의 사용자에게 역할 할당
 */
export function adminRoleUpdateAPI(params: { roleIds: string; adminId: number }) {
  return http({
    url: '/admin/role/update',
    method: 'post',
    params: params,
  })
}
