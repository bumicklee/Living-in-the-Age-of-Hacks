import type { CommonPage, PageParam } from '@/types/common'
import type { UmsMenu, UmsMenuNode } from '@/types/menu'
import http from '@/utils/http'

/**
 *  모든 메뉴 목록을 트리 구조로 반환
 */
export function getMenuTreeListAPI() {
  return http<UmsMenuNode[]>({
    url: '/menu/treeList',
    method: 'get',
  })
}

/**
 * 상위 메뉴 ID로 메뉴 페이징 조회
 */
export function getMenuListByParentIdAPI(parentId: number, params: PageParam) {
  return http<CommonPage<UmsMenu>>({
    url: '/menu/list/' + parentId,
    method: 'get',
    params: params,
  })
}

/**
 * ID로 관리자 메뉴 삭제
 */
export function deleteMenuByIdAPI(id: number) {
  return http({
    url: '/menu/delete/' + id,
    method: 'post',
  })
}

/**
 * 관리자 메뉴 추가
 */
export function menuCreateAPI(data: UmsMenu) {
  return http({
    url: '/menu/create',
    method: 'post',
    data: data,
  })
}

/**
 * 관리자 메뉴 수정
 */
export function updateMenu(id: number, data: UmsMenu) {
  return http({
    url: '/menu/update/' + id,
    method: 'post',
    data: data,
  })
}

/**
 * ID로 메뉴 상세 조회
 */
export function getMenuByIdAPI(id: number) {
  return http<UmsMenu>({
    url: '/menu/' + id,
    method: 'get',
  })
}

/**
 * ID로 메뉴 표시 상태 수정
 */
export function menuUpdateHiddenByIdAPI(id: number, params: { hidden: number }) {
  return http({
    url: '/menu/updateHidden/' + id,
    method: 'post',
    params: params,
  })
}
