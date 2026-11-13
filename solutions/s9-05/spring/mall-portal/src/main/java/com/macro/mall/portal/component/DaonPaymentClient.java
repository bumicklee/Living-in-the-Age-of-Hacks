package com.macro.mall.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.securekit.SecureKitSwitch;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
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
 * [교육용 추가 T24 · 9-5 정답] 결제사(가짜 payment-stub) 결제 요청. POST /v1/payments
 *
 * 9-5에서 바뀐 것
 * - 재시도는 한 곳(큐 소비자)에서만 한다. 여기서는 한 번만 부른다(안에서 바로 다시 하던 3번을 지웠다).
 * - Resilience4j 서킷 브레이커: 최근 10번 중(최소 4번) 실패가 50% 이상이면 20초 동안 결제사를 부르지 않는다(CallNotPermittedException).
 *   20초 뒤 시험 호출 1번이 성공하면 닫힌다. 결제사의 거절(4xx)은 실패로 세지 않는다(잘못된 요청 몇 개로 열리지 않게).
 * - 열리고 닫힐 때 resilience.circuit.opened(WARN)·resilience.circuit.closed(INFO).
 * - 같은 주문을 다시 보낼 때도 같은 멱등 키(Idempotency-Key)를 붙인다. 중복 결제를 막는 쪽(결제사·웹훅)은 10-4.
 * - SecureKitSwitch.on("resilience") 줄은 영상용 랩 스위치다(끄면 9-4의 옛 동작).
 */
@Component
public class DaonPaymentClient {
    static final int TRIES = 3;                       // 옛 동작(스위치를 껐을 때)만 쓴다
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
    private final CircuitBreaker breaker;

    @Value("${daon.payment.url:http://payment-stub:8000}")
    private String baseUrl;

    public DaonPaymentClient() {
        CircuitBreakerConfig cfg = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(20))
                .permittedNumberOfCallsInHalfOpenState(1)
                .ignoreException(e -> e instanceof PaymentFailedException p && p.isRejected())
                .build();
        breaker = CircuitBreaker.of("pay-stub", cfg);
        breaker.getEventPublisher().onStateTransition(ev -> {
            CircuitBreaker.State to = ev.getStateTransition().getToState();
            if (to == CircuitBreaker.State.OPEN) {
                SecurityEventLogger.denied("resilience.circuit.opened", "9-5")
                        .actor("system:pay-worker")
                        .target("dependency:pay-stub")
                        .reason("실패 비율 기준 초과로 차단(" + ev.getStateTransition().getFromState() + " → OPEN, 20초)")
                        .log();
            } else if (to == CircuitBreaker.State.CLOSED) {
                SecurityEventLogger.suspicious("resilience.circuit.closed", "9-5")
                        .actor("system:pay-worker")
                        .target("dependency:pay-stub")
                        .reason("시험 호출 성공, 다시 부름")
                        .log();
            }
        });
    }

    /** 승인되면 결제 번호. 실패하면 PaymentFailedException, 서킷이 열려 있으면 CallNotPermittedException */
    public String pay(String orderSn, BigDecimal amount, String idempotencyKey, String traceId) {
        if (!SecureKitSwitch.on("resilience")) {
            RuntimeException last = null;
            for (int i = 0; i < TRIES; i++) {                   // 옛 동작: 간격 없이 바로 다시
                try {
                    return once(orderSn, amount, idempotencyKey, traceId);
                } catch (RuntimeException e) {
                    last = e;
                }
            }
            throw last;
        }
        return breaker.executeSupplier(() -> once(orderSn, amount, idempotencyKey, traceId));
    }

    public CircuitBreaker.State state() {
        return breaker.getState();
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
