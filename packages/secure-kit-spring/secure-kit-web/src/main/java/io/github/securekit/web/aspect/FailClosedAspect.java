package io.github.securekit.web.aspect;

import io.github.securekit.core.SecureKitException;
import io.github.securekit.core.event.SecurityEvents;
import io.github.securekit.web.annotation.FailClosed;
import io.github.securekit.web.support.ArgFields;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

/**
 * &#64;FailClosed 처리. 메서드 안 예외 = 통과 여부를 모른다 = 막는다(503).
 * 부품이 이미 정한 응답(SecureKitException)은 그대로 둔다.
 */
@Aspect
@Order(KitOrder.FAIL_CLOSED)
public class FailClosedAspect {
    @Around("@annotation(io.github.securekit.web.annotation.FailClosed)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        try {
            return pjp.proceed();
        } catch (SecureKitException e) {
            throw e;
        } catch (Exception e) {
            String event = ArgFields.method(pjp).getAnnotation(FailClosed.class).event();
            SecurityEvents.warn(event, "fail-closed", "method:" + pjp.getSignature().getName(),
                    "의존성 오류(" + e.getClass().getSimpleName() + ") → 통과시키지 않음");
            throw new SecureKitException(503, "잠시 후 다시 시도하세요");
        }
    }
}
