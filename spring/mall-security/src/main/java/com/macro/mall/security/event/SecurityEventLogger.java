package com.macro.mall.security.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.security.trace.TraceId;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [다온마켓 추가] 보안 이벤트 한 줄 로그(랩-설계.md 10절, course/SECURITY_EVENTS.md).
 *
 * <pre>
 * SecurityEventLogger.denied("access.denied.not_owner", "2-2")
 *         .actor("member:" + member.getId())
 *         .target("order:" + orderId)
 *         .reason("본인 주문이 아니거나 없는 주문")
 *         .log();
 * </pre>
 *
 * - 로거 이름은 SECURITY. 거부는 WARN, 의심은 INFO.
 * - 한 줄 JSON, 키: event lesson actor target ip ua reason count ts trace_id.
 * - 넣지 않는 것: 개인정보 원문, 토큰·비밀번호, 요청 본문. target에는 ID만 넣는다.
 * - count: 같은 event·actor가 최근 10분 안에 몇 번째인지(이 앱 인스턴스 안에서만 센다).
 */
public final class SecurityEventLogger {
    private static final Logger LOG = LoggerFactory.getLogger("SECURITY");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long WINDOW_MS = 10 * 60 * 1000L;
    private static final Map<String, Deque<Long>> RECENT = new ConcurrentHashMap<>();
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final boolean denied;
    private final String event;
    private final String lesson;
    private String actor;
    private String target;
    private String reason;
    private HttpServletRequest request;

    private SecurityEventLogger(boolean denied, String event, String lesson) {
        this.denied = denied;
        this.event = event;
        this.lesson = lesson;
    }

    /** 막았을 때(WARN) */
    public static SecurityEventLogger denied(String event, String lesson) {
        return new SecurityEventLogger(true, event, lesson);
    }

    /** 처리했지만 패턴이 이상할 때(INFO) */
    public static SecurityEventLogger suspicious(String event, String lesson) {
        return new SecurityEventLogger(false, event, lesson);
    }

    /** 누가: 회원·관리자 ID나 역할(예: member:102, admin:staff_goods). 비우면 로그인 정보의 이름을 쓴다 */
    public SecurityEventLogger actor(String actor) {
        this.actor = actor;
        return this;
    }

    /** 무엇을: ID만(예: order:9001, path:/daon/customer/list) */
    public SecurityEventLogger target(String target) {
        this.target = target;
        return this;
    }

    public SecurityEventLogger reason(String reason) {
        this.reason = reason;
        return this;
    }

    /** 필터처럼 RequestContextHolder가 아직 없는 곳에서는 요청을 직접 넘긴다 */
    public SecurityEventLogger request(HttpServletRequest request) {
        this.request = request;
        return this;
    }

    public void log() {
        HttpServletRequest req = request != null ? request : currentRequest();
        String who = actor != null ? actor : currentActor();
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("event", event);
        e.put("lesson", lesson);
        e.put("actor", who);
        e.put("target", target);
        e.put("ip", req == null ? null : req.getRemoteAddr());
        e.put("ua", req == null ? null : cut(req.getHeader("User-Agent"), 120));
        e.put("reason", reason);
        e.put("count", count(event + "|" + who));
        e.put("ts", OffsetDateTime.ofInstant(Instant.now(), ZONE).toString());
        e.put("trace_id", traceId(req));
        String line;
        try {
            line = JSON.writeValueAsString(e);
        } catch (JsonProcessingException ex) {
            line = "{\"event\":\"" + event + "\",\"error\":\"json\"}";
        }
        if (denied) {
            LOG.warn(line);
        } else {
            LOG.info(line);
        }
    }

    private static int count(String key) {
        long now = System.currentTimeMillis();
        Deque<Long> q = RECENT.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            q.addLast(now);
            while (!q.isEmpty() && now - q.peekFirst() > WINDOW_MS) {
                q.pollFirst();
            }
            return q.size();
        }
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a) {
            return a.getRequest();
        }
        return null;
    }

    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            return "anonymous";
        }
        return auth.getName();
    }

    /** 추적 번호: 요청 맨 앞 TraceIdFilter가 정한 값(MDC) → 요청 속성 → (필터 밖이면) 새로 만듦. 랩-설계.md 11절 */
    private static String traceId(HttpServletRequest req) {
        String id = TraceId.current();
        if (id == null && req != null && req.getAttribute(TraceId.ATTR) instanceof String s) {
            id = s;
        }
        return id != null ? id : TraceId.newId();
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
