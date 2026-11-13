package com.macro.mall.security.securekit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * [다온마켓 12-2 정답 · secure-kit 부품] 파트너 API 하나가 요구하는 범위(scope) 하나.
 *
 * <pre>
 * &#64;PartnerScope("orders:read:assigned")
 * &#64;GetMapping("/partner/orders")
 * public ... orders(HttpServletRequest req) { PartnerPrincipal p = PartnerPrincipal.of(req); ... }
 * </pre>
 *
 * - 처리는 PartnerScopeInterceptor가 한다: 파트너 입구에서 왔나 → 파트너 토큰(aud daon-partner)이 맞나 → 만료 → 범위.
 * - 범위는 「무슨 기능」만 답한다. 「누구의 데이터」는 조회 조건(partner_id)이 답한다. 둘 다 필요하다(2-2와 같은 이야기).
 * - 범위 이름은 기능 단위로 짓는다: orders:read:assigned(배정 주문 읽기), orders:export(한 번에 내보내기). 「*」는 두지 않는다.
 * - Express는 같은 일을 kit.partnerScope('orders:read:assigned') 미들웨어로 한다(diffs/s12-02).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PartnerScope {
    String value();
}
