package com.macro.mall.portal.domain;

import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.PmsProductAttribute;
import com.macro.mall.model.PmsSkuStock;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 규격과 SKU가 있는 장바구니 상품 정보
 * Created by macro on 2018/8/2.
 */
@Getter
@Setter
public class CartProduct extends PmsProduct {
    @Schema(title = "상품 속성 목록")
    private List<PmsProductAttribute> productAttributeList;
    @Schema(title = "상품 SKU 재고 목록")
    private List<PmsSkuStock> skuStockList;
}
