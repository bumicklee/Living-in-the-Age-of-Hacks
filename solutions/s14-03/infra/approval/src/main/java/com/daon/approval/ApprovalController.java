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
import java.util.List;
import java.util.Map;

/**
 * [A10 · 13-3 + 13-4 정답] 승인 서비스 입구.
 *
 * <pre>
 * POST /approvals/refund          {"returnId":31,"amount":10000}  관리자 토큰 → APPROVED+토큰 · PENDING_SECOND · HELD_BY_BREAKER · DENIED
 * POST /approvals/{id}/approve    {"code":"123456"}  두 번째 승인(재무 담당, 요청자와 다른 사람, 메일 확인 번호) → SCHEDULED
 * POST /approvals/{id}/cancel     지연 실행 창 안에서 취소(요청자·승인자·취소 역할) → CANCELLED
 * GET  /approvals/ready           창이 끝나 토큰을 받을 수 있는 환불 목록(관리자 앱 실행기가 가져간다)
 * GET  /approvals/{id}/token      APPROVED·READY인 환불의 승인 토큰
 * POST /approvals/{id}/executed   실행기가 끝냈다고 알림(기록용)
 * GET  /approvals/{id}            상태만
 * GET  /breaker · POST /breaker/close   출금 서킷 브레이커 상태·닫기(서로 다른 두 사람)
 * POST /breaker/hold                    14-3 축소 운영: 사람이 연다(로그인한 관리자 누구나)
 * </pre>
 *
 * ✏️ 13-4: approve()의 「요청자 ≠ 승인자 + 재무 담당」, 메일 확인 번호(OutOfBandConfirm), 지연 실행(DelayedExecutionScheduler),
 *          서킷 브레이커(PayoutCircuitBreaker). 고액 기준 미만은 13-3처럼 바로 승인한다(승인 피로를 만들지 않게).
 * 서비스 사이 인증(관리자 앱 ↔ 승인 서비스)은 랩에서 망 분리로만 둔다. ready·token·executed는 토큰을 받아도 실행은 관리자 앱
 * 실행기가 서명·내용·1회용 번호를 확인한 뒤에만 한다(서비스 사이 mTLS는 12-3 방식으로 붙일 수 있다).
 */
@RestController
public class ApprovalController {
    private final AdminAuth auth;
    private final ApprovalPolicy policy;
    private final ApprovalStore store;
    private final ApprovalTokens tokens;
    private final PolicyConfig config;
    private final OutOfBandConfirm confirm;
    private final PayoutCircuitBreaker breaker;

