package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.component.DaonPaymentGateway;
import com.macro.mall.portal.service.UmsMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

/**
 * [교육용 추가] 다온마켓 14장 시작 모습: 결제 상태 확인 GET /pay/status?orderId=…
 *
 * 고객 웹이 「가짜 결제하기」 뒤 몇 초마다 부른다. 본인 주문만(2-2와 같은 원칙). 결제사에 묻는 일은 DaonPaymentGateway가 한다.
 * 14-3: 결제사가 느려지면 이 요청이 몰려 요청 처리 스레드를 붙잡는다(재연 make replay T=14-3).
 */
@RestController
@Tag(name = "DaonPayStatusController", description = "[교육용 추가 14장] 결제 상태 확인")
public class DaonPayStatusController {
    @Autowired
    private UmsMemberService memberService;
    @Autowired
    private OmsOrderMapper orderMapper;
    @Autowired
    private DaonPaymentGateway gateway;

    @Operation(summary = "결제 상태 확인(결제사에 묻는다)")
    @GetMapping("/pay/status")
    public ResponseEntity<CommonResult<Map<String, Object>>> status(@RequestParam Long orderId) {
        UmsMember member = memberService.getCurrentMember();
        OmsOrder order = orderMapper.selectByPrimaryKey(orderId);
        if (order == null || !Objects.equals(order.getMemberId(), member.getId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResult.failed("주문이 없습니다"));
        }
        return ResponseEntity.ok(CommonResult.success(gateway.status(order.getOrderSn())));
    }
}
