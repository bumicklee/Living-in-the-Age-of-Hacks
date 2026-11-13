package com.macro.mall.portal.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * [다온마켓 10-8 정답] 장바구니 담기·규격 바꾸기 요청 전용 DTO(2-4와 같은 방법). 상품·SKU·수량만 받는다.
 *
 * 가격·상품 이름·SKU 코드는 서버가 pms_sku_stock·pms_product에서 읽는다. 「가격은 요청에서 받지 않는다」.
 * price는 옛 고객 웹·직접 만든 요청이 보내는 값을 「받아만 두는」 칸이다. 쓰지 않고, SKU 가격과 다르면 price.tampered로 남긴다(탐지용).
 */
@Data
public class CartAddParam {
    @Schema(title = "장바구니 줄 ID(규격 바꾸기 /cart/update/attr 때만)")
    private Long id;
    @Schema(title = "상품 ID")
    private Long productId;
    @Schema(title = "SKU ID")
    private Long productSkuId;
    @Schema(title = "수량(1~99)")
    private Integer quantity;
    @Schema(title = "쓰지 않음: 요청이 보낸 가격(조작 탐지용)", deprecated = true)
    private BigDecimal price;
}
