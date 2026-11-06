package com.macro.mall.portal.dao;

import com.macro.mall.model.SmsCoupon;
import com.macro.mall.portal.domain.CartProduct;
import com.macro.mall.portal.domain.PromotionProduct;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 프런트 장바구니 상품 관리 커스텀Dao
 * Created by macro on 2018/8/2.
 */
public interface PortalProductDao {
    /**
     * 장바구니 상품 정보 조회
     */
    CartProduct getCartProduct(@Param("id") Long id);

    /**
     * 프로모션 상품 정보 목록 조회
     */
    List<PromotionProduct> getPromotionProductList(@Param("ids") List<Long> ids);

    /**
     * 사용 가능한 쿠폰 목록 조회
     */
    List<SmsCoupon> getAvailableCouponList(@Param("productId") Long productId, @Param("productCategoryId") Long productCategoryId);
}
