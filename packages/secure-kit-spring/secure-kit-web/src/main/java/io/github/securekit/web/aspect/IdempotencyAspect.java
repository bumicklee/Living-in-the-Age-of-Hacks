package io.github.securekit.web.aspect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.securekit.core.Hashes;
import io.github.securekit.core.SecureKitException;
import io.github.securekit.core.event.SecurityEvents;
import io.github.securekit.core.store.CounterStore;
import io.github.securekit.core.store.Rate;
import io.github.securekit.web.annotation.Idempotent;
import io.github.securekit.web.support.ArgFields;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;
import java.time.Duration;

/**
 * &#64;Idempotent 처리. 저장 모양(키 하나에 문자열 하나):
 * P|지문 = 처리 중 · D|지문|상태|응답 JSON = 끝남.
 * 메서드는 본문 객체(Map·DTO)나 ResponseEntity를 돌려준다.
 */
@Aspect
@Order(KitOrder.IDEMPOTENT)
public class IdempotencyAspect {
    private final CounterStore store;
    private final ObjectMapper json;

    public IdempotencyAspect(CounterStore store, ObjectMapper json) {
        this.store = store;
        this.json = json;
    }

    @Around("@annotation(io.github.securekit.web.annotation.Idempotent)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        Method method = ArgFields.method(pjp);
        Idempotent ann = method.getAnnotation(Idempotent.class);
        HttpServletRequest req = ArgFields.request();
        String key = req == null ? null : req.getHeader(ann.header());
        if (key == null || key.isBlank() || key.length() > 128) {
            SecurityEvents.info("idempotency.denied.missing_key", "idempotency", "path:" + (req == null ? "-" : req.getRequestURI()), ann.header() + " 머리가 없다");
            throw new SecureKitException(400, ann.header() + " 머리가 필요합니다");
        }
        String storeKey = "idem:" + method.getDeclaringClass().getSimpleName() + "." + method.getName() + ":" + key;
        String fp = ArgFields.fingerprint(json, ArgFields.body(pjp));
        Duration ttl = Rate.duration(ann.ttl());

        if (!store.putIfAbsent(storeKey, "P|" + fp, ttl)) {
            String saved = store.get(storeKey);
            if (saved == null) throw new SecureKitException(409, "잠시 후 다시 시도하세요");
            String[] p = saved.split("\\|", 4);
            if (!p[1].equals(fp)) {
                SecurityEvents.warn("idempotency.denied.key_reuse", "idempotency", "key:" + Hashes.short8(key), "같은 키에 다른 본문");
                throw new SecureKitException(422, "같은 키로 다른 요청을 보낼 수 없습니다");
            }
            if (p[0].equals("P")) throw new SecureKitException(409, "같은 요청을 처리하고 있습니다");
            return replay(method, Integer.parseInt(p[2]), p[3]);
        }

        Object result;
        try {
            result = pjp.proceed();
        } catch (Throwable t) {
            store.delete(storeKey);   // 실패한 요청은 같은 키로 다시 시도할 수 있다
            throw t;
        }
        int status = 200;
        Object body = result;
        if (result instanceof ResponseEntity<?> re) {
            status = re.getStatusCode().value();
            body = re.getBody();
        }
        store.put(storeKey, "D|" + fp + "|" + status + "|" + json.writeValueAsString(body), ttl);
        return result;
    }

    private Object replay(Method method, int status, String body) throws Exception {
        if (ResponseEntity.class.isAssignableFrom(method.getReturnType())) {
            JsonNode node = json.readTree(body);
            return ResponseEntity.status(status).body(node);
        }
        return json.readValue(body, json.getTypeFactory().constructType(method.getGenericReturnType()));
    }
}
