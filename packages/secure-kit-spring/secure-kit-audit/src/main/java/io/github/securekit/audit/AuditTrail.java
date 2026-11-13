package io.github.securekit.audit;

import io.github.securekit.core.json.MiniJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * 감사 기록을 쓰는 곳. 로거 AUDIT에 JSON 한 줄(수집기가 Loki 등으로 보낸다) + 최근 200개(랩 전용 /_kit/audit).
 * 감사 기록은 지우지 않는다(/_kit/reset도 그대로 둔다).
 */
public final class AuditTrail {
    public static final int KEEP = 200;
    private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT");
    private static final Deque<Map<String, Object>> RECENT = new ArrayDeque<>();

    private AuditTrail() {
    }

    public static void write(Map<String, Object> record) {
        synchronized (RECENT) {
            RECENT.addLast(record);
            while (RECENT.size() > KEEP) RECENT.removeFirst();
        }
        AUDIT.info(MiniJson.write(record));
    }

    public static List<Map<String, Object>> recent() {
        synchronized (RECENT) {
            return new ArrayList<>(RECENT);
        }
    }
}
