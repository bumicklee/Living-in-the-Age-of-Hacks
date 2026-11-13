package com.macro.mall.portal.partner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * [교육용 추가 T33] 파트너 연동 표와 가짜 시드(앱이 뜰 때 한 번, 멱등). 원본 mall·EverShop에는 파트너 API가 없다.
 *
 * <pre>
 * daon_partner_app         연동 앱 대장: 이름·담당자·범위·상태·계약 만료·마지막 사용·비밀값 회전일 (12-4에서 대장으로 읽는다)
 *                          api_key 칸은 12-2 시작 지점의 「고정 키」(범위 *, 만료 없음). 12-2 정답 코드는 이 칸을 읽지 않는다
 * daon_partner_credential  앱마다 비밀값(SHA-256만 저장). retire_at_ms가 지나면 거부(12-2 회전 겹침 기간)
 * daon_partner_order       어느 주문이 어느 파트너에게 배정됐나(배송 파트너 = 그 택배사 송장이 붙은 주문)
 * daon_partner_token       발급한 파트너 토큰의 jti·만료(12-4 일괄 폐기가 이 목록을 폐기 목록에 넣는다)
 * </pre>
 *
 * - 시드 파트너(모두 가짜): courier-01 「다온 배송 파트너」, analytics-01 「판매 분석 파트너」(내보내기 범위),
 *   legacy-01 「옛 정산 대행」(계약 끝, 120일 안 씀). 비밀값·고정 키는 랩 전용 가짜 값이고 stubs/partner·daon/partner.py가 같은 값을 쓴다.
 * - 시각 칸 중 랩 시계(LabClock)와 비교하는 것은 밀리초 숫자(_ms)로 둔다. 사람이 읽는 대장 칸은 DATETIME(DB 시계).
 * - 권한: 앱 DB 계정(6-3 동적 계정)에 이 네 표의 CREATE만 더 줬다(infra/openbao/init.sh). 표가 없으면 파트너 API만 동작하지 않는다.
 * - legacy-01의 마지막 사용 시각은 뜰 때마다 「120일 전」으로 다시 맞춘다(랩 시드 고정: 12-4 점검이 늘 같은 결과를 내게).
 */
