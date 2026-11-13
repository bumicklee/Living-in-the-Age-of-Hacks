package io.github.securekit.web.aspect;

import io.github.securekit.core.SecureKitException;
import io.github.securekit.core.event.SecurityEvents;
import io.github.securekit.web.KitPrincipal;
import io.github.securekit.web.annotation.RequireStepUp;
import io.github.securekit.web.support.ArgFields;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.Order;

/**
 * &#64;RequireStepUp 처리. 로그인만으로는 부족한 작업에서 비밀번호를 한 번 더 받는다.
 * 토큰을 훔친 사람은 비밀번호를 모른다. StepUpVerifier 빈이 없으면 막는다(503, 실패하면 막기).
 */
@Aspect
@Order(KitOrder.STEP_UP)
public class StepUpAspect {
    public static final String HEADER = "X-Reauth-Password";
    private final ObjectProvider<StepUpVerifier> verifier;

    public StepUpAspect(ObjectProvider<StepUpVerifier> verifier) {
        this.verifier = verifier;
    }

    @Around("@annotation(io.github.securekit.web.annotation.RequireStepUp)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        RequireStepUp ann = ArgFields.method(pjp).getAnnotation(RequireStepUp.class);
        HttpServletRequest req = ArgFields.request();
        KitPrincipal p = KitPrincipal.requireUser(req);
        String password = req.getHeader(HEADER);
        if (password == null || password.isEmpty()) {
            SecurityEvents.info("auth.stepup.required", "step-up", "action:" + ann.value(), "재인증 없이 고위험 작업을 시도");
            throw new SecureKitException(401, "비밀번호를 한 번 더 확인해 주세요").extra("stepUp", "required");
        }
        StepUpVerifier v = verifier.getIfAvailable();
        if (v == null) throw new SecureKitException(503, "잠시 후 다시 시도하세요");
        if (!v.verify(p.sub(), password)) {
            SecurityEvents.warn("auth.stepup.failed", "step-up", "action:" + ann.value(), "재인증 비밀번호가 틀렸다");
            throw new SecureKitException(401, "비밀번호가 올바르지 않습니다").extra("stepUp", "failed");
        }
        Object result = pjp.proceed();
        SecurityEvents.info("account.change.high_risk", "step-up", "action:" + ann.value(), "재인증 뒤 고위험 작업 완료");
        return result;
    }

}
