/** SKU재고 정보 */
export type PmsSkuStock = {
  /** ID */
  id?: number
  /** 제품ID */
  productId?: number
  /** sku코드 */
  skuCode?: string
  /** 가격 */
  price?: number
  /** 재고 */
  stock?: number
  /** 재고 경고 */
  lowStock?: number
  /** 표시 이미지 */
  pic?: string
  /** 판매량 */
  sale?: number
  /** 단품 프로모션 가격 */
  promotionPrice?: number
  /** 재고 잠금 */
  lockStock?: number
  /** 상품 판매 속성, json 형식 */
  spData?: string
}
