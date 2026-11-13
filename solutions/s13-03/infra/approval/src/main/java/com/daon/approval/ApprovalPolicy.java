package com.daon.approval;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * [A10 · 13-3 정답] 승인 정책. 요청서가 아니라 원장(DB)을 읽고 정책 파일(policy.yml)로 판단한다.
 *
 * ✏️ 13-3 evaluate()
 *   ① 남은 결제 금액 = 주문 결제 금액(oms_order.pay_amount) − 이 서비스가 이미 잡아 둔 환불 합계. 승인 서비스가 DB에서 직접 계산한다.
 *      관리자 앱이나 요청 본문이 준 금액·남은 금액은 믿지 않는다. 요청 금액이 넘으면 approval.denied.over_remaining
 *   ② 1회 한도·같은 주문 하루 횟수 → approval.denied.policy_limit
 *   ③ 고액 기준(refund.high_value) 이상이면 표시만 해 둔다. 두 사람 승인은 13-4(ApprovalController)가 한다
 * 거부 이벤트의 target은 반품 신청 ID, reason에는 규칙 이름만(금액은 넣지 않는다).
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
        if (!Switches.on("approval")) {
            return Decision.approve(ret, false);           // [랩 스위치] 끄면 시작 지점처럼 요청 금액을 그대로 믿는다(영상용)
        }
        if (amount == null || amount.signum() <= 0 || amount.scale() > 0 && amount.stripTrailingZeros().scale() > 0) {
            return Decision.deny("approval.denied.policy_limit", "금액은 원 단위 양수", ret);
        }
        Object orderStatus = ret.get("order_status");
        if (!(orderStatus instanceof Number n) || n.intValue() < 1 || n.intValue() > 3) {
            return Decision.deny("approval.denied.policy_limit", "결제가 끝난 주문이 아님", ret);
        }
        // ① 남은 결제 금액: 원장(주문 결제 금액)과 이 서비스의 승인 기록만으로 계산한다
        BigDecimal paid = (BigDecimal) ret.get("pay_amount");
        BigDecimal reserved = store.jdbc().queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM daon_approval_request WHERE kind = 'refund' AND order_id = ? AND status IN ("
                        + ApprovalStore.RESERVED + ")", BigDecimal.class, ret.get("order_id"));
        BigDecimal remaining = paid.subtract(reserved == null ? BigDecimal.ZERO : reserved);
        if (amount.compareTo(remaining) > 0) {
            return Decision.deny("approval.denied.over_remaining", "요청 금액이 남은 결제 금액을 넘음", ret);
        }
        // ② 정책 파일의 한도
        if (amount.compareTo(policy.won("refund.max_single")) > 0) {
            return Decision.deny("approval.denied.policy_limit", "1회 한도를 넘음", ret);
        }
        Integer today = store.jdbc().queryForObject(
                "SELECT COUNT(*) FROM daon_approval_request WHERE kind = 'refund' AND order_id = ? AND status IN ("
                        + ApprovalStore.RESERVED + ") AND created_at >= CURDATE()", Integer.class, ret.get("order_id"));
        if (today != null && today >= policy.num("refund.max_per_order_per_day")) {
            return Decision.deny("approval.denied.policy_limit", "같은 주문 하루 환불 횟수를 넘음", ret);
        }
        // ③ 고액 표시(13-4에서 두 사람 승인)
        return Decision.approve(ret, amount.compareTo(policy.won("refund.high_value")) >= 0);
    }
}
