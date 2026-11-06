package com.macro.mall.portal.service;

import com.macro.mall.model.OmsCartItem;
import com.macro.mall.portal.domain.CartProduct;
import com.macro.mall.portal.domain.CartPromotionItem;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 장바구니 관리Service
 * Created by macro on 2018/8/2.
 */
public interface OmsCartItemService {
    /**
     * 장바구니에 해당 상품이 있는지 조회, 있으면 수량 증가, 없으면 장바구니에 추가
     */
    @Transactional
    int add(OmsCartItem cartItem);

    /**
     * 회원 번호로 장바구니 목록 조회
     */
    List<OmsCartItem> list(Long memberId);

    /**
     * 프로모션 이벤트 정보를 포함한 장바구니 목록 조회
     */
    List<CartPromotionItem> listPromotion(Long memberId, List<Long> cartIds);

    /**
     * 장바구니 상품 수량 수정
     */
    int updateQuantity(Long id, Long memberId, Integer quantity);

    /**
     * 장바구니 상품 일괄 삭제
     */
    int delete(Long memberId,List<Long> ids);

    /**
     *규격 선택용 장바구니 상품 정보 조회
     */
    CartProduct getCartProduct(Long productId);

    /**
     * 장바구니 상품 규격 수정
     */
    @Transactional
    int updateAttr(OmsCartItem cartItem);

    /**
     * 장바구니 비우기
     */
    int clear(Long memberId);
}
