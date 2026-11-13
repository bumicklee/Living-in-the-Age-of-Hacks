package com.macro.mall.portal.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.security.trace.TraceId;
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

/**
 * [교육용 추가] 다온마켓 14장 시작 모습: 결제사에 「이 주문 결제됐나」를 그 자리에서 묻는다(고객 웹이 결제 뒤 몇 초마다 부른다).
 *
 * ✏️ 14-3: 결제사가 느리면 그만큼 기다린다(시간 제한 5초). 요청 처리 스레드는 그동안 이 호출에 묶인다.
 *   결제사가 오류 없이 느려지기만 해도 동시 확인 요청이 스레드를 다 붙잡아, 결제와 상관없는 상품 목록까지 멈춘다.
 *   9-5의 서킷 브레이커는 「실패 비율」만 봐서 이 경우(느림, 오류 아님)에는 열리지 않는다.
 * 정답(14-3): 동시에 결제사를 기다릴 수 있는 자리 수(벌크헤드) + 느린 호출 비율로 여는 서킷 브레이커 + 축소 운영 스위치.
 */
@Component
public class DaonPaymentGateway {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();

    @Value("${daon.payment.url:http://payment-stub:8000}")
    private String baseUrl;

    /** 결제사가 아는 이 주문의 결제 상태. 닿지 않으면 status = UNKNOWN */
    public Map<String, Object> status(String orderSn) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("orderSn", orderSn);
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/payments/by-order/" + URLEncoder.encode(orderSn, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(5))
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
