package com.macro.mall.dto;

import com.macro.mall.model.SmsCoupon;
import com.macro.mall.model.SmsCouponProductCategoryRelation;
import com.macro.mall.model.SmsCouponProductRelation;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 쿠폰 정보 래퍼, 바인딩된 상품과 카테고리 포함
 * Created by macro on 2018/8/28.
 */
public class SmsCouponParam extends SmsCoupon {
    @Getter
    @Setter
    @Schema(title = "쿠폰에 바인딩된 상품")
    private List<SmsCouponProductRelation> productRelationList;
    @Getter
    @Setter
    @Schema(title = "쿠폰에 바인딩된 상품 카테고리")
    private List<SmsCouponProductCategoryRelation> productCategoryRelationList;
}
