package com.daon.approval;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * [13-4 정답] 지연 실행. 두 번째 승인이 끝난 고액 요청은 SCHEDULED로 execute_after(지금 + dual.delay_seconds)까지 기다린다.
 * 그 창 안에서는 요청자·승인자·취소 역할 누구나 취소할 수 있다(ApprovalController.cancel, approval.cancelled.in_window).
 * 창이 끝나면 서킷 브레이커를 거쳐 READY(토큰을 받을 수 있음) 또는 HELD(브레이커가 멈춤)가 된다.
 * 승인 토큰(13-3)은 READY가 된 뒤에만 나간다. 관리자 앱의 실행기가 READY 목록을 가져가 실행한다.
 * 랩은 지연이 10초다(회사 기본 30분, policy.yml). 시간 조작 훅(A13)은 관리자 앱 시계만 밀므로 여기서는 쓰지 않는다.
 */
@Component
public class DelayedExecutionScheduler {
    private final ApprovalStore store;
    private final PayoutCircuitBreaker breaker;

    public DelayedExecutionScheduler(ApprovalStore store, PayoutCircuitBreaker breaker) {
        this.store = store;
        this.breaker = breaker;
    }

    @Scheduled(fixedDelay = 2000L, initialDelay = 5000L)
    public void tick() {
        List<Map<String, Object>> due = store.jdbc().queryForList(
                "SELECT id, amount, approved_by FROM daon_approval_request WHERE kind = 'refund' AND status = 'SCHEDULED' AND execute_after <= NOW() ORDER BY execute_after LIMIT 20");
        for (Map<String, Object> r : due) {
            String id = String.valueOf(r.get("id"));
            if (breaker.allow(id, (BigDecimal) r.get("amount"), "admin:" + r.get("approved_by"))) {
                if (store.move(id, "SCHEDULED", "READY") > 0) {
                    store.tokenIssued(id);                 // 창(브레이커)이 이 시각부터 센다
                }
            } else {
                store.move(id, "SCHEDULED", "HELD");
            }
        }
    }
}
