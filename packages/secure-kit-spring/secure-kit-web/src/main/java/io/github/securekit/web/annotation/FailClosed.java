package io.github.securekit.web.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 실패하면 막는다(9-2). 본인 확인·권한 서버·사기 탐지처럼 「통과 여부」를 정하는 의존성을 부르는 곳.
 *
 * 메서드 안에서 예외가 나면(연결 실패·시간 초과 등) 통과시키지 않고 503 + 이벤트.
 * 흔한 사고: catch (Exception e) { return true; } — 의존성이 죽으면 모두 통과한다.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface FailClosed {
    /** 남길 이벤트 이름 */
    String event() default "auth.fail_closed.dependency_error";
}
