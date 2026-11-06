package com.macro.mall.portal.domain;

import com.macro.mall.model.OmsCartItem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 장바구니 프로모션 정보 래퍼
 * Created by macro on 2018/8/27.
 */
@Getter
@Setter
public class CartPromotionItem extends OmsCartItem{
    @Schema(title = "프로모션 정보")
    private String promotionMessage;
    @Schema(title = "프로모션으로 차감된 금액, 상품별 적용")
    private BigDecimal reduceAmount;
    @Schema(title = "남은 재고 - 잠금 재고")
    private Integer realStock;
    @Schema(title = "상품 구매 시 포인트 지급")
    private Integer integration;
    @Schema(title = "상품 구매 시 성장 포인트 지급")
    private Integer growth;
}
