package com.macro.mall.security.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * [다온마켓 8-3] 감사 로그 한 줄 쓰기. 로거 이름 AUDIT, 한 줄 JSON.
 *
 * logback-spring.xml이 AUDIT 로거만 따로 파일(DAON_AUDIT_FILE, 기본 /var/logs/daon/audit.jsonl, 볼륨 audit-logs)로 보내고
 * 앱 로그에는 섞지 않는다(additivity=false). 8-4에서 수집기가 이 파일을 stream=audit으로 읽는다.
 */
public final class AuditLog {
    private static final Logger LOG = LoggerFactory.getLogger("AUDIT");
    private static final ObjectMapper JSON = new ObjectMapper();

    private AuditLog() {
    }

    public static synchronized void write(Map<String, Object> entry) {
        try {
            LOG.info(JSON.writeValueAsString(entry));
        } catch (JsonProcessingException e) {
            LOG.info("{\"action\":\"" + entry.get("action") + "\",\"error\":\"json\"}");
        }
    }

    /** 대량 조회 기준(SECURITY data.read.bulk). 랩은 30건(시드 회원 40명으로 보이게, 결정 #16) */
    public static int bulkThreshold() {
        String v = System.getenv("DAON_AUDIT_BULK_THRESHOLD");
        try {
            return v == null || v.isBlank() ? 30 : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return 30;
        }
    }
}
