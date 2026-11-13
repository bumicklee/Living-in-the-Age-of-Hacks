package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.mapper.PmsSkuStockMapper;
import com.macro.mall.model.OmsCartItem;
import com.macro.mall.model.PmsSkuStock;
import com.macro.mall.portal.domain.CartAddParam;
import com.macro.mall.portal.domain.CartProduct;
import com.macro.mall.portal.domain.CartPromotionItem;
import com.macro.mall.portal.service.OmsCartItemService;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.securekit.SecureKitSwitch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 장바구니 관리Controller
 * Created by macro on 2018/8/2.
 */
@Controller
@Tag(name = "OmsCartItemController", description = "장바구니 관리")
@RequestMapping("/cart")
public class OmsCartItemController {
    /** [다온마켓 9-3 정답, F42] 장바구니 수량 범위. 정상 화면은 1~99만 보낸다 */
    static final int MIN_QTY = 1;
    static final int MAX_QTY = 99;
    @Autowired
    private OmsCartItemService cartItemService;
    @Autowired
    private UmsMemberService memberService;
    @Autowired
    private PmsSkuStockMapper skuStockMapper;

    /**
     * 다온마켓 10-8: 요청 전용 DTO(CartAddParam)로 상품·SKU·수량만 받는다. 가격·이름은 서비스가 SKU에서 채운다(F45).
     * 요청에 가격이 섞여 왔고 SKU 가격과 다르면 price.tampered(WARN)를 남기고 서버 가격으로 담는다(거절은 주문 직전 확인에서만, 결정 #11).
     */
    @Operation(summary = "장바구니에 상품 추가")
    @RequestMapping(value = "/add", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<CommonResult> add(@RequestBody CartAddParam param) {
        if (!SecureKitSwitch.on("server-price")) {
            return addUnchecked(param);               // [랩 스위치] 끄면 고치기 전 모습(요청 가격을 그대로 담는다)
        }
        ResponseEntity<CommonResult> bad = checkQuantity(param.getQuantity());
        if (bad != null) {
            return bad;
        }
        if (param.getProductId() == null || param.getProductSkuId() == null) {
            return ResponseEntity.badRequest().body(CommonResult.validateFailed("상품과 규격(SKU)을 고르세요"));
        }
        noteTamper(param);
        int count = cartItemService.add(toItem(param));
        if (count > 0) {
            return ResponseEntity.ok(CommonResult.success(count));
        }
        return ResponseEntity.ok(CommonResult.failed());
    }

    @Operation(summary = "현재 회원의 장바구니 목록 조회")
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<List<OmsCartItem>> list() {
        List<OmsCartItem> cartItemList = cartItemService.list(memberService.getCurrentMember().getId());
        return CommonResult.success(cartItemList);
    }

    @Operation(summary = "현재 회원의 장바구니 목록 조회(프로모션 정보 포함)")
    @RequestMapping(value = "/list/promotion", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<List<CartPromotionItem>> listPromotion(@RequestParam(required = false) List<Long> cartIds) {
        List<CartPromotionItem> cartPromotionItemList = cartItemService.listPromotion(memberService.getCurrentMember().getId(), cartIds);
        return CommonResult.success(cartPromotionItemList);
    }

    @Operation(summary = "장바구니의 지정 상품 수량 수정")
    @RequestMapping(value = "/update/quantity", method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<CommonResult> updateQuantity(@RequestParam Long id,
                                                       @RequestParam Integer quantity) {
        ResponseEntity<CommonResult> bad = checkQuantity(quantity);
        if (bad != null) {
            return bad;
        }
        int count = cartItemService.updateQuantity(id, memberService.getCurrentMember().getId(), quantity);
        if (count > 0) {
            return ResponseEntity.ok(CommonResult.success(count));
        }
        return ResponseEntity.ok(CommonResult.failed());
    }

    @Operation(summary = "장바구니 내 지정 상품의 규격 조회(규격 재선택용)")
    @RequestMapping(value = "/getProduct/{productId}", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<CartProduct> getCartProduct(@PathVariable Long productId) {
        CartProduct cartProduct = cartItemService.getCartProduct(productId);
        return CommonResult.success(cartProduct);
    }

    @Operation(summary = "장바구니 상품 규격 수정")
    @RequestMapping(value = "/update/attr", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult updateAttr(@RequestBody CartAddParam param) {
        // 다온마켓 10-8: 규격 바꾸기도 같은 DTO. 지우는 줄은 서비스가 「내 줄」인지 보고 지운다(F09 확장)
        if (SecureKitSwitch.on("server-price")) {
            if (param.getId() == null || param.getProductId() == null || param.getProductSkuId() == null
                    || checkQuantity(param.getQuantity()) != null) {
                return CommonResult.validateFailed("장바구니 줄·상품·규격·수량(1~99)을 확인하세요");
            }
            noteTamper(param);
        }
        OmsCartItem cartItem = toItem(param);
        cartItem.setId(param.getId());
        if (!SecureKitSwitch.on("server-price")) {
            cartItem.setPrice(param.getPrice());
        }
        int count = cartItemService.updateAttr(cartItem);
        if (count > 0) {
            return CommonResult.success(count);
        }
        return CommonResult.failed();
    }

    @Operation(summary = "장바구니의 지정 상품 삭제")
    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult delete(@RequestParam("ids") List<Long> ids) {
        int count = cartItemService.delete(memberService.getCurrentMember().getId(), ids);
        if (count > 0) {
            return CommonResult.success(count);
        }
        return CommonResult.failed();
    }

    @Operation(summary = "현재 회원의 장바구니 비우기")
    @RequestMapping(value = "/clear", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult clear() {
        int count = cartItemService.clear(memberService.getCurrentMember().getId());
        if (count > 0) {
            return CommonResult.success(count);
        }
        return CommonResult.failed();
    }

    private static OmsCartItem toItem(CartAddParam param) {
        OmsCartItem item = new OmsCartItem();
        item.setProductId(param.getProductId());
        item.setProductSkuId(param.getProductSkuId());
        item.setQuantity(param.getQuantity());
        return item;
    }

    /** 10-8 ⑤ 탐지: 요청이 보낸 가격 값은 넣지 않는다(SECURITY_EVENTS 「보낸 값」 규칙). 조작 사실과 SKU만 */
    private void noteTamper(CartAddParam param) {
        if (param.getPrice() == null || param.getProductSkuId() == null) {
            return;
        }
        PmsSkuStock sku = skuStockMapper.selectByPrimaryKey(param.getProductSkuId());
        if (sku != null && sku.getPrice() != null && sku.getPrice().compareTo(param.getPrice()) != 0) {
            SecurityEventLogger.denied("price.tampered", "10-8")
                    .actor("member:" + memberService.getCurrentMember().getId())
                    .target("sku:" + sku.getId())
                    .reason("요청 가격이 SKU 가격과 다름(서버 가격으로 처리)")
                    .log();
        }
    }

    /** 고치기 전 모습(스위치를 껐을 때만): 요청 본문의 가격을 그대로 장바구니에 */
    private ResponseEntity<CommonResult> addUnchecked(CartAddParam param) {
        ResponseEntity<CommonResult> bad = checkQuantity(param.getQuantity());
        if (bad != null) {
            return bad;
        }
        OmsCartItem item = toItem(param);
        item.setPrice(param.getPrice());
        int count = cartItemService.add(item);
        return count > 0 ? ResponseEntity.ok(CommonResult.success(count)) : ResponseEntity.ok(CommonResult.failed());
    }

    /** 수량이 비었거나 1~99 밖이면 400. 정상 화면이 보내지 않는 값이라 INFO 이벤트(input.denied.quantity)로 남긴다 */
    private ResponseEntity<CommonResult> checkQuantity(Integer quantity) {
        if (!SecureKitSwitch.on("input-limits") || (quantity != null && quantity >= MIN_QTY && quantity <= MAX_QTY)) {
            return null;
        }
        SecurityEventLogger.suspicious("input.denied.quantity", "9-3")
                .target("field:quantity")
                .reason("수량 범위(" + MIN_QTY + "~" + MAX_QTY + ") 밖")
                .log();
        return ResponseEntity.badRequest().body(CommonResult.validateFailed("수량은 " + MIN_QTY + "~" + MAX_QTY + "개입니다"));
    }
}
