import type { CommonPage } from '@/types/common'
import type {
  CouponHistoryQueryParam,
  CouponQueryParam,
  SmsCoupon,
  SmsCouponExt,
} from '@/types/coupon'
import http from '@/utils/http'

/**
 * 쿠폰 이름과 유형으로 쿠폰 목록 페이징 조회
 */
export function getCouponListAPI(params: CouponQueryParam) {
  return http<CommonPage<SmsCoupon>>({
    url: '/coupon/list',
    method: 'get',
    params: params,
  })
}

/**
 * 쿠폰 추가
 */
export function couponCreateAPI(data: SmsCouponExt) {
  return http({
    url: '/coupon/create',
    method: 'post',
    data: data,
  })
}

/**
 * 단일 쿠폰의 상세 정보 조회
 */
export function getCouponByIdAPI(id: number) {
  return http<SmsCouponExt>({
    url: '/coupon/' + id,
    method: 'get',
  })
}

/**
 * ID로 쿠폰 수정
 */
export function couponUpdateByIdAPI(id: number, data: SmsCouponExt) {
  return http({
    url: '/coupon/update/' + id,
    method: 'post',
    data: data,
  })
}

/**
 * ID로 쿠폰 삭제
 */
export function couponDeleteByIdAPI(id: number) {
  return http({
    url: '/coupon/delete/' + id,
    method: 'post',
  })
}

/**
 * 쿠폰 id, 사용 상태, 주문 번호로 발급 내역 페이징 조회
 */
export function getCouponHistoryListAPI(params: CouponHistoryQueryParam) {
  return http({
    url: '/couponHistory/list',
    method: 'get',
    params: params,
  })
}
