import type { PmsBrand } from '@/types/brand'
import type { CommonPage, PageParam } from '@/types/common'
import http from '@/utils/http'

/**
 * 브랜드 이름으로 브랜드 목록 페이징 조회
 */
export function getBrandListAPI(params: PageParam) {
  return http<CommonPage<PmsBrand>>({
    url: '/brand/list',
    method: 'get',
    params: params,
  })
}

/**
 * 브랜드 추가
 */
export function createBrandAPI(data: PmsBrand) {
  return http({
    url: '/brand/create',
    method: 'post',
    data: data,
  })
}

/**
 * 표시 상태 일괄 업데이트
 */
export function brandUpdateShowStatusAPI(params: { ids: string; showStatus: number }) {
  return http({
    url: '/brand/update/showStatus',
    method: 'post',
    params: params,
  })
}

/**
 * 제조사 상태 일괄 업데이트
 */
export function brandUpdateFactoryStatusAPI(params: { ids: string; factoryStatus: number }) {
  return http({
    url: '/brand/update/factoryStatus',
    method: 'post',
    params: params,
  })
}

/**
 * 브랜드 ID로 브랜드 삭제
 */
export function brandDeleteByIdAPI(id: number) {
  return http({
    url: '/brand/delete/' + id,
    method: 'get',
  })
}

/**
 * ID로 브랜드 상세 조회
 */
export function getBrandAPI(id: number) {
  return http<PmsBrand>({
    url: '/brand/' + id,
    method: 'get',
  })
}

/**
 * 브랜드 ID로 브랜드 정보 수정
 */
export function updateBrandAPI(id: number, data: PmsBrand) {
  return http({
    url: '/brand/update/' + id,
    method: 'post',
    data: data,
  })
}
