package com.daon.approval;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * [13-4 정답] 출금(환불) 서킷 브레이커. 승인 토큰을 내주기 직전에 창(1시간) 안에 풀어 준 건수·합계를 읽는다.
 * 기준(policy.yml breaker)을 넘으면 OPEN으로 바꾸고 자동 실행을 모두 멈춘다(payout.breaker.open). 9-5의 서킷 브레이커와 원리는 같고
 * 대상이 「돈이 나가는 양」이다. 자동으로 닫지 않는다: 정해진 역할의 서로 다른 두 사람이 닫는다(close()).
 */
@Component
public class PayoutCircuitBreaker {
    private final ApprovalStore store;
    private final PolicyConfig policy;

    public PayoutCircuitBreaker(ApprovalStore store, PolicyConfig policy) {
        this.store = store;
        this.policy = policy;
    }

    public boolean open() {
        return "OPEN".equals(store.jdbc().queryForObject("SELECT state FROM daon_approval_breaker WHERE id = 1", String.class));
    }

    /** 이 금액을 풀어 줘도 되나. 안 되면 브레이커를 열고 false */
    public synchronized boolean allow(String approvalId, BigDecimal amount, String actor) {
        if (!Switches.on("dual-approval")) {
            return true;                                   // [랩 스위치] 끄면 13-3처럼 양을 보지 않는다
        }
        if (open()) {
            return false;
        }
        Map<String, Object> w = store.jdbc().queryForMap(
                "SELECT COUNT(*) AS n, COALESCE(SUM(amount), 0) AS total FROM daon_approval_request"
                        + " WHERE kind = 'refund' AND status IN ('APPROVED','READY','EXECUTED') AND token_at >= NOW() - INTERVAL ? MINUTE",
                policy.num("breaker.window_minutes"));
        long n = ((Number) w.get("n")).longValue();
        BigDecimal total = new BigDecimal(String.valueOf(w.get("total")));
        boolean over = n + 1 > policy.num("breaker.max_count") || total.add(amount).compareTo(policy.won("breaker.max_amount")) > 0;
        if (!over) {
            return true;
        }
        String why = n + 1 > policy.num("breaker.max_count") ? "시간당 환불 건수 기준을 넘음" : "시간당 환불 합계 기준을 넘음";
        int changed = store.jdbc().update("UPDATE daon_approval_breaker SET state = 'OPEN', opened_at = NOW(), reason = ?, close_vote = NULL WHERE id = 1 AND state = 'CLOSED'", why);
        if (changed > 0) {
            SecurityEvents.denied("payout.breaker.open", "13-4", actor, "approval:" + approvalId, why + "(자동 실행을 멈춤)");
        }
        return false;
    }

    /** 닫기: 정해진 역할의 서로 다른 두 사람. 첫 사람은 표만 남긴다(10분 안에 두 번째 사람). 결과 상태를 돌려준다 */
    public synchronized String close(AdminAuth.Actor actor) {
        if (!open()) {
            return "CLOSED";
        }
        Map<String, Object> b = store.jdbc().queryForMap(
                "SELECT close_vote, (close_vote_at IS NOT NULL AND close_vote_at > NOW() - INTERVAL 10 MINUTE) AS fresh FROM daon_approval_breaker WHERE id = 1");
        Object vote = b.get("close_vote");
        boolean fresh = b.get("fresh") instanceof Number f && f.intValue() == 1;
        if (vote == null || !fresh || actor.username().equals(vote)) {
            store.jdbc().update("UPDATE daon_approval_breaker SET close_vote = ?, close_vote_at = NOW() WHERE id = 1", actor.username());
            return "OPEN(1/2)";
        }
        store.jdbc().update("UPDATE daon_approval_breaker SET state = 'CLOSED', opened_at = NULL, reason = NULL, close_vote = NULL, close_vote_at = NULL WHERE id = 1");
        return "CLOSED";
    }
}
