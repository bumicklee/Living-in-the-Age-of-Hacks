package com.macro.mall.portal.domain;

import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.PmsProductFullReduction;
import com.macro.mall.model.PmsProductLadder;
import com.macro.mall.model.PmsSkuStock;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 프로모션 상품 정보, sku, 할인 혜택, 금액 할인 혜택 포함
 * Created by macro on 2018/8/27.
 */
@Getter
@Setter
public class PromotionProduct extends PmsProduct {
    //상품 재고 정보
    private List<PmsSkuStock> skuStockList;
    //상품 할인 정보
    private List<PmsProductLadder> productLadderList;
    //상품 금액 할인 정보
    private List<PmsProductFullReduction> productFullReductionList;
}
