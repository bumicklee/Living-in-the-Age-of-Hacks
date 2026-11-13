package com.macro.mall.portal.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.securekit.DegradedMode;
import com.macro.mall.security.securekit.SecureKitSwitch;
import com.macro.mall.security.trace.TraceId;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * [다온마켓 14-3 정답] 결제사에 결제 상태를 묻는 곳. 결제사가 「오류 없이 느려지기만」 해도 다른 기능이 멈추지 않게 칸막이를 둔다.
 *
 * ① 벌크헤드(칸막이): 결제사를 동시에 기다릴 수 있는 자리는 SLOTS(6)개. 자리가 없으면 기다리지 않고(대기 0) 바로 「확인 중」으로 답한다.
 *    요청 처리 스레드(랩 24개)가 결제사 응답을 기다리느라 모두 묶이는 일이 없다 → 상품 목록은 그대로 뜬다.
 * ② 느린 호출 비율로 여는 서킷 브레이커: 최근 10번 중(최소 6번) 2초를 넘긴 호출이 절반 이상이면 20초 동안 부르지 않는다.
 *    9-5 브레이커는 「실패 비율」만 봤다. 오늘은 「느림」도 고장으로 센다(시간 제한도 5초 → 2.5초).
 * ③ 축소 운영(DegradedMode)이면 결제사를 부르지 않고 바로 「확인 중」.
 * 넘친 횟수는 resilience.bulkhead.rejected(INFO)로 10초에 한 줄씩 묶어 남긴다(count = 그 사이 넘친 횟수). 사람이 축소 운영을 켜기 전에 먼저 오른다.
 * SecureKitSwitch.on("degraded-mode") 줄은 영상용 랩 스위치다(끄면 14-3 시작 모습: 칸막이 없이 5초까지 기다린다).
 */
@Component
public class DaonPaymentGateway {
    static final int SLOTS = 6;
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
    private final Semaphore slots = new Semaphore(SLOTS);
    private final AtomicInteger rejected = new AtomicInteger();
    private final AtomicLong lastLog = new AtomicLong();
    private final CircuitBreaker breaker;

    @Value("${daon.payment.url:http://payment-stub:8000}")
    private String baseUrl;
    @Autowired
    private DegradedMode degradedMode;

    public DaonPaymentGateway() {
        CircuitBreakerConfig cfg = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(6)
                .slowCallDurationThreshold(Duration.ofSeconds(2))
                .slowCallRateThreshold(50)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(20))
                .permittedNumberOfCallsInHalfOpenState(1)
                .build();
        breaker = CircuitBreaker.of("pay-status", cfg);
        breaker.getEventPublisher().onStateTransition(ev -> {
            if (ev.getStateTransition().getToState() == CircuitBreaker.State.OPEN) {
                SecurityEventLogger.denied("resilience.circuit.opened", "14-3")
                        .actor("system:mall-portal")
                        .target("dependency:pay-stub")
                        .reason("느린 호출 비율 기준 초과로 결제 확인을 20초 멈춤")
                        .log();
            }
        });
    }

    /** 결제사가 아는 이 주문의 결제 상태. 칸막이·브레이커·축소 운영이면 status = PENDING_CHECK(「확인 중」) */
    public Map<String, Object> status(String orderSn) {
        if (!SecureKitSwitch.on("degraded-mode")) {
            return call(orderSn, Duration.ofSeconds(5));                  // [랩 스위치] 14-3 시작 모습
        }
        if (degradedMode.isOn(DegradedMode.PAYMENT)) {
            return pending(orderSn, "축소 운영 중이라 결제 확인을 잠시 멈췄습니다");
        }
        if (!slots.tryAcquire()) {                                        // 기다리지 않는다
            noteRejected();
            return pending(orderSn, "결제 확인이 늦어지고 있습니다");
        }
        try {
            return breaker.executeSupplier(() -> call(orderSn, Duration.ofMillis(2500)));
        } catch (CallNotPermittedException e) {
            return pending(orderSn, "결제 확인이 늦어지고 있습니다");
        } finally {
            slots.release();
        }
    }

    Map<String, Object> pending(String orderSn, String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("orderSn", orderSn);
        out.put("status", "PENDING_CHECK");
        out.put("message", message);
        return out;
    }

    private void noteRejected() {
        int n = rejected.incrementAndGet();
        long now = System.currentTimeMillis();
        long last = lastLog.get();
        if (now - last >= 10_000 && lastLog.compareAndSet(last, now)) {
            int count = rejected.getAndSet(0);
            SecurityEventLogger.suspicious("resilience.bulkhead.rejected", "14-3")
                    .actor("system:mall-portal")
                    .target("feature:payment")
                    .reason("결제 확인 동시 자리(" + SLOTS + ") 초과, 기다리지 않고 「확인 중」으로 답함")
                    .count(Math.max(count, n > 0 ? 1 : 0))
                    .log();
        }
    }

    Map<String, Object> call(String orderSn, Duration timeout) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("orderSn", orderSn);
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/payments/by-order/" + URLEncoder.encode(orderSn, StandardCharsets.UTF_8)))
                    .timeout(timeout)
                    .GET();
            String trace = TraceId.current();
            if (trace != null) {
                b.header("X-Trace-Id", trace);
            }
            HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode j = res.body() == null || res.body().isBlank() ? JSON.createObjectNode() : JSON.readTree(res.body());
            out.put("status", res.statusCode() == 200 ? j.path("status").asText("UNKNOWN") : (res.statusCode() == 404 ? "NONE" : "UNKNOWN"));
        } catch (java.io.IOException e) {
            out.put("status", "UNKNOWN");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            out.put("status", "UNKNOWN");
        }
        return out;
    }
}
