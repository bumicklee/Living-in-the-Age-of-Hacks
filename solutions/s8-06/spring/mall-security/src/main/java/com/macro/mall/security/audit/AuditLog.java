package com.macro.mall.security.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.security.securekit.SecureKitSwitch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Map;

/**
 * [다온마켓 8-3] 감사 로그 한 줄 쓰기. 로거 이름 AUDIT, 한 줄 JSON.
 * [다온마켓 8-6] 줄마다 seq·prev·hash를 붙여 체인으로 묶는다(AuditChain). 방어 스위치 audit-chain을 끄면 8-3 모습(체인 없음).
 *
 * logback-spring.xml이 AUDIT 로거만 따로 파일(DAON_AUDIT_FILE, 기본 /var/logs/daon/audit.jsonl, 볼륨 audit-logs)로 보내고
 * 앱 로그에는 섞지 않는다(additivity=false). 8-4부터 수집기가 이 파일을 stream=audit으로 Loki에 보낸다.
 * Loki의 사본이 머리 해시(마지막 seq·hash)를 맡는다: 앱 망에서는 Loki에 닿지 않고, 삭제 API도 꺼 두었다.
 */
public final class AuditLog {
    private static final Logger LOG = LoggerFactory.getLogger("AUDIT");
    private static final ObjectMapper JSON = new ObjectMapper();
    public static final Path FILE = Path.of(System.getenv().getOrDefault("DAON_AUDIT_FILE", "/var/logs/daon/audit.jsonl"));
    private static final AuditChain CHAIN = new AuditChain();
    private static volatile boolean resumed;

    private AuditLog() {
    }

    public static synchronized void write(Map<String, Object> entry) {
        if (SecureKitSwitch.on("audit-chain")) {
            if (!resumed) {
                CHAIN.resumeFrom(FILE);          // 앱이 다시 떠도 파일의 마지막 줄에서 이어 간다
                resumed = true;
            }
            // 사유 칸은 사람이 적는 글자다. 로그 변환기(8-5)가 나중에 가리면 해시가 어긋나므로 해시를 만들기 전에 먼저 가린다
            Map<String, Object> e = new java.util.LinkedHashMap<>(entry);
            if (e.get("reason") instanceof String r) {
                e.put("reason", com.macro.mall.common.log.LogMask.mask(r));
            }
            LOG.info(CHAIN.link(e));
            return;
        }
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
