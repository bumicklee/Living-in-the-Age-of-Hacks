package io.github.securekit.web.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 같은 요청 두 번 막기(10-4). 환불·결제·주문처럼 두 번 되면 돈이 새는 곳.
 *
 * 키 없음 → 400. 같은 키 + 같은 본문 → 처음 응답을 그대로(처리는 한 번). 같은 키 + 다른 본문 → 422.
 * 처리 중에 같은 키가 또 오면 409. 처리가 예외로 끝나면 키를 지워 다시 시도할 수 있게 한다.
 * 동시성(같은 주문을 서로 다른 키로 두 번)은 막지 않는다. 그건 DB 조건부 UPDATE·유니크 제약이 막는다.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Idempotent {
    String header() default "Idempotency-Key";

    /** 키를 기억하는 기간(예: 24h) */
    String ttl() default "24h";
}
