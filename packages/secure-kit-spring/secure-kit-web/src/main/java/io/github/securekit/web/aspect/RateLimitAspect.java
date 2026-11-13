package io.github.securekit.web.aspect;

import io.github.securekit.core.KitMessages;
import io.github.securekit.core.SecureKitException;
import io.github.securekit.core.event.SecurityEvents;
import io.github.securekit.core.store.CounterStore;
import io.github.securekit.core.store.Rate;
import io.github.securekit.web.annotation.RateLimited;
import io.github.securekit.web.support.ArgFields;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

/**
 * &#64;RateLimited 처리. 실패(401)만 계정별·IP별로 센다. 성공하면 그 계정의 실패 수를 지운다.
 * 한도: secure-kit.rate-limit.&lt;이름&gt;.per-account · per-ip (없으면 어노테이션 기본값).
 */
@Aspect
@Order(KitOrder.RATE_LIMIT)
public class RateLimitAspect {
    private final CounterStore store;
    private final Environment env;

    public RateLimitAspect(CounterStore store, Environment env) {
        this.store = store;
        this.env = env;
    }

    @Around("@annotation(io.github.securekit.web.annotation.RateLimited)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        RateLimited rl = ArgFields.method(pjp).getAnnotation(RateLimited.class);
        String name = rl.value();
        Rate perAccount = Rate.parse(env.getProperty("secure-kit.rate-limit." + name + ".per-account", rl.perAccount()));
        Rate perIp = Rate.parse(env.getProperty("secure-kit.rate-limit." + name + ".per-ip", rl.perIp()));

        Object account = ArgFields.field(pjp.getArgs(), rl.account());
        HttpServletRequest req = ArgFields.request();
        String ip = req == null ? "-" : req.getRemoteAddr();
        String accountKey = account == null ? null : "rl:" + name + ":account:" + account;
        String ipKey = "rl:" + name + ":ip:" + ip;

        if (accountKey != null && store.count(accountKey) >= perAccount.max()) {
            throw limited(accountKey, "account:" + account, "계정별 실패 한도 " + perAccount);
        }
        if (store.count(ipKey) >= perIp.max()) {
            throw limited(ipKey, "ip:" + ip, "IP별 실패 한도 " + perIp);
        }

        Object result;
        try {
            result = pjp.proceed();
        } catch (Throwable t) {
            if (isAuthFailure(t)) countFailure(accountKey, perAccount, ipKey, perIp);
            throw t;
        }
        if (result instanceof ResponseEntity<?> re && re.getStatusCode().value() == 401) {
            countFailure(accountKey, perAccount, ipKey, perIp);
        } else if (accountKey != null) {
            store.delete(accountKey);
        }
        return result;
    }

    private void countFailure(String accountKey, Rate perAccount, String ipKey, Rate perIp) {
        if (accountKey != null) store.increment(accountKey, perAccount.window());
        store.increment(ipKey, perIp.window());
    }

    private static boolean isAuthFailure(Throwable t) {
        if (t instanceof SecureKitException se) return se.status() == 401;
        return t instanceof ResponseStatusException rse && rse.getStatusCode().value() == 401;
    }

    private SecureKitException limited(String key, String target, String reason) {
        long seconds = Math.max(1, (store.ttlMillis(key) + 999) / 1000);
        SecurityEvents.warn("auth.login.rate_limited", "rate-limit", target, reason);
        return new SecureKitException(429, KitMessages.TRY_LATER).header("Retry-After", Long.toString(seconds));
    }
}
