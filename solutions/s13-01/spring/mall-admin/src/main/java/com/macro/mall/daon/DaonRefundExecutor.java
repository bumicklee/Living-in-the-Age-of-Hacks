package com.macro.mall.daon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.security.trace.TraceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

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
 * [교육용 추가 · 13장 시작 모습] 환불 실행기. 원본 mall은 반품을 확인한 뒤 실제로 돈을 돌려주는 코드가 없다.
 * 다온마켓은 「확인 버튼 = 그 자리에서 결제사 환불」로 붙였다(T34). 금액은 관리자 앱이 받은 요청의 returnAmount 그대로다.
 *
 * - 결제사는 랩의 가짜 결제사(payment-stub, POST /v1/refunds). 진짜 돈은 없다.
 * - 이 모습의 문제: 관리자 앱 혼자 금액을 정하고 혼자 실행한다. 남은 결제 금액·한도·두 번째 사람·시간이 없다.
 * - ✏️ 13-3에서 「승인 서비스가 서명한 승인 토큰을 확인해야만 실행하는」 실행기로 바꾼다.
 */
@Component
public class DaonRefundExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(DaonRefundExecutor.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final String stub = env("DAON_PAYMENT_STUB_URL", "http://payment-stub:8000");

    @Autowired
    private JdbcTemplate jdbc;

    /** 반품 확인 직후 바로 환불(금액은 요청이 준 값) */
    public void refundNow(Long returnId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        List<String> sn = jdbc.queryForList("SELECT order_sn FROM oms_order_return_apply WHERE id = ?", String.class, returnId);
        if (sn.isEmpty() || sn.get(0) == null) {
            return;
        }
        send(sn.get(0), returnId, amount.longValue(), "");
    }

    /** 가짜 결제사에 환불 한 건. 성공하면 결제사 환불 번호 */
    String send(String orderSn, Long returnId, long amount, String approval) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("order_sn", orderSn);
            body.put("amount", amount);
            body.put("return_id", returnId);
            body.put("approval", approval);
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(stub + "/v1/refunds")).timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
            String trace = TraceId.current();
            if (trace != null) {
                b.header(TraceId.HEADER, trace);
            }
            HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 201) {
                LOG.warn("[13장] 가짜 결제사 환불 실패: HTTP {}", r.statusCode());
                return null;
            }
            return JSON.readTree(r.body()).path("refund_id").asText(null);
        } catch (Exception e) {
            LOG.warn("[13장] 가짜 결제사에 닿지 않습니다: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    static String env(String name, String def) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? def : v.trim();
    }
}
