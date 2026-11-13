package com.macro.mall.portal.service.impl;

import com.macro.mall.model.OmsCartItem;
import com.macro.mall.model.PmsProductFullReduction;
import com.macro.mall.model.PmsProductLadder;
import com.macro.mall.model.PmsSkuStock;
import com.macro.mall.portal.dao.PortalProductDao;
import com.macro.mall.portal.domain.CartPromotionItem;
import com.macro.mall.portal.domain.PromotionProduct;
import com.macro.mall.portal.service.OmsPromotionService;
import com.macro.mall.security.securekit.SecureKitSwitch;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Created by macro on 2018/8/27.
 * 프로모션 관리 Service 구현 클래스
 */
@Service
public class OmsPromotionServiceImpl implements OmsPromotionService {
    @Autowired
    private PortalProductDao portalProductDao;

    @Override
    public List<CartPromotionItem> calcCartPromotion(List<OmsCartItem> cartItemList) {
        //1.먼저 productId로 CartItem을 그룹화하고, spu 단위로 할인 계산
        Map<Long, List<OmsCartItem>> productCartMap = groupCartItemBySpu(cartItemList);
        //2.모든 상품의 할인 관련 정보 조회
        List<PromotionProduct> promotionProductList = getPromotionProductList(cartItemList);
        //3.상품 프로모션 유형에 따라 프로모션 할인 가격 계산
        List<CartPromotionItem> cartPromotionItemList = new ArrayList<>();
        for (Map.Entry<Long, List<OmsCartItem>> entry : productCartMap.entrySet()) {
            Long productId = entry.getKey();
            PromotionProduct promotionProduct = getPromotionProductById(productId, promotionProductList);
            List<OmsCartItem> itemList = entry.getValue();
            Integer promotionType = promotionProduct.getPromotionType();
            if (promotionType == 1) {
                //단품 프로모션
                for (OmsCartItem item : itemList) {
                    CartPromotionItem cartPromotionItem = new CartPromotionItem();
                    BeanUtils.copyProperties(item,cartPromotionItem);
                    cartPromotionItem.setPromotionMessage("단품 프로모션");
                    //상품 정가-프로모션 가격
                    PmsSkuStock skuStock = getOriginalPrice(promotionProduct, item.getProductSkuId());
                    BigDecimal originalPrice = skuStock.getPrice();
                    //단품 프로모션 시 정가 사용
                    cartPromotionItem.setPrice(originalPrice);
                    cartPromotionItem.setReduceAmount(originalPrice.subtract(skuStock.getPromotionPrice()));
                    cartPromotionItem.setRealStock(skuStock.getStock()-skuStock.getLockStock());
                    cartPromotionItem.setIntegration(promotionProduct.getGiftPoint());
                    cartPromotionItem.setGrowth(promotionProduct.getGiftGrowth());
                    cartPromotionItemList.add(cartPromotionItem);
                }
            } else if (promotionType == 3) {
                //할인 혜택
                int count = getCartItemCount(itemList);
                PmsProductLadder ladder = getProductLadder(count, promotionProduct.getProductLadderList());
                if(ladder!=null){
                    for (OmsCartItem item : itemList) {
                        CartPromotionItem cartPromotionItem = new CartPromotionItem();
                        BeanUtils.copyProperties(item,cartPromotionItem);
                        String message = getLadderPromotionMessage(ladder);
                        cartPromotionItem.setPromotionMessage(message);
                        //상품 정가-할인율*상품 정가
                        PmsSkuStock skuStock = getOriginalPrice(promotionProduct,item.getProductSkuId());
                        BigDecimal originalPrice = skuStock.getPrice();
                        serverPrice(cartPromotionItem, skuStock);   //다온마켓 10-8: 계단 할인 충족 갈래도 SKU 가격으로(원본은 요청 가격 → 금액이 음수가 될 수 있었다)
                        BigDecimal reduceAmount = originalPrice.subtract(ladder.getDiscount().multiply(originalPrice));
                        cartPromotionItem.setReduceAmount(reduceAmount);
                        cartPromotionItem.setRealStock(skuStock.getStock()-skuStock.getLockStock());
                        cartPromotionItem.setIntegration(promotionProduct.getGiftPoint());
                        cartPromotionItem.setGrowth(promotionProduct.getGiftGrowth());
                        cartPromotionItemList.add(cartPromotionItem);
                    }
                }else{
                    handleNoReduce(cartPromotionItemList,itemList,promotionProduct);
                }
            } else if (promotionType == 4) {
                //금액 할인
                BigDecimal totalAmount= getCartItemAmount(itemList,promotionProductList);
                PmsProductFullReduction fullReduction = getProductFullReduction(totalAmount,promotionProduct.getProductFullReductionList());
                if(fullReduction!=null){
                    for (OmsCartItem item : itemList) {
                        CartPromotionItem cartPromotionItem = new CartPromotionItem();
                        BeanUtils.copyProperties(item,cartPromotionItem);
                        String message = getFullReductionPromotionMessage(fullReduction);
                        cartPromotionItem.setPromotionMessage(message);
                        //(상품 정가/총액)*금액 할인액
                        PmsSkuStock skuStock= getOriginalPrice(promotionProduct, item.getProductSkuId());
                        BigDecimal originalPrice = skuStock.getPrice();
                        serverPrice(cartPromotionItem, skuStock);   //다온마켓 10-8: 만원 할인 충족 갈래도 SKU 가격으로
                        BigDecimal reduceAmount = originalPrice.divide(totalAmount,RoundingMode.HALF_EVEN).multiply(fullReduction.getReducePrice());
                        cartPromotionItem.setReduceAmount(reduceAmount);
                        cartPromotionItem.setRealStock(skuStock.getStock()-skuStock.getLockStock());
                        cartPromotionItem.setIntegration(promotionProduct.getGiftPoint());
                        cartPromotionItem.setGrowth(promotionProduct.getGiftGrowth());
                        cartPromotionItemList.add(cartPromotionItem);
                    }
                }else{
                    handleNoReduce(cartPromotionItemList,itemList,promotionProduct);
                }
            } else {
                //할인 없음
                handleNoReduce(cartPromotionItemList, itemList,promotionProduct);
            }
        }
        return cartPromotionItemList;
    }

