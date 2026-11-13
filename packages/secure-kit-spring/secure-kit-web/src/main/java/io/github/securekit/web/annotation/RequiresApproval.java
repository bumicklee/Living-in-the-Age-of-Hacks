package io.github.securekit.web.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 고액 작업은 다른 사람의 승인을 받는다(13-3·13-4).
 *
 * 기준 secure-kit.approval.policies.&lt;정책&gt;.threshold 미만이면 그대로 진행.
 * 이상이면 202 {"status":"PENDING_APPROVAL","approvalId"} → 다른 사람이 승인 → 같은 본문 + X-Approval-Id로 다시 → 진행.
 * 승인 번호는 한 번만(다시 쓰면 409). 금액·주문이 승인 때와 다르면 403. 요청자 본인 승인은 403.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresApproval {
    /** 정책 이름(설정 키) */
    String policy();

    /** 요청 본문에서 금액을 꺼낼 칸 이름 */
    String amount() default "amount";

    /** 설정이 없을 때 기준 금액 */
    long threshold() default 300_000;
}