    public ApprovalController(AdminAuth auth, ApprovalPolicy policy, ApprovalStore store, ApprovalTokens tokens, PolicyConfig config,
                              OutOfBandConfirm confirm, PayoutCircuitBreaker breaker) {
        this.auth = auth;
        this.policy = policy;
        this.store = store;
        this.tokens = tokens;
        this.config = config;
        this.confirm = confirm;
        this.breaker = breaker;
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
            if (d.highValue() && Switches.on("dual-approval")) {
                // 13-4: 고액은 두 번째 사람. 번호는 메일로만, 응답에는 넣지 않는다
                store.insertRefund(id, d.ret(), amount, actor.username(), null, "PENDING_SECOND");
                boolean mailed = confirm.send(id);
                SecurityEvents.info("approval.pending.second_approver", "13-4", actor.tag(), "approval:" + id, "고액이라 두 번째 승인자를 기다림");
                return ResponseEntity.ok(Map.of("id", id, "status", "PENDING_SECOND",
                        "message", mailed ? "재무 담당에게 확인 번호를 메일로 보냈습니다" : "확인 번호 메일을 보내지 못했습니다(다시 요청하세요)"));
            }
            if (!breaker.allow(id, amount, actor.tag())) {
                store.insertRefund(id, d.ret(), amount, actor.username(), "policy", "HELD");
                return ResponseEntity.ok(Map.of("id", id, "status", "HELD_BY_BREAKER", "message", "출금 서킷 브레이커가 열려 자동 실행을 멈췄습니다"));
            }
            store.insertRefund(id, d.ret(), amount, actor.username(), "policy", "APPROVED");
            return ResponseEntity.ok(issue(id));
        }
    }

    @PostMapping("/approvals/{id}/approve")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable("id") String id, @RequestBody(required = false) Map<String, Object> body,
                                                       HttpServletRequest req) {
        AdminAuth.Actor actor = auth.actor(req);
        if (actor == null) {
            return ResponseEntity.status(401).body(Map.of("status", "UNAUTHORIZED"));
        }
        Map<String, Object> row = store.find(id);
        if (row == null || !"refund".equals(row.get("kind"))) {
            return ResponseEntity.notFound().build();
        }
        if (!"PENDING_SECOND".equals(row.get("status"))) {
            return ResponseEntity.status(409).body(Map.of("status", String.valueOf(row.get("status"))));
        }
        // ✏️ 13-4 ① 요청한 사람이 스스로 두 번째 승인을 할 수 없다
        if (actor.username().equals(row.get("requested_by"))) {
            SecurityEvents.denied("approval.denied.self_approval", "13-4", actor.tag(), "approval:" + id, "요청한 사람이 스스로 두 번째 승인을 시도");
            return ResponseEntity.status(403).body(Map.of("status", "DENIED", "reason", "요청자와 다른 사람이 승인해야 합니다"));
        }
        // ② 정해진 역할(재무 담당)만
        if (!actor.hasAny(config.list("dual.second_approver_roles"))) {
            SecurityEvents.denied("approval.denied.policy_limit", "13-4", actor.tag(), "approval:" + id, "두 번째 승인 역할이 아님");
            return ResponseEntity.status(403).body(Map.of("status", "DENIED", "reason", "두 번째 승인 역할이 아닙니다"));
        }
        // ③ 다른 경로(메일)로 받은 확인 번호
        String code = body == null ? null : String.valueOf(body.get("code"));
        if (!confirm.check(id, code)) {
            SecurityEvents.denied("approval.denied.bad_confirm", "13-4", actor.tag(), "approval:" + id, "메일 확인 번호가 없거나 틀림·만료");
            return ResponseEntity.status(403).body(Map.of("status", "DENIED", "reason", "확인 번호가 맞지 않습니다"));
        }
        // ④ 지연 실행 창: 바로 실행하지 않고 SCHEDULED
        int moved = store.jdbc().update("UPDATE daon_approval_request SET status = 'SCHEDULED', approved_by = ?, decided_at = NOW(),"
                + " execute_after = NOW() + INTERVAL ? SECOND WHERE id = ? AND status = 'PENDING_SECOND'", actor.username(), config.num("dual.delay_seconds"), id);
        if (moved == 0) {
            return ResponseEntity.status(409).body(Map.of("status", "CHANGED"));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("status", "SCHEDULED");
        out.put("executeAfter", store.find(id).get("execute_after"));
        return ResponseEntity.ok(out);
    }

    @PostMapping("/approvals/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable("id") String id, HttpServletRequest req) {
        AdminAuth.Actor actor = auth.actor(req);
        if (actor == null) {
            return ResponseEntity.status(401).body(Map.of("status", "UNAUTHORIZED"));
        }
        Map<String, Object> row = store.find(id);
        if (row == null) {
            return ResponseEntity.notFound().build();
        }
        boolean mine = actor.username().equals(row.get("requested_by")) || actor.username().equals(row.get("approved_by"));
        if (!mine && !actor.hasAny(config.list("dual.cancel_roles"))) {
            return ResponseEntity.status(403).body(Map.of("status", "DENIED", "reason", "취소할 수 있는 사람이 아닙니다"));
        }
        String from = String.valueOf(row.get("status"));
        if (!("PENDING_SECOND".equals(from) || "SCHEDULED".equals(from)) || store.move(id, from, "CANCELLED") == 0) {
            return ResponseEntity.status(409).body(Map.of("status", from));
        }
        SecurityEvents.info("approval.cancelled.in_window", "13-4", actor.tag(), "approval:" + id, "지연 실행 창 안에서 취소");
        return ResponseEntity.ok(Map.of("id", id, "status", "CANCELLED"));
    }

    @GetMapping("/approvals/ready")
    public List<Map<String, Object>> ready() {
        return store.jdbc().queryForList(
                "SELECT id, return_id AS returnId FROM daon_approval_request WHERE kind = 'refund' AND status = 'READY' ORDER BY execute_after LIMIT 50");
    }

    @GetMapping("/approvals/{id}/token")
    public ResponseEntity<Map<String, Object>> token(@PathVariable("id") String id) {
        Map<String, Object> row = store.find(id);
        if (row == null || !"refund".equals(row.get("kind"))) {
            return ResponseEntity.notFound().build();
        }
        String status = String.valueOf(row.get("status"));
        if (!("APPROVED".equals(status) || "READY".equals(status))) {
            return ResponseEntity.status(409).body(Map.of("status", status));
        }
        return ResponseEntity.ok(issue(id));
    }

    @PostMapping("/approvals/{id}/executed")
    public Map<String, Object> executed(@PathVariable("id") String id) {
        int n = store.jdbc().update("UPDATE daon_approval_request SET status = 'EXECUTED', executed_at = NOW() WHERE id = ? AND status IN ('APPROVED','READY')", id);
        return Map.of("id", id, "changed", n);
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

    @GetMapping("/breaker")
    public Map<String, Object> breakerState() {
        return store.jdbc().queryForMap("SELECT state, opened_at AS openedAt, reason FROM daon_approval_breaker WHERE id = 1");
    }

    @PostMapping("/breaker/close")
    public ResponseEntity<Map<String, Object>> breakerClose(HttpServletRequest req) {
        AdminAuth.Actor actor = auth.actor(req);
        if (actor == null) {
            return ResponseEntity.status(401).body(Map.of("status", "UNAUTHORIZED"));
        }
        if (!actor.hasAny(config.list("breaker.close_roles"))) {
            return ResponseEntity.status(403).body(Map.of("status", "DENIED"));
        }
        return ResponseEntity.ok(Map.of("state", breaker.close(actor)));
    }

    /** 14-3 축소 운영: 출금 서킷 브레이커를 사람이 연다(로그인한 관리자 누구나. 닫기는 두 사람) */
    @PostMapping("/breaker/hold")
    public ResponseEntity<Map<String, Object>> breakerHold(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest req) {
        AdminAuth.Actor actor = auth.actor(req);
        if (actor == null) {
            return ResponseEntity.status(401).body(Map.of("status", "UNAUTHORIZED"));
        }
        String reason = body == null || body.get("reason") == null ? "" : String.valueOf(body.get("reason"));
        return ResponseEntity.ok(Map.of("state", breaker.hold(actor, reason)));
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
