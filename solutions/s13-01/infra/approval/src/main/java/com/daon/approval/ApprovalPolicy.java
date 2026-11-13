package com.daon.approval;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * [A10] 승인 정책. 요청서가 아니라 원장(DB)을 읽고 정책 파일(policy.yml)로 판단한다.
 *
 * ✏️ 13-3 손으로 고치기: evaluate()
 *   지금(시작 지점)은 받은 요청 금액을 그대로 믿고 승인한다. 다온마켓 관리자 앱이 하던 일을 자리만 옮긴 셈이다.
 *   고칠 것: ① 남은 결제 금액(주문 결제 금액 − 이미 잡아 둔 환불 합계)을 여기서 DB로 직접 계산해 넘으면 거부
 *           ② 1회 한도·같은 주문 하루 횟수(policy.yml) ③ 거부하면 approval.denied.* 한 줄
 */
@Component
public class ApprovalPolicy {
    private final ApprovalStore store;
    private final PolicyConfig policy;

    public ApprovalPolicy(ApprovalStore store, PolicyConfig policy) {
        this.store = store;
        this.policy = policy;
    }

    public record Decision(boolean ok, String event, String reason, Map<String, Object> ret, boolean highValue) {
        static Decision approve(Map<String, Object> ret, boolean high) {
            return new Decision(true, null, null, ret, high);
        }

        static Decision deny(String event, String reason, Map<String, Object> ret) {
            return new Decision(false, event, reason, ret, false);
        }
    }

    public Decision evaluate(long returnId, BigDecimal amount, String actor) {
        Map<String, Object> ret = store.returnWithOrder(returnId);
        if (ret == null) {
            return Decision.deny("approval.denied.policy_limit", "없는 반품 신청", null);
        }
        // ✏️ 13-3: 여기에 남은 결제 금액·1회 한도·하루 횟수 확인을 넣는다
        return Decision.approve(ret, false);
    }
}
