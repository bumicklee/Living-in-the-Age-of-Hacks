package io.github.securekit.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 개인정보를 읽는 관리자 기능에 감사 기록 한 줄(8-3).
 *
 * <pre>
 * &#64;GetMapping("/admin/members/{id}")
 * &#64;Audited(action = "member.read", resource = "member")
 * public MemberView read(&#64;PathVariable long id) { ... }
 * </pre>
 *
 * 기록(키 8개): ts trace_id actor action target count reason result.
 * - target: resource + ":" + 첫 &#64;PathVariable 값(member:3), 없으면 목록 표시(member:list?page=1&amp;size=20)
 * - count: 돌려준 건수(List 크기, {"list": […]}의 크기, 값 하나면 1)
 * - reason: 머리 X-Access-Reason(상담 번호 등)
 * - result: ok · denied(401·403) · error
 * 이름·전화·본문 같은 값은 넣지 않는다.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Audited {
    String action();

    /** target 앞부분(예: member). 비우면 action의 첫 마디 */
    String resource() default "";
}
