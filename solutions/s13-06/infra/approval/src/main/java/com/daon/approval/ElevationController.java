package com.daon.approval;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * [13-6 정답] 운영 권한 상승(JIT)도 승인 서비스가 맡는다(13-3의 승인 서비스를 재사용, kind=ops-elevation).
 *
 * <pre>
 * POST /elevations                {"reason":"…","group":"admin-accounts","minutes":30}  요청자 = 관리자 토큰 주인
 * POST /elevations/{id}/approve   다른 사람(운영 책임)이 승인. 요청자가 스스로 승인하면 approval.denied.self_approval
 * GET  /elevations/{id}/token     요청자 본인만, 승인된 뒤에만: 서명된 권한 상승 토큰(누가·어떤 묶음·만료)
 * </pre>
 *
 * 권한 상승의 네 요소: 사유(필수, 200자), 다른 사람의 승인, 시간 제한(최대 60분), 기록(이 표 + 관리자 앱의 ops.elevation.granted).
 * 사유는 이 표(감사용)에만 남기고 보안 이벤트에는 넣지 않는다.
 */
@RestController
public class ElevationController {
    private final AdminAuth auth;
    private final ApprovalStore store;
    private final ApprovalTokens tokens;
    private final PolicyConfig config;

    public ElevationController(AdminAuth auth, ApprovalStore store, ApprovalTokens tokens, PolicyConfig config) {
        this.auth = auth;
        this.store = store;
        this.tokens = tokens;
        this.config = config;
    }

    @PostMapping("/elevations")
    public ResponseEntity<Map<String, Object>> request(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        AdminAuth.Actor actor = auth.actor(req);
        if (actor == null) {
            return ResponseEntity.status(401).body(Map.of("status", "UNAUTHORIZED"));
        }
        String reason = body.get("reason") == null ? "" : String.valueOf(body.get("reason")).strip();
        String group = String.valueOf(body.get("group"));
        int minutes;
        try {
            minutes = Integer.parseInt(String.valueOf(body.get("minutes")));
        } catch (RuntimeException e) {
            minutes = -1;
        }
        if (reason.isEmpty() || reason.length() > 200 || !config.list("elevation.groups").contains(group)
                || minutes < 1 || minutes > config.num("elevation.max_minutes")) {
            return ResponseEntity.badRequest().body(Map.of("status", "BAD_REQUEST",
                    "reason", "사유(200자까지)·묶음(" + String.join(", ", config.list("elevation.groups")) + ")·시간(1~" + config.num("elevation.max_minutes") + "분)이 필요합니다"));
        }
        String id = ApprovalStore.newId();
        store.jdbc().update("INSERT INTO daon_approval_request (id, kind, target, minutes, reason, requested_by, status, nonce, created_at)"
                + " VALUES (?, 'ops-elevation', ?, ?, ?, ?, 'PENDING_SECOND', ?, NOW())", id, group, minutes, reason, actor.username(), ApprovalStore.nonce());
        return ResponseEntity.ok(Map.of("id", id, "status", "PENDING_SECOND"));
    }

    @PostMapping("/elevations/{id}/approve")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable("id") String id, HttpServletRequest req) {
        AdminAuth.Actor actor = auth.actor(req);
        if (actor == null) {
            return ResponseEntity.status(401).body(Map.of("status", "UNAUTHORIZED"));
        }
        Map<String, Object> row = store.find(id);
        if (row == null || !"ops-elevation".equals(row.get("kind"))) {
            return ResponseEntity.notFound().build();
        }
        if (actor.username().equals(row.get("requested_by"))) {
            SecurityEvents.denied("approval.denied.self_approval", "13-6", actor.tag(), "approval:" + id, "권한 상승을 요청한 사람이 스스로 승인을 시도");
            return ResponseEntity.status(403).body(Map.of("status", "DENIED", "reason", "요청자와 다른 사람이 승인해야 합니다"));
        }
        if (!actor.hasAny(config.list("elevation.approver_roles"))) {
            SecurityEvents.denied("approval.denied.policy_limit", "13-6", actor.tag(), "approval:" + id, "권한 상승 승인 역할이 아님");
            return ResponseEntity.status(403).body(Map.of("status", "DENIED", "reason", "권한 상승 승인 역할이 아닙니다"));
        }
        int n = store.jdbc().update("UPDATE daon_approval_request SET status = 'APPROVED', approved_by = ?, decided_at = NOW() WHERE id = ? AND status = 'PENDING_SECOND'",
                actor.username(), id);
        return n == 0 ? ResponseEntity.status(409).body(Map.of("status", String.valueOf(row.get("status"))))
                : ResponseEntity.ok(Map.of("id", id, "status", "APPROVED"));
    }

    @GetMapping("/elevations/{id}/token")
    public ResponseEntity<Map<String, Object>> token(@PathVariable("id") String id, HttpServletRequest req) {
        AdminAuth.Actor actor = auth.actor(req);
        if (actor == null) {
            return ResponseEntity.status(401).body(Map.of("status", "UNAUTHORIZED"));
        }
        Map<String, Object> row = store.find(id);
        if (row == null || !"ops-elevation".equals(row.get("kind")) || !actor.username().equals(row.get("requested_by"))) {
            return ResponseEntity.notFound().build();
        }
        if (!"APPROVED".equals(row.get("status")) || row.get("token_at") != null) {
            return ResponseEntity.status(409).body(Map.of("status", String.valueOf(row.get("status")), "reason", "승인 전이거나 이미 받은 권한 상승"));
        }
        String token = tokens.elevation(row);
        store.tokenIssued(id);
        return ResponseEntity.ok(Map.of("id", id, "token", token));
    }
}
