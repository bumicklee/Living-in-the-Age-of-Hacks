package com.macro.mall.portal.service;

import com.macro.mall.model.SmsCoupon;
import com.macro.mall.model.SmsCouponHistory;
import com.macro.mall.portal.domain.CartPromotionItem;
import com.macro.mall.portal.domain.SmsCouponHistoryDetail;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 사용자 쿠폰 관리Service
 * Created by macro on 2018/8/29.
 */
public interface UmsMemberCouponService {
    /**
     * 회원 쿠폰 추가
     */
    @Transactional
    void add(Long couponId);

    /**
     * 쿠폰 내역 목록 조회
     */
    List<SmsCouponHistory> listHistory(Integer useStatus);

    /**
     * 장바구니 정보로 사용 가능한 쿠폰 조회
     */
    List<SmsCouponHistoryDetail> listCart(List<CartPromotionItem> cartItemList, Integer type);

    /**
     * 현재 상품 관련 쿠폰 조회
     */
    List<SmsCoupon> listByProduct(Long productId);

    /**
     * 사용자 쿠폰 목록 조회
     */
    List<SmsCoupon> list(Integer useStatus);
}
