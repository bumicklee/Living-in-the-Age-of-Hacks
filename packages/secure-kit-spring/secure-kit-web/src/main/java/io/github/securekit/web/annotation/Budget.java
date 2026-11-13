package io.github.securekit.web.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 비용이 드는 작업의 예산(10-7). 문자 발송·외부 유료 API·AI 호출 등.
 *
 * <pre>
 * &#64;Budget(value = "sms", key = "phone")   // 같은 번호에 60초 1통(설정 secure-kit.budget.sms.limit)
 * </pre>
 *
 * 넘으면 429 + budget.denied.&lt;이름&gt;. target은 키 값의 sha256 앞 8자(번호 원문은 남기지 않는다).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Budget {
    /** 예산 이름 */
    String value();

    /** 요청 본문에서 셀 대상을 꺼낼 칸 이름. 비우면 actor(사용자)별로 센다 */
    String key() default "";

    /** 설정이 없을 때 한도 */
    String limit() default "1/60s";
}
