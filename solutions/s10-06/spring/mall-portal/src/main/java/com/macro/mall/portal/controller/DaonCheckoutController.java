package com.macro.mall.portal.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.common.util.ClientIp;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.OmsOrderExample;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.component.DaonCheckoutLimiter;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.security.trace.TraceId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * [다온마켓 10-4·10-6 정답] 결제 시작 POST /pay/start?orderId=…[&amp;cardToken=tok_test_4242]
 *
 * - 고객 웹의 「가짜 결제하기」가 부른다. 결제 완료는 여기서 정하지 않는다. 결제사가 서명한 웹훅(/pay/webhook)만 정한다.
 * - 금액은 서버가 주문에서 읽는다(요청에서 받지 않는다, 10-8과 같은 원칙). 본인 주문이고 결제 대기(0)일 때만.
 * - cardToken은 랩의 가짜 테스트 카드 토큰(stubs/README.md). 카드 번호를 받지도 남기지도 않는다.
 * - 같은 주문은 같은 멱등 키(Idempotency-Key)로 보낸다.
 * - 10-6: 결제사를 부르기 전에 계정·IP·카드 지문 세 축 한도(DaonCheckoutLimiter). 넘으면 429. 결제사 결과는 실패율 창에 더한다.
 *   카드 토큰 모양(tok_test_ + 숫자)도 여기서 먼저 본다(결제사에 이상한 값을 보내지 않는다).
 */
@RestController
@Tag(name = "DaonCheckoutController", description = "[다온마켓 10-4] 결제 시작")
public class DaonCheckoutController {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();

    @Value("${daon.payment.url:http://payment-stub:8000}")
    private String payUrl;
    @Value("${daon.payment.callback-url:http://mall-portal:8085/pay/webhook}")
    private String callbackUrl;
    @Autowired
    private UmsMemberService memberService;
    @Autowired
    private OmsOrderMapper orderMapper;
    @Autowired
    private DaonCheckoutLimiter limiter;

    @Operation(summary = "결제 시작(가짜 결제사로, 결과는 웹훅이 정한다)")
    @PostMapping("/pay/start")
    public ResponseEntity<CommonResult<Map<String, Object>>> start(@RequestParam Long orderId,
                                                                   @RequestParam(required = false) String cardToken,
                                                                   HttpServletRequest request) {
        UmsMember member = memberService.getCurrentMember();
        if (cardToken != null && !cardToken.matches("tok_test_[0-9]{4,16}")) {
            return ResponseEntity.badRequest().body(CommonResult.validateFailed("카드 토큰 형식이 맞지 않습니다"));
        }
        String axis = limiter.check(member.getId(), ClientIp.of(request), cardToken);   // 10-6: 결제사를 부르기 전에
        if (axis != null) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(CommonResult.failed("잠시 후 다시 시도해 주세요"));
        }
        OmsOrder order = ownPendingOrder(member.getId(), orderId);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResult.failed("결제할 수 있는 주문이 없습니다"));
        }
        BigDecimal amount = order.getPayAmount();
        if (amount == null || amount.signum() <= 0) {
            return ResponseEntity.badRequest().body(CommonResult.validateFailed("결제할 금액이 없습니다"));
        }
        PayResult r = callPg(order.getOrderSn(), amount, cardToken);
        if (r.status() == 402 || r.status() / 100 == 2) {
            limiter.record(r.status() == 402);
        }
        return toResponse(r, order.getOrderSn());
    }

    OmsOrder ownPendingOrder(Long memberId, Long orderId) {
        OmsOrderExample ex = new OmsOrderExample();
        ex.createCriteria().andIdEqualTo(orderId).andMemberIdEqualTo(memberId).andStatusEqualTo(0).andDeleteStatusEqualTo(0);
        List<OmsOrder> list = orderMapper.selectByExample(ex);
        return list.isEmpty() ? null : list.get(0);
    }

    /** status: 결제사 HTTP 상태(0 = 닿지 않음) */
    record PayResult(int status, String paymentId, String fingerprint) { }

    PayResult callPg(String orderSn, BigDecimal amount, String cardToken) {
        try {
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("order_sn", orderSn);
            req.put("amount", amount);
            req.put("callback_url", callbackUrl);
            if (cardToken != null && !cardToken.isBlank()) {
                req.put("card_token", cardToken);
            }
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(payUrl + "/v1/payments"))
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", "pay-start-" + orderSn)
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(req)));
            String trace = TraceId.current();
            if (trace != null) {
                b.header("X-Trace-Id", trace);
            }
            HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode j = res.body() == null || res.body().isBlank() ? JSON.createObjectNode() : JSON.readTree(res.body());
            return new PayResult(res.statusCode(), j.path("payment_id").asText(null), j.path("card_fingerprint").asText(null));
        } catch (java.io.IOException e) {
            return new PayResult(0, null, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new PayResult(0, null, null);
        }
    }

    ResponseEntity<CommonResult<Map<String, Object>>> toResponse(PayResult r, String orderSn) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderSn", orderSn);
        if (r.paymentId() != null) {
            data.put("paymentId", r.paymentId());
        }
        if (r.status() / 100 == 2) {
            data.put("status", "승인 요청됨(결제 완료는 결제사 알림으로 바뀝니다)");
            return ResponseEntity.ok(CommonResult.success(data));
        }
        if (r.status() == 402) {
            data.put("status", "카드 거절");
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(CommonResult.failed("카드가 거절되었습니다"));
        }
        if (r.status() / 100 == 4) {
            return ResponseEntity.badRequest().body(CommonResult.validateFailed("결제 요청 형식이 맞지 않습니다"));
        }
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(CommonResult.failed("결제사에 닿지 않습니다. 잠시 뒤 다시 시도해 주세요"));
    }
}
