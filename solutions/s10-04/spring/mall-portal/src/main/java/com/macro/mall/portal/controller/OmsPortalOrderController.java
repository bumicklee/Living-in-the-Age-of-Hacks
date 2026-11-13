package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.domain.ConfirmOrderResult;
import com.macro.mall.portal.domain.OmsOrderDetail;
import com.macro.mall.portal.domain.OrderParam;
import com.macro.mall.portal.service.OmsPortalOrderService;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.securekit.SecureKitSwitch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 주문 관리Controller
 * Created by macro on 2018/8/30.
 */
@Controller
@Tag(name = "OmsPortalOrderController", description = "주문 관리")
@RequestMapping("/order")
public class OmsPortalOrderController {
    @Autowired
    private OmsPortalOrderService portalOrderService;
    @Autowired
    private UmsMemberService memberService;

    @Operation(summary = "장바구니 정보로 주문 확인서 생성")
    @RequestMapping(value = "/generateConfirmOrder", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult<ConfirmOrderResult> generateConfirmOrder(@RequestBody List<Long> cartIds) {
        ConfirmOrderResult confirmOrderResult = portalOrderService.generateConfirmOrder(cartIds);
        return CommonResult.success(confirmOrderResult);
    }

    @Operation(summary = "장바구니 정보로 주문 생성")
    @RequestMapping(value = "/generateOrder", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult generateOrder(@RequestBody OrderParam orderParam) {
        Map<String, Object> result = portalOrderService.generateOrder(orderParam);
        return CommonResult.success(result, "주문 성공");
    }

    /**
     * 다온마켓 10-4: 고객이 부르는 「결제 성공」을 없앴다(F02). 결제 완료는 결제사가 서명한 웹훅(/pay/webhook)만 정한다.
     * 지운 자리에 얇은 거절 처리기를 남겨 둔다: 정상 고객 웹은 더 이상 부르지 않으므로, 부르면 탐지 신호다.
     */
    @Operation(summary = "(없앰) 사용자 결제 성공 콜백 → 410")
    @RequestMapping(value = "/paySuccess", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<CommonResult> paySuccess(@RequestParam(required = false) Long orderId, @RequestParam(required = false) Integer payType) {
        if (!SecureKitSwitch.on("pay-webhook")) {
            Integer count = portalOrderService.paySuccess(orderId, payType);   // [랩 스위치] 끄면 고치기 전 모습
            return ResponseEntity.ok(CommonResult.success(count, "결제 성공"));
        }
        SecurityEventLogger.denied("payment.denied.client_call", "10-4")
                .actor("member:" + memberService.getCurrentMember().getId())
                .target("order:" + orderId)
                .reason("없앤 결제 성공 API를 회원이 부름(결제 완료는 웹훅만)")
                .log();
        return ResponseEntity.status(HttpStatus.GONE).body(CommonResult.failed("이 결제 방식은 더 이상 쓰지 않습니다. 결제하기를 다시 눌러 주세요"));
    }

    @Operation(summary = "만료된 주문 자동 취소")
    @RequestMapping(value = "/cancelTimeOutOrder", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult cancelTimeOutOrder() {
        portalOrderService.cancelTimeOutOrder();
        return CommonResult.success(null);
    }

    @Operation(summary = "기한 초과 주문 단건 취소")
    @RequestMapping(value = "/cancelOrder", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult cancelOrder(Long orderId) {
        portalOrderService.sendDelayMessageCancelOrder(orderId);
        return CommonResult.success(null);
    }

    @Operation(summary = "상태별로 사용자 주문 목록을 페이지 단위로 조회")
    @Parameter(name = "status", description = "주문 상태:-1->전체;0->결제 대기;1->배송 대기;2->발송됨;3->완료;4->닫힘",
            in = ParameterIn.QUERY, schema = @Schema(type = "integer",defaultValue = "-1",allowableValues = {"-1","0","1","2","3","4"}))
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<CommonPage<OmsOrderDetail>> list(@RequestParam Integer status,
                                                   @RequestParam(required = false, defaultValue = "1") Integer pageNum,
                                                   @RequestParam(required = false, defaultValue = "5") Integer pageSize) {
        CommonPage<OmsOrderDetail> orderPage = portalOrderService.list(status,pageNum,pageSize);
        return CommonResult.success(orderPage);
    }

    @Operation(summary = "ID로 주문 상세 조회")
    @RequestMapping(value = "/detail/{orderId}", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<OmsOrderDetail> detail(@PathVariable Long orderId) {
        OmsOrderDetail orderDetail = portalOrderService.detail(orderId);
        return CommonResult.success(orderDetail);
    }

    @Operation(summary = "사용자 주문 취소")
    @RequestMapping(value = "/cancelUserOrder", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult cancelUserOrder(Long orderId) {
        portalOrderService.cancelOrder(orderId);
        return CommonResult.success(null);
    }

    @Operation(summary = "사용자 수취 확인")
    @RequestMapping(value = "/confirmReceiveOrder", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult confirmReceiveOrder(Long orderId) {
        portalOrderService.confirmReceiveOrder(orderId);
        return CommonResult.success(null);
    }

    @Operation(summary = "사용자 주문 삭제")
    @RequestMapping(value = "/deleteOrder", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult deleteOrder(Long orderId) {
        portalOrderService.deleteOrder(orderId);
        return CommonResult.success(null);
    }
}
