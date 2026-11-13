package com.macro.mall.portal.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.mapper.OmsCartItemMapper;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.mapper.PmsSkuStockMapper;
import com.macro.mall.model.OmsCartItem;
import com.macro.mall.model.OmsCartItemExample;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.PmsSkuStock;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.dao.PortalProductDao;
import com.macro.mall.portal.domain.CartProduct;
import com.macro.mall.portal.domain.CartPromotionItem;
import com.macro.mall.portal.service.OmsCartItemService;
import com.macro.mall.portal.service.OmsPromotionService;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.security.securekit.SecureKitSwitch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 장바구니 관리 Service 구현 클래스
 * Created by macro on 2018/8/2.
 */
@Service
public class OmsCartItemServiceImpl implements OmsCartItemService {
    @Autowired
    private OmsCartItemMapper cartItemMapper;
    @Autowired
    private PortalProductDao productDao;
    @Autowired
    private OmsPromotionService promotionService;
    @Autowired
    private UmsMemberService memberService;
    @Autowired
    private PmsSkuStockMapper skuStockMapper;
    @Autowired
    private PmsProductMapper productMapper;

    @Override
    public int add(OmsCartItem cartItem) {
        int count;
        fillFromSku(cartItem);                      // 다온마켓 10-8: 가격·이름·SKU 코드는 서버가 정한다
        UmsMember currentMember =memberService.getCurrentMember();
        cartItem.setMemberId(currentMember.getId());
        cartItem.setMemberNickname(currentMember.getNickname());
        cartItem.setDeleteStatus(0);
        OmsCartItem existCartItem = getCartItem(cartItem);
        if (existCartItem == null) {
            cartItem.setCreateDate(new Date());
            count = cartItemMapper.insert(cartItem);
        } else {
            cartItem.setModifyDate(new Date());
            existCartItem.setQuantity(existCartItem.getQuantity() + cartItem.getQuantity());
            if (SecureKitSwitch.on("server-price")) {
                existCartItem.setPrice(cartItem.getPrice());   // 다온마켓 10-8: 예전에 담긴 가격도 지금 SKU 가격으로
            }
            count = cartItemMapper.updateByPrimaryKey(existCartItem);
        }
        return count;
    }

    /**
     * 다온마켓 10-8: pms_sku_stock·pms_product에서 읽어 장바구니 줄을 채운다. SKU가 없거나 상품과 짝이 맞지 않으면 400.
     * [랩 스위치] server-price를 끄면 가격만 요청 값을 그대로 둔다(이름 등은 서버 값, 영상의 「끄면 1원」).
     */
    private void fillFromSku(OmsCartItem cartItem) {
        PmsSkuStock sku = cartItem.getProductSkuId() == null ? null : skuStockMapper.selectByPrimaryKey(cartItem.getProductSkuId());
        if (sku == null || !sku.getProductId().equals(cartItem.getProductId())) {
            Asserts.fail("상품 규격(SKU)이 맞지 않습니다");
        }
        PmsProduct product = productMapper.selectByPrimaryKey(sku.getProductId());
        if (product == null || !Integer.valueOf(1).equals(product.getPublishStatus()) || Integer.valueOf(1).equals(product.getDeleteStatus())) {
            Asserts.fail("판매하지 않는 상품입니다");
        }
        if (SecureKitSwitch.on("server-price")) {
            cartItem.setPrice(sku.getPrice());
        }
        cartItem.setProductSkuCode(sku.getSkuCode());
        cartItem.setProductAttr(sku.getSpData());
        cartItem.setProductName(product.getName());
        cartItem.setProductSubTitle(product.getSubTitle());
        cartItem.setProductPic(product.getPic());
        cartItem.setProductBrand(product.getBrandName());
        cartItem.setProductSn(product.getProductSn());
        cartItem.setProductCategoryId(product.getProductCategoryId());
    }

