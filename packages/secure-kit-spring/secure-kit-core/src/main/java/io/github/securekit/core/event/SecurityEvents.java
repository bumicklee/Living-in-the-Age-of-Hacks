package io.github.securekit.core.event;

import io.github.securekit.core.json.MiniJson;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 막으면 남긴다: 부품이 요청을 막으면 보안 이벤트 한 줄을 쓴다.
 *
 * <pre>
 * WARN SECURITY {"seq":1,"event":"auth.login.rate_limited","part":"rate-limit","level":"WARN",
 *                "actor":"anonymous","target":"account:kit-user","reason":"...","ts":"...","trace_id":"..."}
 * </pre>
 *
 * - 키 9개는 고정이다(8장 수집기가 그대로 읽는다).
 * - 값(비밀번호·토큰·전화번호 원문)은 넣지 않는다. target에는 ID나 해시만.
 * - 최근 500개는 메모리에 둔다(랩 전용 GET /_kit/events).
 * - 누가(actor)·추적 번호는 {@link Context}가 정한다. 웹 모듈이 요청에서 읽는 것으로 바꿔 끼운다.
 */
public final class SecurityEvents {
    public static final int KEEP = 500;

    /** 지금 처리 중인 요청의 actor·추적 번호 */
    public interface Context {
        String actor();

        String traceId();
    }

    private static volatile Context context = new Context() {
        public String actor() { return "anonymous"; }
        public String traceId() { return "-"; }
    };
    /** (level, json) → 한 줄 쓰기. 기본은 표준 오류. 스타터가 SLF4J 로거 SECURITY로 바꾼다 */
    private static volatile BiConsumer<String, String> printer =
            (level, json) -> System.err.println(level + " SECURITY " + json);

    private static final Deque<Map<String, Object>> RECENT = new ArrayDeque<>();
    private static long seq;

    private SecurityEvents() {
    }

    public static void useContext(Context c) {
        context = c;
    }

    public static void usePrinter(BiConsumer<String, String> p) {
        printer = p;
    }

    /** 막았을 때 */
    public static Map<String, Object> warn(String event, String part, String target, String reason) {
        return emit("WARN", event, part, null, target, reason);
    }

    /** 막지는 않았지만 남길 일(의심·고위험 작업) */
    public static Map<String, Object> info(String event, String part, String target, String reason) {
        return emit("INFO", event, part, null, target, reason);
    }

    /** actor를 직접 정할 때(null이면 Context에서 읽는다) */
    public static Map<String, Object> emit(String level, String event, String part, String actor, String target, String reason) {
        Map<String, Object> e = new LinkedHashMap<>();
        String json;
        synchronized (RECENT) {
            e.put("seq", ++seq);
            e.put("event", event);
            e.put("part", part);
            e.put("level", level);
            e.put("actor", actor != null ? actor : current(true));
            e.put("target", target == null ? "-" : target);
            e.put("reason", reason == null ? "" : reason);
            e.put("ts", Instant.now().toString());
            e.put("trace_id", current(false));
            RECENT.addLast(e);
            while (RECENT.size() > KEEP) RECENT.removeFirst();
            json = MiniJson.write(e);
        }
        printer.accept(level, json);
        return e;
    }

    /** seq가 after보다 큰 이벤트(최근 500개 안에서) */
    public static List<Map<String, Object>> after(long after) {
        List<Map<String, Object>> out = new ArrayList<>();
        synchronized (RECENT) {
            for (Map<String, Object> e : RECENT) {
                if ((Long) e.get("seq") > after) out.add(e);
            }
        }
        return out;
    }

    public static long lastSeq() {
        synchronized (RECENT) {
            return seq;
        }
    }

    /** Context가 오류를 내도 이벤트는 남긴다 */
    private static String current(boolean actor) {
        try {
            String v = actor ? context.actor() : context.traceId();
            if (v != null && !v.isEmpty()) return v;
        } catch (RuntimeException ignored) {
            // 요청 밖(기동 중 등)
        }
        return actor ? "anonymous" : "-";
    }
}
