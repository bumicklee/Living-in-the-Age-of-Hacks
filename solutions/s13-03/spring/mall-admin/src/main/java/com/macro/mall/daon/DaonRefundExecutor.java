package com.macro.mall.daon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.lab.LabClock;
import com.macro.mall.security.trace.TraceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
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
 * [다온마켓 13-3 정답] 환불 실행기(시작 모습의 「확인하면 바로 환불」을 바꾼 것). 승인 서비스가 서명한 승인 토큰이 있어야만 움직인다.
 *
 * <pre>
 * execute(returnId, token)
 *   ⓐ 서명: 금고 transit/verify/daon-approval(관리자 앱은 확인만 할 수 있다)
 *   ⓑ 내용: kind=refund, 토큰의 반품 ID = 실행하려는 반품 ID, 토큰의 주문 = DB의 그 반품의 주문, 반품은 확인(1) 상태
 *   ⓒ 만료: exp 전(5분)
 *   ⓓ 1회용 번호(jti): 처음 쓰는지(Redis SET NX, 앞의 확인을 모두 통과한 뒤에만 표시한다)
 *   하나라도 틀리면 결제사를 부르지 않고 refund.denied.no_approval(WARN, 정상 흐름에서는 나오지 않는다)
 *   통과하면 토큰 속 금액(요청이 준 금액이 아니다)으로 가짜 결제사에 환불하고, 그 금액을 반품 신청에 적는다
 * </pre>
 *
 * 한계: 관리자 앱이 통째로 뚫리면 이 확인을 건너뛰고 결제사를 부를 수 있다. 그런 실행은 결제사 환불 목록과 승인 기록을 맞춰 보는
 * 대사(10-5 방식)로 찾는다. 승인 토큰은 「앱 혼자 승인을 만들 수 없게」 하는 장치다.
 */
@Component
public class DaonRefundExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(DaonRefundExecutor.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String USED = "mall:approval:used:";
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final String stub = env("DAON_PAYMENT_STUB_URL", "http://payment-stub:8000");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DaonApprovalToken tokens;
    @Autowired
    private StringRedisTemplate redis;

    /** 승인 토큰을 확인하고 실행. 실행했으면 true */
    public boolean execute(Long returnId, String token) {
        if (token == null || token.isBlank()) {
            return deny(returnId, "승인 토큰 없음");
        }
        Map<String, Object> p = tokens.verify(token, "refund");
        if (p == null) {
            return deny(returnId, "서명이 맞지 않거나 승인 토큰이 아님");
        }
        if (!String.valueOf(returnId).equals(String.valueOf(p.get("return_id")))) {
            return deny(returnId, "토큰의 반품 신청과 실행하려는 반품 신청이 다름");
        }
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT order_id, order_sn, status FROM oms_order_return_apply WHERE id = ?", returnId);
        if (rows.isEmpty() || !String.valueOf(rows.get(0).get("order_id")).equals(String.valueOf(p.get("order_id")))
                || !Integer.valueOf(1).equals(((Number) rows.get(0).get("status")).intValue())) {
            return deny(returnId, "토큰 내용(주문·상태)이 DB와 다름");
        }
        long exp = p.get("exp") instanceof Number n ? n.longValue() : 0L;
        if (exp <= LabClock.nowSeconds()) {
            return deny(returnId, "만료된 승인 토큰");
        }
        String jti = String.valueOf(p.get("jti"));
        Boolean first = redis.opsForValue().setIfAbsent(USED + jti, String.valueOf(returnId), Duration.ofDays(2));
        if (!Boolean.TRUE.equals(first)) {
            return deny(returnId, "이미 쓴 승인 토큰(1회용 번호 재사용)");
        }
        long amount;
        try {
            amount = new BigDecimal(String.valueOf(p.get("amount"))).longValueExact();
        } catch (RuntimeException e) {
            return deny(returnId, "토큰 금액 형식 오류");
        }
        String refundId = send(String.valueOf(rows.get(0).get("order_sn")), returnId, amount, jti);
        if (refundId == null) {
            redis.delete(USED + jti);                    // 결제사가 받지 않았으면 같은 토큰으로 다시 해 볼 수 있게
            return false;
        }
        jdbc.update("UPDATE oms_order_return_apply SET return_amount = ? WHERE id = ?", BigDecimal.valueOf(amount), returnId);
        LOG.info("[13-3] 승인 토큰을 확인하고 환불했습니다: return:{} approval:{}", returnId, p.get("approval"));
        return true;
    }

    private boolean deny(Long returnId, String reason) {
        SecurityEventLogger.denied("refund.denied.no_approval", "13-3")
                .target("return:" + returnId)
                .reason(reason)
                .log();
        return false;
    }

    /** [랩 스위치 approval을 껐을 때만] 시작 지점 모습: 요청 금액 그대로 바로 환불 */
    public void refundNow(Long returnId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        List<String> sn = jdbc.queryForList("SELECT order_sn FROM oms_order_return_apply WHERE id = ?", String.class, returnId);
        if (!sn.isEmpty() && sn.get(0) != null) {
            send(sn.get(0), returnId, amount.longValue(), "");
        }
    }

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
