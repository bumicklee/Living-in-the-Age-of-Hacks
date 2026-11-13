package io.github.securekit.web.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 로그인 속도 제한·크리덴셜 스터핑 막기(4-3).
 *
 * <pre>
 * &#64;PostMapping("/login")
 * &#64;RateLimited("login")      // 한도는 secure-kit.rate-limit.login.per-account · per-ip
 * public ResponseEntity&lt;?&gt; login(&#64;RequestBody LoginRequest body) { ... }
 * </pre>
 *
 * 실패(401)만 센다. 계정별 한도에 닿으면 비밀번호가 맞아도 429 + Retry-After + auth.login.rate_limited.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RateLimited {
    /** 규칙 이름(설정 secure-kit.rate-limit.&lt;이름&gt;) */
    String value();

    /** 요청 본문에서 계정을 꺼낼 칸 이름 */
    String account() default "username";

    /** 설정이 없을 때 쓰는 기본 한도 */
    String perAccount() default "5/10m";

    String perIp() default "50/10m";
}
