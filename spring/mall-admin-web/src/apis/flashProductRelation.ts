import type { CommonPage } from '@/types/common'
import type { FlashProductQueryParam, SmsFlashPromotionProductRelation } from '@/types/flash'
import http from '@/utils/http'

/**
 * 회차별 연관 및 상품 정보 페이지 조회
 */
export function getFlashProductRelationListAPI(params: FlashProductQueryParam) {
  return http<CommonPage<SmsFlashPromotionProductRelation>>({
    url: '/flashProductRelation/list',
    method: 'get',
    params: params,
  })
}
/**
 * 상품을 일괄 선택해 연관 상품 추가
 */
export function flashProductRelationCreateAPI(data: SmsFlashPromotionProductRelation[]) {
  return http({
    url: '/flashProductRelation/create',
    method: 'post',
    data: data,
  })
}
/**
 * ID로 연관 정보 수정
 */
export function flashProductRelationDeleteByIdAPI(id: number) {
  return http({
    url: '/flashProductRelation/delete/' + id,
    method: 'post',
  })
}
/**
 * ID로 연관 정보 수정
 */
export function flashProductRelationUpdateByIdAPI(
  id: number,
  data: SmsFlashPromotionProductRelation,
) {
  return http({
    url: '/flashProductRelation/update/' + id,
    method: 'post',
    data: data,
  })
}
