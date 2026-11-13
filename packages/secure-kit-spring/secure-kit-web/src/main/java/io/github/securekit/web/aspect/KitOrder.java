package io.github.securekit.web.aspect;

/**
 * 애스펙트가 겹칠 때 바깥 → 안 순서. 감사(@Audited)가 가장 바깥이라 막힌 요청도 기록된다.
 * 감사 10 → 속도 제한 20 → 재인증·파트너 30 → 승인 40 → 멱등·예산 50 → 실패하면 막기 60
 */
public final class KitOrder {
    public static final int AUDIT = 10;
    public static final int RATE_LIMIT = 20;
    public static final int STEP_UP = 30;
    public static final int PARTNER = 30;
    public static final int APPROVAL = 40;
    public static final int IDEMPOTENT = 50;
    public static final int BUDGET = 50;
    public static final int FAIL_CLOSED = 60;

    private KitOrder() {
    }
}
