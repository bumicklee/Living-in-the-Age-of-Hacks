package com.macro.mall.security.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * [다온마켓 5-3 · 랩 전용] 이미 평문으로 들어 있는 개인정보 칸을 한 번 잠근다(교재 5.3 「기존 행 바꾸기」).
 *
 * <pre>
 * 1) 칸 길이를 늘린다(암호문이 길다): VARCHAR(512). 몇 번 돌려도 같다
 * 2) enc:로 시작하지 않는 값을 FieldCrypto로 잠가 다시 저장한다. 값은 로그에 남기지 않고 건수만 남긴다
 * </pre>
 *
 * - 환경 변수 DAON_PII_MIGRATE=true인 앱 하나(mall-portal)에서만, 스위치 pii-encrypt가 켜져 있을 때, 앱이 뜬 뒤 한 번 돈다.
 * - 회사에서는 앱이 뜰 때 이런 일을 하지 않는다. 점검 시간에 따로 실행하는 이전 도구로 하고, 되돌릴 수 있게 백업부터 한다.
 * - 매퍼를 거치지 않는 직접 SQL이라 타입 핸들러가 끼어들지 않는다.
 */
@Component
public class PiiReencrypt implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(PiiReencrypt.class);
    private static final int BATCH = 500;

    /** 표, 칸, 길이를 늘릴 때 지킬 정의(널 허용·설명은 원래대로) */
    private static final String[][] COLUMNS = {
            {"ums_member", "phone", "VARCHAR(512) NULL COMMENT '휴대폰 번호(암호문)'"},
            {"oms_order", "receiver_phone", "VARCHAR(512) NOT NULL COMMENT '수령인 전화번호(암호문)'"},
            {"oms_order", "receiver_detail_address", "VARCHAR(512) NULL COMMENT '상세 주소(암호문)'"},
            {"ums_member_receive_address", "phone_number", "VARCHAR(512) NULL COMMENT '전화번호(암호문)'"},
            {"ums_member_receive_address", "detail_address", "VARCHAR(512) NULL COMMENT '상세 주소(암호문)'"},
    };

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private FieldCrypto crypto;
    @Autowired
    private StringRedisTemplate redis;

    @Override
    public void run(ApplicationArguments args) {
        if (!"true".equalsIgnoreCase(System.getenv("DAON_PII_MIGRATE")) || !FieldCrypto.enabled()) {
            return;
        }
        try {
            long total = 0;
            for (String[] c : COLUMNS) {
                jdbc.execute("ALTER TABLE " + c[0] + " MODIFY " + c[1] + " " + c[2]);
                long done = 0;
                while (true) {
                    List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, " + c[1] + " AS v FROM " + c[0]
                            + " WHERE " + c[1] + " IS NOT NULL AND " + c[1] + " <> '' AND " + c[1] + " NOT LIKE 'enc:%' LIMIT " + BATCH);
                    if (rows.isEmpty()) {
                        break;
                    }
                    for (Map<String, Object> r : rows) {
                        jdbc.update("UPDATE " + c[0] + " SET " + c[1] + " = ? WHERE id = ?", crypto.encrypt(String.valueOf(r.get("v"))), r.get("id"));
                    }
                    done += rows.size();
                }
                if (done > 0) {
                    LOG.info("[A4] 개인정보 재암호화: {}.{} {}행", c[0], c[1], done);
                }
                total += done;
            }
            clearMemberCache();
            LOG.info("[A4] 개인정보 재암호화 끝: 모두 {}행", total);
        } catch (RuntimeException e) {
            LOG.error("[A4] 개인정보 재암호화 실패(앱은 계속 뜬다): {}", e.getMessage());
        }
    }

    /** 옛 평문이 든 회원 캐시를 비운다(KEYS는 금지된 명령이라 SCAN으로 훑는다) */
    private void clearMemberCache() {
        long n = 0;
        try (Cursor<String> cur = redis.scan(ScanOptions.scanOptions().match("mall:ums:member:*").count(200).build())) {
            while (cur.hasNext()) {
                redis.delete(cur.next());
                n++;
            }
        }
        if (n > 0) {
            LOG.info("[A4] 옛 회원 캐시 {}개를 비웠습니다", n);
        }
    }
}
