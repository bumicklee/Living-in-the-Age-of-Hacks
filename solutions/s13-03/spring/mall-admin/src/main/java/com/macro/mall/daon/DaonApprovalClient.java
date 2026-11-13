package com.macro.mall.daon;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.security.trace.TraceId;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
 * [다온마켓 13-3 정답] 승인 서비스(A10)에 「요청」만 하는 클라이언트. 관리자 앱은 승인하지 않는다.
 *
 * - 누가 요청했는지는 관리자 앱이 정하지 않는다: 로그인한 관리자의 토큰(Authorization)을 그대로 넘기고, 승인 서비스가 직접 확인한다.
 * - X-Trace-Id를 넘긴다(랩-설계 11절). 같은 trace_id가 mall-admin 줄과 approval 줄에 함께 남는다(14-4 타임라인).
 * - 승인 서비스가 안 닿으면 거부로 본다(fail-closed, 9-2).
 */
@Component
public class DaonApprovalClient {
    private static final Logger LOG = LoggerFactory.getLogger(DaonApprovalClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final String base = DaonRefundExecutor.env("DAON_APPROVAL_URL", "");

    public record Result(String status, String id, String token, String reason) {
    }

    public boolean configured() {
        return !base.isEmpty();
    }

    /** 반품 확인 → 승인 요청. 금액은 「요청 금액」일 뿐이다. 판단은 승인 서비스가 DB를 읽고 한다 */
    public Result requestRefund(Long returnId, BigDecimal amount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("returnId", returnId);
        body.put("amount", amount == null ? null : amount.toPlainString());
        JsonNode r = call("POST", "/approvals/refund", body, callerToken());
        if (r == null) {
            return new Result("DENIED", null, null, "승인 서비스에 닿지 않음");
        }
        return new Result(r.path("status").asText("DENIED"), r.path("id").asText(null), r.path("token").asText(null), r.path("reason").asText(null));
    }

    /** 13-4: 지연 실행 창이 끝나 토큰을 받을 수 있는 환불 [{id, returnId}] */
    public List<Map<String, Object>> ready() {
        JsonNode r = call("GET", "/approvals/ready", null, null);
        if (r == null || !r.isArray()) {
            return List.of();
        }
        return JSON.convertValue(r, new TypeReference<>() { });
    }

    public String token(String approvalId) {
        JsonNode r = call("GET", "/approvals/" + approvalId + "/token", null, null);
        return r == null ? null : r.path("token").asText(null);
    }

    public void executed(String approvalId) {
        call("POST", "/approvals/" + approvalId + "/executed", Map.of(), null);
    }

    /** 13-6: 권한 상승 요청·토큰 받기(요청자 = 로그인한 관리자) */
    public JsonNode elevation(String method, String path, Map<String, Object> body) {
        return call(method, path, body, callerToken());
    }

    private JsonNode call(String method, String path, Map<String, Object> body, String authorization) {
        if (base.isEmpty()) {
            return null;
        }
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(5));
            String trace = TraceId.current();
            if (trace != null) {
                b.header(TraceId.HEADER, trace);
            }
            if (authorization != null) {
                b.header("Authorization", authorization);
            }
            if ("GET".equals(method)) {
                b.GET();
            } else {
                b.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
            }
            HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() >= 500 || r.body().isEmpty()) {
                return null;
            }
            return JSON.readTree(r.body());
        } catch (Exception e) {
            LOG.warn("[13-3] 승인 서비스 호출 실패({}): {}", path.replaceAll("/ap[0-9a-f]+", "/{id}"), e.getClass().getSimpleName());
            return null;
        }
    }

    private static String callerToken() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a) {
            HttpServletRequest req = a.getRequest();
            return req.getHeader("Authorization");
        }
        return null;
    }
}
