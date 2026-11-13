package com.macro.mall.portal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.config.DaonPayQueueConfig;
import com.macro.mall.portal.service.UmsMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * [교육용 추가 T24] 주문 결제 요청. POST /daon/order/{orderId}/pay?ref=…
 *
 * 결제사를 바로 부르지 않고 결제 요청 큐에 넣는다(202 「결제 대기」). ref는 같은 주문을 여러 번 결제 요청할 때(측정) 구분하는 값.
 * 멱등 키 = pay-&lt;주문 번호&gt;-&lt;ref&gt;. 결제사가 같은 키를 두 번 받으면 한 번만 처리해야 한다(10-4).
 */
@RestController
@Tag(name = "DaonPayController", description = "[교육용 추가 9-4] 주문 결제 요청(큐)")
public class DaonPayController {
    private static final ObjectMapper JSON = new ObjectMapper();
    @Autowired
    private UmsMemberService memberService;
    @Autowired
    private OmsOrderMapper orderMapper;
    @Autowired
    private RabbitTemplate rabbit;

    @Operation(summary = "결제 요청(큐에 넣고 202)")
    @PostMapping("/daon/order/{orderId}/pay")
    public ResponseEntity<CommonResult<Map<String, Object>>> pay(@PathVariable Long orderId,
                                                                 @RequestParam(required = false, defaultValue = "1") String ref) throws Exception {
        if (!ref.matches("[A-Za-z0-9-]{1,40}")) {
            return ResponseEntity.badRequest().body(CommonResult.validateFailed("ref는 영문·숫자·하이픈 40자까지"));
        }
        UmsMember m = memberService.getCurrentMember();
        OmsOrder o = orderMapper.selectByPrimaryKey(orderId);
        if (o == null || !Objects.equals(o.getMemberId(), m.getId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResult.failed("주문이 없습니다"));
        }
        BigDecimal amount = o.getPayAmount() != null && o.getPayAmount().signum() > 0 ? o.getPayAmount()
                : (o.getTotalAmount() != null && o.getTotalAmount().signum() > 0 ? o.getTotalAmount() : BigDecimal.ONE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", o.getId());
        body.put("orderSn", o.getOrderSn());
        body.put("amount", amount);
        body.put("key", "pay-" + o.getOrderSn() + "-" + ref);
        rabbit.send("", DaonPayQueueConfig.QUEUE, MessageBuilder.withBody(JSON.writeValueAsBytes(body))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setHeader(DaonPayQueueConfig.ATTEMPT_HEADER, 0)
                .build());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(CommonResult.success(Map.<String, Object>of("status", "결제 대기", "orderSn", o.getOrderSn())));
    }
}