@Component
public class DaonPartnerSchema implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger("DAON-LAB");

    /** id, 이름, 담당자, 범위, 마지막 사용(며칠 전), 비밀값 회전(며칠 전). 고정 키·비밀값은 seedKey()·seedSecret()이 만든다(랩 가짜 값) */
    static final String[][] APPS = {
            {"courier-01", "다온 배송 파트너(가짜)", "물류팀 김가짜(logistics@daon.example)", "orders:read:assigned", "1", "20"},
            {"analytics-01", "다온 판매 분석 파트너(가짜)", "마케팅팀 이가짜(marketing@daon.example)", "orders:read:assigned orders:export", "2", "30"},
            {"legacy-01", "옛 정산 대행(가짜, 계약 끝)", "재무팀 박가짜(finance@daon.example)", "orders:read:assigned", "120", "200"},
    };

    @Autowired
    private JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        try {
            createTables();
            seed();
        } catch (RuntimeException e) {
            LOG.warn("[12장] 파트너 표를 만들지 못했습니다: {} (DB 권한 확인: infra/openbao/init.sh)", e.getClass().getSimpleName());
        }
    }

    void createTables() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS daon_partner_app ("
                + " id VARCHAR(40) NOT NULL PRIMARY KEY,"
                + " name VARCHAR(100) NOT NULL,"
                + " owner VARCHAR(100) NULL COMMENT '담당자(사고 때 누구에게 묻나)',"
                + " scopes VARCHAR(200) NOT NULL COMMENT '공백으로 나눈 범위',"
                + " status VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT 'active·revoked',"
                + " api_key VARCHAR(80) NULL COMMENT '[12-2 시작] 고정 키(범위 *, 만료 없음)',"
                + " expires_at DATETIME NULL COMMENT '계약 만료',"
                + " last_used_at DATETIME NULL,"
                + " secret_rotated_at DATETIME NULL,"
                + " created_at DATETIME NOT NULL"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '다온마켓 12장 연동 앱 대장(교육용 추가)'");
        jdbc.execute("CREATE TABLE IF NOT EXISTS daon_partner_credential ("
                + " id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                + " app_id VARCHAR(40) NOT NULL,"
                + " secret_sha256 CHAR(64) NOT NULL COMMENT '비밀값 원문은 저장하지 않는다',"
                + " created_ms BIGINT NOT NULL,"
                + " retire_at_ms BIGINT NULL COMMENT '이 시각(랩 시계) 뒤로 거부. NULL이면 지금 쓰는 값',"
                + " KEY idx_partner_cred_app (app_id)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '다온마켓 12-2 파트너 비밀값(회전)'");
        jdbc.execute("CREATE TABLE IF NOT EXISTS daon_partner_order ("
                + " order_id BIGINT NOT NULL PRIMARY KEY,"
                + " app_id VARCHAR(40) NOT NULL,"
                + " KEY idx_partner_order_app (app_id)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '다온마켓 12-2 주문 배정(누구의 데이터인가)'");
        jdbc.execute("CREATE TABLE IF NOT EXISTS daon_partner_token ("
                + " jti VARCHAR(40) NOT NULL PRIMARY KEY,"
                + " app_id VARCHAR(40) NOT NULL,"
                + " scopes VARCHAR(200) NOT NULL,"
                + " cnf VARCHAR(64) NULL COMMENT '12-3 묶인 인증서 지문(x5t#S256)',"
                + " issued_ms BIGINT NOT NULL,"
                + " expires_ms BIGINT NOT NULL,"
                + " KEY idx_partner_token_app (app_id, expires_ms)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '다온마켓 12장 발급한 파트너 토큰(값은 저장하지 않음)'");
    }

    void seed() {
        long now = System.currentTimeMillis();
        for (String[] a : APPS) {
            jdbc.update("INSERT IGNORE INTO daon_partner_app (id, name, owner, scopes, status, api_key, expires_at, last_used_at, secret_rotated_at, created_at)"
                            + " VALUES (?, ?, ?, ?, 'active', ?, NULL, NOW() - INTERVAL ? DAY, NOW() - INTERVAL ? DAY, NOW() - INTERVAL 400 DAY)",
                    a[0], a[1], a[2], a[3], seedKey(a[0]), Integer.parseInt(a[4]), Integer.parseInt(a[5]));
            Integer creds = jdbc.queryForObject("SELECT COUNT(*) FROM daon_partner_credential WHERE app_id = ?", Integer.class, a[0]);
            if (creds == null || creds == 0) {
                jdbc.update("INSERT INTO daon_partner_credential (app_id, secret_sha256, created_ms) VALUES (?, ?, ?)",
                        a[0], sha256Hex(seedSecret(a[0])), now);
            }
        }
        // 계약이 끝난 legacy-01: 랩 시드 고정(12-4 점검 「90일 넘게 안 쓴 앱」)
        jdbc.update("UPDATE daon_partner_app SET last_used_at = NOW() - INTERVAL 120 DAY, secret_rotated_at = NOW() - INTERVAL 200 DAY,"
                + " expires_at = NOW() - INTERVAL 30 DAY WHERE id = 'legacy-01'");
        // 배정: 배송 파트너는 송장이 붙은 주문 중 일부(시드 기준 81건), 분석 파트너는 자기 제휴 채널 주문(시드 기준 21건)
        jdbc.update("INSERT IGNORE INTO daon_partner_order (order_id, app_id) SELECT id, 'courier-01' FROM oms_order"
                + " WHERE id BETWEEN 9004 AND 9300 AND delivery_sn IS NOT NULL AND MOD(id, 3) = 0");
        jdbc.update("INSERT IGNORE INTO daon_partner_order (order_id, app_id) SELECT id, 'analytics-01' FROM oms_order"
                + " WHERE id BETWEEN 9004 AND 9300 AND MOD(id, 10) = 1");
    }

    /** [12-2 시작] 랩 전용 가짜 고정 키(stubs/partner/partner_app.py와 같은 값) */
    static String seedKey(String appId) {
        return "lab-fake-partner-key-" + appId;
    }

    /** 랩 전용 가짜 비밀값(stubs/partner/partner_app.py·daon/partner.py와 같은 값) */
    static String seedSecret(String appId) {
        return "lab-fake-partner-secret-" + appId + "-v1";
    }

    static String sha256Hex(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
