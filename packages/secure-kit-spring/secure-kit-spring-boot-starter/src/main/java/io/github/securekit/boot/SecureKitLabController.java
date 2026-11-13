package io.github.securekit.boot;

import io.github.securekit.audit.AuditTrail;
import io.github.securekit.audit.KitLogBuffer;
import io.github.securekit.core.KitResettable;
import io.github.securekit.core.SecureKitException;
import io.github.securekit.core.event.SecurityEvents;
import io.github.securekit.core.secret.SecretRules;
import io.github.securekit.core.store.CounterStore;
import io.github.securekit.web.aspect.ApprovalService;
import io.github.securekit.web.token.KitTokenService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 랩 전용 주소 /_kit/* (secure-kit.lab.expose: true 일 때만). 회사 서비스에서는 끈다.
 * 계약 시험이 부품의 속(이벤트·감사 기록·로그)을 들여다보는 창이다. 상태를 바꾸는 것은 reset·approve뿐이다.
 */
@RestController
@RequestMapping("/_kit")
public class SecureKitLabController {
    private final CounterStore store;
    private final ObjectProvider<KitResettable> resettables;
    private final ObjectProvider<KitTokenService> tokens;
    private final ApprovalService approvals;

    public SecureKitLabController(CounterStore store, ObjectProvider<KitResettable> resettables,
                                  ObjectProvider<KitTokenService> tokens, ApprovalService approvals) {
        this.store = store;
        this.resettables = resettables;
        this.tokens = tokens;
        this.approvals = approvals;
    }

    /** 메모리 상태를 처음으로. 이벤트·감사 기록은 지우지 않는다 */
    @PostMapping("/reset")
    public ResponseEntity<Void> reset() {
        store.clear();
        resettables.orderedStream().forEach(KitResettable::reset);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/events")
    public Map<String, Object> events(@RequestParam(name = "after", defaultValue = "0") long after) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("seq", SecurityEvents.lastSeq());
        out.put("events", SecurityEvents.after(after));
        return out;
    }

    @GetMapping("/audit")
    public Map<String, Object> audit() {
        return Map.of("records", AuditTrail.recent());
    }

    @GetMapping("/logs")
    public Map<String, Object> logs() {
        return Map.of("lines", KitLogBuffer.lines());
    }

    /** 기동 검사기와 같은 판단. 값은 돌려주지 않는다 */
    @PostMapping("/startup/check")
    public Map<String, Object> startupCheck(@RequestBody Map<String, Object> props) {
        List<String> denied = SecretRules.denied(props);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", denied.isEmpty());
        out.put("denied", denied);
        return out;
    }

    @PostMapping("/partner/token")
    public Map<String, Object> partnerToken(@RequestBody Map<String, Object> body) {
        KitTokenService t = tokens.getIfAvailable();
        if (t == null) throw new SecureKitException(404, "토큰 부품이 꺼져 있습니다");
        List<String> scopes = new ArrayList<>();
        if (body.get("scopes") instanceof List<?> l) for (Object o : l) scopes.add(String.valueOf(o));
        return Map.of("token", t.issuePartner(String.valueOf(body.get("app")), scopes));
    }

    @PostMapping("/approvals/{id}/approve")
    public Map<String, Object> approve(@PathVariable("id") String id, @RequestBody Map<String, Object> body) {
        Object approver = body.get("approver");
        approvals.approve(id, approver == null ? null : approver.toString());
        return Map.of("status", "APPROVED");
    }
}
