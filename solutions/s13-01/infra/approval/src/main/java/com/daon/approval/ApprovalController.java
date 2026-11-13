package com.daon.approval;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * [A10 · 13-3] 승인 서비스 입구.
 *
 * <pre>
 * POST /approvals/refund   {"returnId":31,"amount":10000}   Authorization: 관리자 토큰(관리자 앱이 받은 그대로 넘긴다), X-Trace-Id
 *      → {"id":"ap…","status":"APPROVED","token":"<서명된 승인 토큰>"}  또는  {"status":"DENIED","reason":"…"}
 * GET  /approvals/{id}     상태만(토큰·금액 없음)
 * GET  /health
 * </pre>
 *
 * - 요청자는 관리자 토큰의 주인이다(본문에 요청자 이름을 받지 않는다). 승인자 칸에는 정책이 승인했다는 뜻으로 "policy".
 * - 같은 주문에 동시에 들어온 요청이 남은 금액을 같이 통과하지 않게 판단과 기록을 한 줄로 세운다(인스턴스 하나 기준).
 * - 13-4에서 고액은 두 번째 사람·메일 확인 번호·지연 실행을 거친다(이 파일의 13-4 판).
 */
@RestController
public class ApprovalController {
    private final AdminAuth auth;
    private final ApprovalPolicy policy;
    private final ApprovalStore store;
    private final ApprovalTokens tokens;
    private final PolicyConfig config;

    public ApprovalController(AdminAuth auth, ApprovalPolicy policy, ApprovalStore store, ApprovalTokens tokens, PolicyConfig config) {
        this.auth = auth;
        this.policy = policy;
        this.store = store;
        this.tokens = tokens;
        this.config = config;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("ok", true);
    }

    @PostMapping("/approvals/refund")
    public ResponseEntity<Map<String, Object>> refund(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        AdminAuth.Actor actor = auth.actor(req);
        if (actor == null) {
            return ResponseEntity.status(401).body(Map.of("status", "UNAUTHORIZED"));
        }
        long returnId;
        BigDecimal amount;
        try {
            returnId = Long.parseLong(String.valueOf(body.get("returnId")));
            amount = new BigDecimal(String.valueOf(body.get("amount")));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "BAD_REQUEST"));
        }
        synchronized (this) {
            ApprovalPolicy.Decision d = policy.evaluate(returnId, amount, actor.tag());
            String id = ApprovalStore.newId();
            if (!d.ok()) {
                store.insertDenied(id, d.ret(), returnId, amount, actor.username(), d.reason());
                SecurityEvents.denied(d.event(), "13-3", actor.tag(), "return:" + returnId, d.reason());
                return ResponseEntity.ok(Map.of("id", id, "status", "DENIED", "reason", d.reason()));
            }
            store.insertRefund(id, d.ret(), amount, actor.username(), "policy", "APPROVED");
            return ResponseEntity.ok(issue(id));
        }
    }

    @GetMapping("/approvals/{id}")
    public ResponseEntity<Map<String, Object>> status(@PathVariable("id") String id) {
        Map<String, Object> row = store.find(id);
        if (row == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("kind", row.get("kind"));
        out.put("status", row.get("status"));
        out.put("returnId", row.get("return_id"));
        out.put("requestedBy", row.get("requested_by"));
        out.put("approvedBy", row.get("approved_by"));
        out.put("executeAfter", row.get("execute_after"));
        return ResponseEntity.ok(out);
    }

    private Map<String, Object> issue(String id) {
        Map<String, Object> row = store.find(id);
        String token = tokens.refund(row, config.num("refund.token_minutes"));
        store.tokenIssued(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("status", row.get("status"));
        out.put("token", token);
        return out;
    }
}
