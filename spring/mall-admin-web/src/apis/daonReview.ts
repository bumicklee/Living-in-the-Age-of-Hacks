import http from '@/utils/http'

/** [교육용 추가] 다온마켓 상품 후기 한 줄(mall-admin GET /daon/review/list) */
export interface DaonReview {
  id: number
  productId: number
  productName: string
  memberNickName: string
  memberIp: string
  star: number
  content: string
  createTime: string
}

/**
 * [교육용 추가] 상품 후기 목록(최신순)
 */
export function getDaonReviewListAPI(params: { pageSize?: number }) {
  return http<{ list: DaonReview[]; textOnly?: boolean }>({
    url: '/daon/review/list',
    method: 'get',
    params: params,
  })
}
