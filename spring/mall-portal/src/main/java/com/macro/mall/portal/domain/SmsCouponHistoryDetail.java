package com.macro.mall.portal.domain;

import com.macro.mall.model.SmsCoupon;
import com.macro.mall.model.SmsCouponHistory;
import com.macro.mall.model.SmsCouponProductCategoryRelation;
import com.macro.mall.model.SmsCouponProductRelation;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 쿠폰 수령 내역 상세(쿠폰 정보와 연관 관계 포함)
 * Created by macro on 2018/8/29.
 */
@Getter
@Setter
public class SmsCouponHistoryDetail extends SmsCouponHistory {
    @Schema(title = "관련 쿠폰 정보")
    private SmsCoupon coupon;
    @Schema(title = "쿠폰 연관 상품")
    private List<SmsCouponProductRelation> productRelationList;
    @Schema(title = "쿠폰 연관 상품 카테고리")
    private List<SmsCouponProductCategoryRelation> categoryRelationList;
}
