package io.github.securekit.audit;

import io.github.securekit.core.SecureKitException;
import io.github.securekit.web.KitContext;
import io.github.securekit.web.aspect.KitOrder;
import io.github.securekit.web.support.ArgFields;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * &#64;Audited 처리. 가장 바깥 애스펙트라 막힌 요청(403)도 result=denied로 남는다.
 * 기록을 남긴 뒤 예외는 그대로 던진다(감사가 응답을 바꾸지 않는다).
 */
@Aspect
@Order(KitOrder.AUDIT)
public class AuditAspect {
    private final String reasonHeader;

    public AuditAspect(String reasonHeader) {
        this.reasonHeader = reasonHeader;
    }

    @Around("@annotation(io.github.securekit.audit.Audited)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        Method method = ArgFields.method(pjp);
        Audited audited = method.getAnnotation(Audited.class);
        HttpServletRequest req = ArgFields.request();
        Object result;
        try {
            result = pjp.proceed();
        } catch (Throwable t) {
            boolean denied = t instanceof SecureKitException se && (se.status() == 401 || se.status() == 403);
            AuditTrail.write(record(req, audited, target(pjp, audited, req), 0, denied ? "denied" : "error"));
            throw t;
        }
        AuditTrail.write(record(req, audited, target(pjp, audited, req), count(result), "ok"));
        return result;
    }

    private Map<String, Object> record(HttpServletRequest req, Audited a, String target, int count, String result) {
        String reason = req == null ? null : req.getHeader(reasonHeader);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ts", Instant.now().toString());
        r.put("trace_id", KitContext.traceId(req));
        r.put("actor", KitContext.actor(req));
        r.put("action", a.action());
        r.put("target", target);
        r.put("count", count);
        r.put("reason", reason == null ? "" : reason.length() > 64 ? reason.substring(0, 64) : reason);
        r.put("result", result);
        return r;
    }

    /** member:3 · member:list?page=1&size=20 · member:list */
    static String target(ProceedingJoinPoint pjp, Audited a, HttpServletRequest req) {
        String resource = a.resource().isEmpty() ? a.action().split("\\.")[0] : a.resource();
        Annotation[][] anns = ArgFields.method(pjp).getParameterAnnotations();
        for (int i = 0; i < anns.length; i++) {
            for (Annotation ann : anns[i]) {
                if (ann instanceof PathVariable) return resource + ":" + pjp.getArgs()[i];
            }
        }
        String page = req == null ? null : first(req.getParameter("pageNum"), req.getParameter("page"));
        String size = req == null ? null : first(req.getParameter("pageSize"), req.getParameter("size"));
        if (page != null && size != null) return resource + ":list?page=" + digits(page) + "&size=" + digits(size);
        return resource + ":list";
    }

    /** 돌려준 건수 */
    static int count(Object result) {
        Object body = result instanceof ResponseEntity<?> re ? re.getBody() : result;
        if (body == null) return 0;
        if (body instanceof Collection<?> c) return c.size();
        if (body instanceof Map<?, ?> m && m.get("list") instanceof Collection<?> list) return list.size();
        Object list = ArgFields.fieldOf(body, "list");
        if (list instanceof Collection<?> c) return c.size();
        return 1;
    }

    private static String first(String a, String b) {
        return a != null ? a : b;
    }

    private static String digits(String v) {
        return v.replaceAll("[^0-9]", "");
    }
}