    /**
     * 모든 상품의 할인 관련 정보 조회
     */
    private List<PromotionProduct> getPromotionProductList(List<OmsCartItem> cartItemList) {
        List<Long> productIdList = new ArrayList<>();
        for(OmsCartItem cartItem:cartItemList){
            productIdList.add(cartItem.getProductId());
        }
        return portalProductDao.getPromotionProductList(productIdList);
    }

    /**
     * SPU 단위로 장바구니 상품 그룹화
     */
    private Map<Long, List<OmsCartItem>> groupCartItemBySpu(List<OmsCartItem> cartItemList) {
        Map<Long, List<OmsCartItem>> productCartMap = new TreeMap<>();
        for (OmsCartItem cartItem : cartItemList) {
            List<OmsCartItem> productCartItemList = productCartMap.get(cartItem.getProductId());
            if (productCartItemList == null) {
                productCartItemList = new ArrayList<>();
                productCartItemList.add(cartItem);
                productCartMap.put(cartItem.getProductId(), productCartItemList);
            } else {
                productCartItemList.add(cartItem);
            }
        }
        return productCartMap;
    }

    /**
     * 금액 할인 프로모션 메시지 조회
     */
    private String getFullReductionPromotionMessage(PmsProductFullReduction fullReduction) {
        StringBuilder sb = new StringBuilder();
        sb.append("금액 할인: ");
        sb.append(fullReduction.getFullPrice());
        sb.append("원 이상 구매 시 ");
        sb.append(fullReduction.getReducePrice());
        sb.append("원 할인");
        return sb.toString();
    }

    /**
     * 할인 조건을 충족하지 못한 상품 처리
     */
    private void handleNoReduce(List<CartPromotionItem> cartPromotionItemList, List<OmsCartItem> itemList,PromotionProduct promotionProduct) {
        for (OmsCartItem item : itemList) {
            CartPromotionItem cartPromotionItem = new CartPromotionItem();
            BeanUtils.copyProperties(item,cartPromotionItem);
            cartPromotionItem.setPromotionMessage("할인 없음");
            cartPromotionItem.setReduceAmount(new BigDecimal(0));
            PmsSkuStock skuStock = getOriginalPrice(promotionProduct,item.getProductSkuId());
            if(skuStock!=null){
                cartPromotionItem.setRealStock(skuStock.getStock()-skuStock.getLockStock());
                serverPrice(cartPromotionItem, skuStock);   //다온마켓 10-8: 할인 없음·조건 미달 갈래도 SKU 가격으로(F45의 자리)
            }
            cartPromotionItem.setIntegration(promotionProduct.getGiftPoint());
            cartPromotionItem.setGrowth(promotionProduct.getGiftGrowth());
            cartPromotionItemList.add(cartPromotionItem);
        }
    }

