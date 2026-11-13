package io.github.securekit.web;

import io.github.securekit.core.event.SecurityEvents;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 지금 처리 중인 요청에서 「누가(actor)」와 「추적 번호」를 읽는다. 이벤트·감사 기록이 같은 규칙을 쓴다.
 *
 * actor 규칙
 * - 요청 속성 secure-kit.actor가 있으면 그 값(자기 로그인을 쓰는 회사 서비스가 넣는다. 예: admin:shop-admin)
 * - secure-kit 토큰으로 로그인했으면: 파트너 partner:앱 · 역할 admin이면 admin:아이디 · 아니면 user:아이디
 * - 없으면 anonymous
 */
public final class KitContext {
    public static final String ACTOR_ATTR = "secure-kit.actor";
    public static final String PRINCIPAL_ATTR = "secure-kit.principal";
    public static final String TRACE_ATTR = "secure-kit.trace-id";

    private static final ThreadLocal<HttpServletRequest> CURRENT = new ThreadLocal<>();

    private KitContext() {
    }

    /** TraceIdFilter가 요청 처음에 묶고 끝에 푼다(Spring의 RequestContextHolder보다 먼저 돈다) */
    static void bind(HttpServletRequest request) {
        CURRENT.set(request);
    }

    static void unbind() {
        CURRENT.remove();
    }

    public static HttpServletRequest current() {
        HttpServletRequest r = CURRENT.get();
        if (r != null) return r;
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes s ? s.getRequest() : null;
    }

    public static String traceId(HttpServletRequest request) {
        Object t = request == null ? null : request.getAttribute(TRACE_ATTR);
        return t == null ? "-" : t.toString();
    }

    public static KitPrincipal principal(HttpServletRequest request) {
        Object p = request == null ? null : request.getAttribute(PRINCIPAL_ATTR);
        return p instanceof KitPrincipal kp ? kp : null;
    }

    public static String actor(HttpServletRequest request) {
        if (request == null) return "anonymous";
        Object a = request.getAttribute(ACTOR_ATTR);
        if (a != null && !a.toString().isBlank()) return a.toString();
        KitPrincipal p = principal(request);
        return p == null ? "anonymous" : p.actor();
    }

    /** actor에서 아이디만(admin:kit-admin → kit-admin). 승인·재인증에서 본인 확인에 쓴다 */
    public static String subject(HttpServletRequest request) {
        KitPrincipal p = principal(request);
        if (p != null) return p.sub();
        String a = actor(request);
        int i = a.indexOf(':');
        return i < 0 ? a : a.substring(i + 1);
    }

    /** 보안 이벤트가 이 규칙으로 actor·추적 번호를 채우게 한다(스타터가 한 번 부른다) */
    public static void installEventContext() {
        SecurityEvents.useContext(new SecurityEvents.Context() {
            @Override
            public String actor() {
                return KitContext.actor(current());
            }

            @Override
            public String traceId() {
                return KitContext.traceId(current());
            }
        });
    }
}
