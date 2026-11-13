package com.macro.mall.portal.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.portal.component.DaonWebhookVerifier;
import com.macro.mall.portal.service.OmsPortalOrderService;
import com.macro.mall.portal.service.impl.DaonPaymentService;
import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.securekit.SecureKitSwitch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

/**
 * [다온마켓 10-4 정답] 결제사 웹훅 수신 POST /pay/webhook. 로그인 없이 열린 경로는 이것 하나다(application.yml).
 *
 * 검사 순서: ① 서명 → ② 중복 → ③ 금액 → ④ 상태 전이(DaonPaymentService).
 * 서명이 먼저다. 중복 검사를 먼저 하면 위조 웹훅이 이벤트 ID를 먼저 차지해 진짜 웹훅을 「중복」으로 만들 수 있다.
 * 중복 웹훅에는 200을 준다(처리는 하지 않음). 4xx를 주면 결제사가 계속 다시 보낸다.
 */
@RestController
@Tag(name = "DaonPaymentWebhookController", description = "[다온마켓 10-4] 결제사 웹훅")
public class DaonPaymentWebhookController {
    private static final ObjectMapper JSON = new ObjectMapper();
    @Autowired
    private DaonWebhookVerifier verifier;
    @Autowired
    private DaonPaymentService paymentService;
    @Autowired
    private OmsPortalOrderService portalOrderService;

    @Operation(summary = "결제사 웹훅(서명 X-Daon-Signature)")
    @PostMapping("/pay/webhook")
    public ResponseEntity<Map<String, Object>> webhook(@RequestBody(required = false) byte[] body,
                                                       @RequestHeader(value = "X-Daon-Signature", required = false) String signature) {
        if (!SecureKitSwitch.on("pay-webhook")) {
            return unchecked(body);                   // [랩 스위치] 끄면 서명·중복·금액·상태를 보지 않는다(영상의 「끄면 레드」)
        }
        boolean ok;
        try {
            ok = verifier.valid(body, signature);    // ① 서명: 받은 바이트 그대로
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.<String, Object>of("accepted", false, "reason", "서명을 확인할 수 없음"));
        }
        if (!ok) {
            SecurityEventLogger.denied("payment.denied.bad_signature", "10-4")
                    .actor("anonymous")
                    .target("path:/pay/webhook")
                    .reason("웹훅 서명 불일치")
                    .log();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.<String, Object>of("accepted", false, "reason", "서명 불일치"));
        }
        JsonNode ev;
        try {
            ev = JSON.readTree(body);
        } catch (java.io.IOException e) {
            return ResponseEntity.badRequest().body(Map.<String, Object>of("accepted", false, "reason", "본문 형식"));
        }
        String event = ev.path("event").asText(""), paymentId = ev.path("payment_id").asText(""), orderSn = ev.path("order_sn").asText("");
        if (!event.matches("[a-z._]{1,40}") || !paymentId.matches("[A-Za-z0-9_-]{1,40}") || !orderSn.matches("[A-Za-z0-9]{1,40}")
                || !ev.path("amount").isNumber()) {
            return ResponseEntity.badRequest().body(Map.<String, Object>of("accepted", false, "reason", "필수 값 없음"));
        }
        BigDecimal amount = ev.path("amount").decimalValue();
        DaonPaymentService.Outcome out = paymentService.apply(event, paymentId, orderSn, amount);
        return switch (out) {
            case APPLIED, DUPLICATE, IGNORED -> ResponseEntity.ok(Map.<String, Object>of("accepted", true, "result", out.name().toLowerCase()));
            case NO_ORDER -> ResponseEntity.ok(Map.<String, Object>of("accepted", false, "result", "no_order"));
            case AMOUNT_MISMATCH -> ResponseEntity.unprocessableEntity().body(Map.<String, Object>of("accepted", false, "result", "amount_mismatch"));
            case BAD_TRANSITION -> ResponseEntity.status(HttpStatus.CONFLICT).body(Map.<String, Object>of("accepted", false, "result", "bad_transition"));
        };
    }

    /** 고치기 전 모습(스위치를 껐을 때만): 본문의 주문 번호를 그대로 결제 완료로 */
    private ResponseEntity<Map<String, Object>> unchecked(byte[] body) {
        try {
            JsonNode ev = JSON.readTree(body);
            portalOrderService.paySuccessByOrderSn(ev.path("order_sn").asText(), 1);
            return ResponseEntity.ok(Map.<String, Object>of("accepted", true, "result", "unchecked"));
        } catch (java.io.IOException | RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.<String, Object>of("accepted", false));
        }
    }
}
