package com.macro.mall.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * [교육용 추가 T24] 결제사(가짜 payment-stub) 결제 요청. POST /v1/payments
 *
 * 9-4 시작 상태: 시간 제한 1초, 실패하면 간격 없이 바로 다시(모두 3번). 받는 쪽 큐 소비자도 실패하면 다시 넣는다(3번).
 * 두 계층의 재시도가 곱해져 결제사가 느려지면 요청이 몇 배로 늘어난다.
 */
@Component
public class DaonPaymentClient {
    static final int TRIES = 3;
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();

    @Value("${daon.payment.url:http://payment-stub:8000}")
    private String baseUrl;

    /** 승인되면 결제 번호. 안 되면 PaymentFailedException */
    public String pay(String orderSn, BigDecimal amount, String idempotencyKey, String traceId) {
        RuntimeException last = null;
        for (int i = 0; i < TRIES; i++) {                       // 간격 없이 바로 다시
            try {
                return once(orderSn, amount, idempotencyKey, traceId);
            } catch (RuntimeException e) {
                last = e;
            }
        }
        throw last;
    }

    String once(String orderSn, BigDecimal amount, String idempotencyKey, String traceId) {
        try {
            String body = JSON.writeValueAsString(Map.of("order_sn", orderSn, "amount", amount));
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/payments"))
                    .timeout(Duration.ofSeconds(1))
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", idempotencyKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (traceId != null) {
                b.header("X-Trace-Id", traceId);
            }
            HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            int code = res.statusCode();
            if (code / 100 == 2) {
                return JSON.readTree(res.body()).path("payment_id").asText();
            }
            throw new PaymentFailedException("결제사 응답 HTTP " + code, code / 100 == 4);
        } catch (IOException e) {
            throw new PaymentFailedException("결제사에 닿지 않거나 시간 초과(" + e.getClass().getSimpleName() + ")", false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentFailedException("중단됨", false);
        }
    }

    /** rejected=true: 결제사가 요청을 거절(4xx). 다시 보내도 결과가 같다 */
    public static class PaymentFailedException extends RuntimeException {
        private final boolean rejected;

        public PaymentFailedException(String message, boolean rejected) {
            super(message);
            this.rejected = rejected;
        }

        public boolean isRejected() {
            return rejected;
        }
    }
}