    /**
     * 다온마켓 10-8: 주문 확인(generateConfirmOrder)과 주문 생성(generateOrder)이 모두 지나는 곳. 모든 갈래에서 가격을 SKU에서 다시 정한다.
     * 단품 할인 갈래가 원래 하던 일(setPrice(originalPrice))을 나머지 갈래에도 똑같이 한다. 장바구니에 오래전에 담긴 가격도 여기서 바로잡힌다.
     * [랩 스위치] server-price를 끄면 고치기 전 모습(장바구니 가격을 그대로).
     */
    private void serverPrice(CartPromotionItem cartPromotionItem, PmsSkuStock skuStock) {
        if (skuStock != null && skuStock.getPrice() != null && SecureKitSwitch.on("server-price")) {
            cartPromotionItem.setPrice(skuStock.getPrice());
        }
    }

    private PmsProductFullReduction getProductFullReduction(BigDecimal totalAmount,List<PmsProductFullReduction> fullReductionList) {
        //조건 높은 순 정렬
        fullReductionList.sort(new Comparator<PmsProductFullReduction>() {
            @Override
            public int compare(PmsProductFullReduction o1, PmsProductFullReduction o2) {
                return o2.getFullPrice().subtract(o1.getFullPrice()).intValue();
            }
        });
        for(PmsProductFullReduction fullReduction:fullReductionList){
            if(totalAmount.subtract(fullReduction.getFullPrice()).intValue()>=0){
                return fullReduction;
            }
        }
        return null;
    }

    /**
     * 할인 프로모션 정보 조회
     */
    private String getLadderPromotionMessage(PmsProductLadder ladder) {
        StringBuilder sb = new StringBuilder();
        sb.append("수량 할인: ");
        sb.append(ladder.getCount());
        sb.append("개 이상 구매 시 정가의 ");
        sb.append(ladder.getDiscount().multiply(new BigDecimal(10)));
        sb.append("할 가격");
        return sb.toString();
    }

    /**
     * 구매 상품 수량에 맞는 할인 정책 조회
     */
    private PmsProductLadder getProductLadder(int count, List<PmsProductLadder> productLadderList) {
        //수량 많은 순 정렬
        productLadderList.sort(new Comparator<PmsProductLadder>() {
            @Override
            public int compare(PmsProductLadder o1, PmsProductLadder o2) {
                return o2.getCount() - o1.getCount();
            }
        });
        for (PmsProductLadder productLadder : productLadderList) {
            if (count >= productLadder.getCount()) {
                return productLadder;
            }
        }
        return null;
    }

    /**
     * 장바구니 내 지정 상품의 수량 조회
     */
    private int getCartItemCount(List<OmsCartItem> itemList) {
        int count = 0;
        for (OmsCartItem item : itemList) {
            count += item.getQuantity();
        }
        return count;
    }

    /**
     * 장바구니 내 지정 상품의 총 금액 조회
     */
    private BigDecimal getCartItemAmount(List<OmsCartItem> itemList, List<PromotionProduct> promotionProductList) {
        BigDecimal amount = new BigDecimal(0);
        for (OmsCartItem item : itemList) {
            //상품 정가 계산
            PromotionProduct promotionProduct = getPromotionProductById(item.getProductId(), promotionProductList);
            PmsSkuStock skuStock = getOriginalPrice(promotionProduct,item.getProductSkuId());
            amount = amount.add(skuStock.getPrice().multiply(new BigDecimal(item.getQuantity())));
        }
        return amount;
    }

    /**
     * 상품 정가 조회
     */
    private PmsSkuStock getOriginalPrice(PromotionProduct promotionProduct, Long productSkuId) {
        for (PmsSkuStock skuStock : promotionProduct.getSkuStockList()) {
            if (productSkuId.equals(skuStock.getId())) {
                return skuStock;
            }
        }
        return null;
    }

    /**
     * 상품 id로 상품 프로모션 정보 조회
     */
    private PromotionProduct getPromotionProductById(Long productId, List<PromotionProduct> promotionProductList) {
        for (PromotionProduct promotionProduct : promotionProductList) {
            if (productId.equals(promotionProduct.getId())) {
                return promotionProduct;
            }
        }
        return null;
    }
}
