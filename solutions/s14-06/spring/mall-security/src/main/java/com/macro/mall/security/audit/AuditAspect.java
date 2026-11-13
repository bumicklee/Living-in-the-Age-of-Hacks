package com.macro.mall.security.audit;

import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.securekit.SecureKitSwitch;
import com.macro.mall.security.trace.TraceId;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.annotation.Annotation;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * [다온마켓 8-3] &#64;Audited가 붙은 관리자 조회가 끝나면 감사 로그(AUDIT) 한 줄을 남긴다.
 *
 * - 필드 8개: ts trace_id actor action target count reason result (docs/audit-schema.yaml)
 * - count는 실제로 돌려준 건수다. CommonPage면 그 페이지의 list 크기(전체 건수 total이 아니다), List면 크기, 값 하나면 1, null이면 0.
 * - 예외로 끝난 조회도 result=error로 남긴다(남기고 예외는 그대로 던진다).
 * - 넣지 않는 것: 이름·전화·주소 원문, 토큰, 요청·응답 본문. target에는 ID나 페이지 번호만.
 * - 한 번에 기준(DAON_AUDIT_BULK_THRESHOLD, 랩 30건) 이상이면 SECURITY data.read.bulk(INFO)를 한 줄 더 남긴다.
 *   한 요청만으로는 정상 업무와 구별이 어려워 경보가 아니라 8-4 「계정별 누적」 규칙의 재료다.
 * - [15장 시작 모습] 수정 행위도 남긴다(받는 사람 정보 수정 change.order.receiver). 대상 ID는 @Audited(idField = "orderId")로 본문에서 읽는다.
 *   action 이름을 order.*로 짓지 않는 이유: 8-4 대량 조회 규칙(bulk.yaml)이 order.*의 count를 「읽은 건수」로 더하기 때문이다.
 * - 방어 스위치 audit-log(make switch K=audit-log V=off)를 끄면 남기지 않는다(영상의 레드).
 */
@Aspect
@Component
@Order(2)
public class AuditAspect {
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    public static final String REASON_HEADER = "X-Access-Reason";

    @Around("@annotation(audited)")
    public Object around(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        if (!SecureKitSwitch.on("audit-log")) {
            return pjp.proceed();
        }
        Object ret;
        try {
            ret = pjp.proceed();
        } catch (Throwable t) {
            AuditLog.write(entry(pjp, audited, 0, "error"));
            throw t;
        }
        int count = countOf(ret);
        String result = resultOf(ret);
        AuditLog.write(entry(pjp, audited, count, result));
        int threshold = AuditLog.bulkThreshold();
        if ("ok".equals(result) && count >= threshold) {
            SecurityEventLogger.suspicious("data.read.bulk", "8-3")
                    .actor(actor())
                    .target("action:" + audited.action())
                    .reason("한 번에 기준 건수(" + threshold + "건) 이상 조회")
                    .log();
        }
        return ret;
    }

    private static Map<String, Object> entry(ProceedingJoinPoint pjp, Audited audited, int count, String result) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("ts", OffsetDateTime.now(ZONE).toString());
        e.put("trace_id", TraceId.current());
        e.put("actor", actor());
        e.put("action", audited.action());
        e.put("target", target(pjp, audited));
        e.put("count", count);
        e.put("reason", reason());
        e.put("result", result);
        return e;
    }

    /** 실제로 돌려준 건수 */
    static int countOf(Object ret) {
        Object data = ret instanceof CommonResult<?> r ? r.getData() : ret;
        if (data == null) {
            return 0;
        }
        if (data instanceof CommonPage<?> p) {
            return p.getList() == null ? 0 : p.getList().size();
        }
        if (data instanceof Collection<?> c) {
            return c.size();
        }
        return 1;
    }

    static String resultOf(Object ret) {
        if (ret instanceof CommonResult<?> r) {
            long code = r.getCode();
            if (code == 200) {
                return "ok";
            }
            return code == 401 || code == 403 ? "denied" : "error";
        }
        return "ok";
    }

    private static String actor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            return "anonymous";
        }
        return "admin:" + auth.getName();
    }

    /** 종류:ID(경로 변수) 또는 종류:list?page=…&size=… (검색어 같은 조건 값은 개인정보일 수 있어 넣지 않는다) */
    private static String target(ProceedingJoinPoint pjp, Audited audited) {
        Object[] args = pjp.getArgs();
        Annotation[][] anns = ((MethodSignature) pjp.getSignature()).getMethod().getParameterAnnotations();
        for (int i = 0; i < anns.length; i++) {
            for (Annotation a : anns[i]) {
                if (a instanceof PathVariable && args[i] != null) {
                    return audited.resource() + ":" + safe(String.valueOf(args[i]), 40);
                }
            }
        }
        if (!audited.idField().isEmpty()) {
            // [15장 시작 모습] 수정 요청처럼 대상이 본문에 있을 때: 정해 둔 칸(getter) 하나만 읽는다
            String id = bodyId(args, anns, audited.idField());
            if (id != null) {
                return audited.resource() + ":" + safe(id, 40);
            }
        }
        HttpServletRequest req = request();
        StringBuilder t = new StringBuilder(audited.resource()).append(":list");
        if (req != null) {
            String page = req.getParameter("pageNum");
            String size = req.getParameter("pageSize");
            if (page != null || size != null) {
                t.append("?page=").append(page == null ? "1" : safe(page, 6)).append("&size=").append(size == null ? "-" : safe(size, 6));
            }
        }
        return t.toString();
    }

    /** @RequestBody 인자에서 칸 하나(getter 또는 Map 키)만 읽는다. 못 찾으면 null */
    private static String bodyId(Object[] args, Annotation[][] anns, String field) {
        String getter = "get" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
        for (int i = 0; i < anns.length; i++) {
            boolean body = false;
            for (Annotation a : anns[i]) {
                body |= a instanceof org.springframework.web.bind.annotation.RequestBody;
            }
            Object arg = args[i];
            if (!body || arg == null) {
                continue;
            }
            if (arg instanceof Map<?, ?> m) {
                Object v = m.get(field);
                return v == null ? null : String.valueOf(v);
            }
            try {
                Object v = arg.getClass().getMethod(getter).invoke(arg);
                return v == null ? null : String.valueOf(v);
            } catch (ReflectiveOperationException e) {
                return null;
            }
        }
        return null;
    }

    /** 업무 사유: X-Access-Reason(상담 번호 등). 한글은 URL 인코딩으로 보낸다. 줄바꿈·제어 문자는 지우고 100자까지 */
    private static String reason() {
        HttpServletRequest req = request();
        String v = req == null ? null : req.getHeader(REASON_HEADER);
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            v = URLDecoder.decode(v, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            // 인코딩이 아니면 그대로 쓴다
        }
        v = v.replaceAll("\\p{Cntrl}", " ").trim();
        return v.length() > 100 ? v.substring(0, 100) : v;
    }

    private static String safe(String v, int max) {
        String s = v.replaceAll("[^A-Za-z0-9._-]", "_");
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static HttpServletRequest request() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a) {
            return a.getRequest();
        }
        return null;
    }
}
