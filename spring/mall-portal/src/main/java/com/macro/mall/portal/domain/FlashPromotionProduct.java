package com.macro.mall.portal.domain;

import com.macro.mall.model.PmsProduct;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 타임 세일 정보 및 상품 객체 래핑
 * Created by macro on 2019/1/28.
 */
@Getter
@Setter
public class FlashPromotionProduct extends PmsProduct{
    @Schema(title = "타임 세일 가격")
    private BigDecimal flashPromotionPrice;
    @Schema(title = "타임 세일 수량 계산용")
    private Integer flashPromotionCount;
    @Schema(title = "타임 세일 구매 제한 수량")
    private Integer flashPromotionLimit;
}
