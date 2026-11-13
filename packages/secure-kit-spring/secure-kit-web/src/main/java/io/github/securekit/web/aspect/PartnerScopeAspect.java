package io.github.securekit.web.aspect;

import io.github.securekit.core.SecureKitException;
import io.github.securekit.core.event.SecurityEvents;
import io.github.securekit.web.KitContext;
import io.github.securekit.web.KitPrincipal;
import io.github.securekit.web.annotation.PartnerScope;
import io.github.securekit.web.support.ArgFields;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

/** &#64;PartnerScope 처리. 파트너 토큰인지(aud) 먼저, 그다음 범위(scope) */
@Aspect
@Order(KitOrder.PARTNER)
public class PartnerScopeAspect {
    @Around("@annotation(io.github.securekit.web.annotation.PartnerScope)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        String need = ArgFields.method(pjp).getAnnotation(PartnerScope.class).value();
        KitPrincipal p = KitContext.principal(ArgFields.request());
        if (p == null || !p.isPartner()) {
            if (p != null) {
                SecurityEvents.warn("partner.denied.audience", "partner-scope", "aud:" + p.audience(), "파트너 토큰이 아니다");
            }
            throw new SecureKitException(401, "파트너 인증이 필요합니다");
        }
        if (!p.scopes().contains(need)) {
            SecurityEvents.warn("partner.denied.scope", "partner-scope", "scope:" + need, "토큰 범위 밖 요청");
            throw new SecureKitException(403, "허용되지 않은 범위입니다");
        }
        return pjp.proceed();
    }
}
