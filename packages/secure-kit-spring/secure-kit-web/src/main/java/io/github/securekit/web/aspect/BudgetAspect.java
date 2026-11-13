package io.github.securekit.web.aspect;

import io.github.securekit.core.Hashes;
import io.github.securekit.core.KitMessages;
import io.github.securekit.core.SecureKitException;
import io.github.securekit.core.event.SecurityEvents;
import io.github.securekit.core.store.CounterStore;
import io.github.securekit.core.store.Rate;
import io.github.securekit.web.KitContext;
import io.github.securekit.web.annotation.Budget;
import io.github.securekit.web.support.ArgFields;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

/**
 * &#64;Budget 처리. 셀 대상(전화번호 등)은 해시로만 다룬다. 저장소 키에도 원문을 넣지 않는다.
 * 한도: secure-kit.budget.&lt;이름&gt;.limit (예: 1/60s).
 */
@Aspect
@Order(KitOrder.BUDGET)
public class BudgetAspect {
    private final CounterStore store;
    private final Environment env;

    public BudgetAspect(CounterStore store, Environment env) {
        this.store = store;
        this.env = env;
    }

    @Around("@annotation(io.github.securekit.web.annotation.Budget)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        Budget ann = ArgFields.method(pjp).getAnnotation(Budget.class);
        Rate rate = Rate.parse(env.getProperty("secure-kit.budget." + ann.value() + ".limit", ann.limit()));
        Object value = ann.key().isEmpty() ? KitContext.actor(ArgFields.request()) : ArgFields.field(pjp.getArgs(), ann.key());
        if (value == null) throw new SecureKitException(400, "요청 형식이 올바르지 않습니다");
        String hash = Hashes.sha256Hex(String.valueOf(value));
        String key = "budget:" + ann.value() + ":" + hash;
        long n = store.increment(key, rate.window());
        if (n > rate.max()) {
            String label = ann.key().isEmpty() ? "actor" : ann.key();
            SecurityEvents.warn("budget.denied." + ann.value(), "budget", label + ":" + hash.substring(0, 8), "예산 " + rate + " 초과");
            long seconds = Math.max(1, (store.ttlMillis(key) + 999) / 1000);
            throw new SecureKitException(429, KitMessages.TRY_LATER).header("Retry-After", Long.toString(seconds));
        }
        return pjp.proceed();
    }
}
