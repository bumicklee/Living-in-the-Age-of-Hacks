import type { UmsResourceCategory } from '@/types/resource'
import http from '@/utils/http'

/**
 * 모든 관리자 리소스 분류 조회
 */
export function resourceCategoryListAllAPI() {
  return http<UmsResourceCategory[]>({
    url: '/resourceCategory/listAll',
    method: 'get',
  })
}

/**
 * 관리자 리소스 카테고리 추가
 */
export function resourceCategoryCreateAPI(data: UmsResourceCategory) {
  return http({
    url: '/resourceCategory/create',
    method: 'post',
    data: data,
  })
}

/**
 * 관리자 리소스 카테고리 수정
 */
export function resourceCategoryUpdateAPI(id: number, data: UmsResourceCategory) {
  return http({
    url: '/resourceCategory/update/' + id,
    method: 'post',
    data: data,
  })
}

/**
 * ID로 관리자 리소스 분류 삭제
 */
export function resourceCategoryDeleteByIdAPI(id: number) {
  return http({
    url: '/resourceCategory/delete/' + id,
    method: 'post',
  })
}
