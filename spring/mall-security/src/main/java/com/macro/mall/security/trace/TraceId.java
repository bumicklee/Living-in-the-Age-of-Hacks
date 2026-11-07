package com.macro.mall.security.trace;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * [다온마켓 추가] 요청 추적 번호(trace_id). 랩-설계.md 11절.
 *
 * 한 요청이 입구 → 앱 → 로그 → 큐 메시지까지 같은 번호를 달고 간다.
 * - 들어올 때: X-Trace-Id 헤더(없거나 모양이 이상하면 traceparent → X-Request-Id → 새로 만듦)
 * - 앱 안: MDC "trace_id"(일반 로그 줄의 [..]와 SECURITY 이벤트의 trace_id)
 * - 나갈 때: 응답 헤더 X-Trace-Id, RabbitMQ 메시지 헤더 X-Trace-Id
 */
public final class TraceId {
    public static final String HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "trace_id";
    public static final String ATTR = TraceId.class.getName();

    // 받아 주는 모양: 영문·숫자·._- 로 8~64자. 줄바꿈·공백·따옴표가 섞인 값은 버리고 새로 만든다(로그 위조 막기)
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{7,63}");
    private static final Pattern W3C_TRACE = Pattern.compile("[0-9a-f]{32}");

    private TraceId() {
    }

    /** 바깥에서 받은 값이 쓸 만하면 그대로, 아니면 null */
    public static String accept(String candidate) {
        if (candidate == null) {
            return null;
        }
        String v = candidate.trim();
        return SAFE.matcher(v).matches() ? v : null;
    }

    /** W3C traceparent(00-<trace-id>-<span-id>-<flags>)에서 trace-id만 */
    public static String fromTraceparent(String traceparent) {
        if (traceparent == null) {
            return null;
        }
        String[] parts = traceparent.trim().split("-");
        if (parts.length >= 4 && W3C_TRACE.matcher(parts[1]).matches() && !parts[1].matches("0+")) {
            return parts[1];
        }
        return null;
    }

    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 지금 스레드의 추적 번호(요청 처리 중이거나 큐 메시지를 받는 중일 때). 없으면 null */
    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
