package io.github.securekit.web.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 파트너 토큰 범위(12-2·12-3). 파트너(aud=partner) 토큰만 받고, 범위(scope)에 이 값이 있어야 한다.
 * 사람 토큰 → 401. 범위 밖 → 403 + partner.denied.scope(actor partner:앱).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PartnerScope {
    /** 필요한 범위(예: orders:read) */
    String value();
}
