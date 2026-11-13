package com.daon.approval;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [A10] 보안 이벤트 한 줄(로거 SECURITY). 관리자 앱의 SecurityEventLogger와 같은 키·같은 모양(course/SECURITY_EVENTS.md).
 * 승인 서비스는 mall 코드를 가져오지 않으므로 같은 형식을 여기서 다시 쓴다.
 *
 * - target에는 ID만(return:31, approval:ap…, admin:staff_order). 금액은 넣지 않는다(거래 정보라 필요하면 승인 서비스 표를 따로 본다).
 * - trace_id는 관리자 앱이 넘긴 X-Trace-Id(TraceFilter가 MDC에 넣는다). 같은 번호가 mall-admin 줄과 approval 줄에 함께 남는다.
 */
public final class SecurityEvents {
    private static final Logger LOG = LoggerFactory.getLogger("SECURITY");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, Deque<Long>> RECENT = new ConcurrentHashMap<>();
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private SecurityEvents() {
    }

    public static void denied(String event, String lesson, String actor, String target, String reason) {
        log(true, event, lesson, actor, target, reason);
    }

    public static void info(String event, String lesson, String actor, String target, String reason) {
        log(false, event, lesson, actor, target, reason);
    }

    private static void log(boolean warn, String event, String lesson, String actor, String target, String reason) {
        HttpServletRequest req = RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a ? a.getRequest() : null;
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("event", event);
        e.put("lesson", lesson);
        e.put("actor", actor == null ? "anonymous" : actor);
        e.put("target", target);
        e.put("ip", req == null ? null : req.getRemoteAddr());
        String ua = req == null ? null : req.getHeader("User-Agent");
        e.put("ua", ua == null ? null : ua.substring(0, Math.min(ua.length(), 120)));
        e.put("reason", reason);
        e.put("count", count(event + "|" + actor));
        e.put("ts", OffsetDateTime.now(ZONE).toString());
        String trace = MDC.get(TraceFilter.MDC_KEY);
        e.put("trace_id", trace != null ? trace : TraceFilter.newId());
        String line;
        try {
            line = JSON.writeValueAsString(e);
        } catch (Exception ex) {
            line = "{\"event\":\"" + event + "\",\"error\":\"json\"}";
        }
        if (warn) {
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
            while (!q.isEmpty() && now - q.peekFirst() > 10 * 60 * 1000L) {
                q.pollFirst();
            }
            return q.size();
        }
    }
}
