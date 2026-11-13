package io.github.securekit.web.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 고위험 작업 전에 비밀번호를 한 번 더 확인한다(4-7). 이메일·비밀번호·출금 계좌 바꾸기 등.
 *
 * 머리 X-Reauth-Password 없음 → 401 {"stepUp":"required"} · 틀림 → 401 {"stepUp":"failed"}.
 * 맞으면 진행하고 account.change.high_risk(INFO)를 남긴다. 확인은 앱이 준 StepUpVerifier 빈이 한다.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequireStepUp {
    /** 이벤트에 남길 작업 이름 */
    String value() default "account.change";
}
