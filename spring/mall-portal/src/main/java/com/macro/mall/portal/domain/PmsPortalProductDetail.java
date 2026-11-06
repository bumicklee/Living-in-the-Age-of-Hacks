package com.macro.mall.portal.domain;

import com.macro.mall.model.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 프런트 상품 상세
 * Created by macro on 2020/4/6.
 */
@Getter
@Setter
public class PmsPortalProductDetail{
    @Schema(title = "상품 정보")
    private PmsProduct product;
    @Schema(title = "상품 브랜드")
    private PmsBrand brand;
    @Schema(title = "상품 속성 및 파라미터")
    private List<PmsProductAttribute> productAttributeList;
    @Schema(title = "직접 입력한 상품 속성 및 파라미터 값")
    private List<PmsProductAttributeValue> productAttributeValueList;
    @Schema(title = "상품의 sku 재고 정보")
    private List<PmsSkuStock> skuStockList;
    @Schema(title = "상품 수량별 가격 설정")
    private List<PmsProductLadder> productLadderList;
    @Schema(title = "상품 금액 할인 가격 설정")
    private List<PmsProductFullReduction> productFullReductionList;
    @Schema(title = "상품 사용 가능 쿠폰")
    private List<SmsCoupon> couponList;
}