    /**
     * 회원 id, 상품 id, 규격으로 장바구니 상품 조회
     */
    private OmsCartItem getCartItem(OmsCartItem cartItem) {
        OmsCartItemExample example = new OmsCartItemExample();
        OmsCartItemExample.Criteria criteria = example.createCriteria().andMemberIdEqualTo(cartItem.getMemberId())
                .andProductIdEqualTo(cartItem.getProductId()).andDeleteStatusEqualTo(0);
        if (cartItem.getProductSkuId()!=null) {
            criteria.andProductSkuIdEqualTo(cartItem.getProductSkuId());
        }
        List<OmsCartItem> cartItemList = cartItemMapper.selectByExample(example);
        if (!CollectionUtils.isEmpty(cartItemList)) {
            return cartItemList.get(0);
        }
        return null;
    }

    @Override
    public List<OmsCartItem> list(Long memberId) {
        OmsCartItemExample example = new OmsCartItemExample();
        example.createCriteria().andDeleteStatusEqualTo(0).andMemberIdEqualTo(memberId);
        return cartItemMapper.selectByExample(example);
    }

    @Override
    public List<CartPromotionItem> listPromotion(Long memberId, List<Long> cartIds) {
        List<OmsCartItem> cartItemList = list(memberId);
        if(CollUtil.isNotEmpty(cartIds)){
            cartItemList = cartItemList.stream().filter(item->cartIds.contains(item.getId())).collect(Collectors.toList());
        }
        List<CartPromotionItem> cartPromotionItemList = new ArrayList<>();
        if(!CollectionUtils.isEmpty(cartItemList)){
            cartPromotionItemList = promotionService.calcCartPromotion(cartItemList);
        }
        return cartPromotionItemList;
    }

    @Override
    public int updateQuantity(Long id, Long memberId, Integer quantity) {
        OmsCartItem cartItem = new OmsCartItem();
        cartItem.setQuantity(quantity);
        OmsCartItemExample example = new OmsCartItemExample();
        example.createCriteria().andDeleteStatusEqualTo(0)
                .andIdEqualTo(id).andMemberIdEqualTo(memberId);
        return cartItemMapper.updateByExampleSelective(cartItem, example);
    }

    @Override
    public int delete(Long memberId, List<Long> ids) {
        OmsCartItem record = new OmsCartItem();
        record.setDeleteStatus(1);
        OmsCartItemExample example = new OmsCartItemExample();
        example.createCriteria().andIdIn(ids).andMemberIdEqualTo(memberId);
        return cartItemMapper.updateByExampleSelective(record, example);
    }

    @Override
    public CartProduct getCartProduct(Long productId) {
        return productDao.getCartProduct(productId);
    }

    @Override
    public int updateAttr(OmsCartItem cartItem) {
        //기존 장바구니 정보 삭제(다온마켓 10-8: 원본은 줄 ID만으로 지워 남의 장바구니 줄도 지울 수 있었다. 내 줄일 때만)
        OmsCartItem updateCart = new OmsCartItem();
        updateCart.setModifyDate(new Date());
        updateCart.setDeleteStatus(1);
        OmsCartItemExample mine = new OmsCartItemExample();
        mine.createCriteria().andIdEqualTo(cartItem.getId()).andMemberIdEqualTo(memberService.getCurrentMember().getId()).andDeleteStatusEqualTo(0);
        if (cartItemMapper.updateByExampleSelective(updateCart, mine) == 0) {
            Asserts.fail("장바구니 줄을 찾을 수 없습니다");
        }
        cartItem.setId(null);
        add(cartItem);
        return 1;
    }

    @Override
    public int clear(Long memberId) {
        OmsCartItem record = new OmsCartItem();
        record.setDeleteStatus(1);
        OmsCartItemExample example = new OmsCartItemExample();
        example.createCriteria().andMemberIdEqualTo(memberId);
        return cartItemMapper.updateByExampleSelective(record,example);
    }
}
