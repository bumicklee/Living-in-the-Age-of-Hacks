package com.macro.mall.portal.service;

import com.macro.mall.model.OmsCartItem;
import com.macro.mall.portal.domain.CartPromotionItem;

import java.util.List;

/**
 * 프로모션 관리Service
 * Created by macro on 2018/8/27.
 */
public interface OmsPromotionService {
    /**
     * 장바구니 프로모션 정보 계산
     * @param cartItemList 장바구니
     */
    List<CartPromotionItem> calcCartPromotion(List<OmsCartItem> cartItemList);
}
