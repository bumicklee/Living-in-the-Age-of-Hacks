package com.macro.mall.security.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * [다온마켓 5-4 정답] ums_member.phone_hmac(검색용 칸)을 만들고, 번호로 회원을 찾는다.
 *
 * - 칸과 색인은 PiiReencrypt(랩 전용 이전 도구)가 한 번 만든다. ensureSchema()는 몇 번 불러도 같다.
 * - 옛 색인 idx_phone(평문 전화번호 중복 막기)은 암호문에서 뜻이 없어서, 검색용 칸의 유일 색인으로 옮긴다.
 * - 회원을 저장한 뒤 indexByUsername으로 검색 칸을 채운다(저장 매퍼는 번호를 암호문으로 넣기 때문에 검색 값은 따로 만든다).
 */
@Component
public class PhoneIndex {
    private static final Logger LOG = LoggerFactory.getLogger(PhoneIndex.class);

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PhoneHmac hmac;

    public void ensureSchema() {
        Integer col = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'ums_member' AND column_name = 'phone_hmac'", Integer.class);
        if (col == null || col == 0) {
            jdbc.execute("ALTER TABLE ums_member ADD COLUMN phone_hmac VARCHAR(80) NULL COMMENT '전화번호 검색용 HMAC(키 버전 포함)'");
        }
        Integer oldIdx = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() AND table_name = 'ums_member' AND index_name = 'idx_phone'", Integer.class);
        if (oldIdx != null && oldIdx > 0) {
            jdbc.execute("ALTER TABLE ums_member DROP INDEX idx_phone");
        }
        Integer newIdx = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() AND table_name = 'ums_member' AND index_name = 'idx_phone_hmac'", Integer.class);
        if (newIdx == null || newIdx == 0) {
            jdbc.execute("ALTER TABLE ums_member ADD UNIQUE INDEX idx_phone_hmac (phone_hmac)");
        }
    }

    /** 번호로 회원 번호(id)를 찾는다. 없으면 빈 값 */
    public Optional<Long> findMemberId(String phone) {
        String h = hmac.of(phone);
        if (h == null) {
            return Optional.empty();
        }
        List<Long> ids = jdbc.queryForList("SELECT id FROM ums_member WHERE phone_hmac = ? LIMIT 1", Long.class, h);
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
    }

    /** 회원을 저장한 직후 검색 칸을 채운다 */
    public void indexByUsername(String username, String phone) {
        String h = hmac.of(phone);
        if (h != null) {
            jdbc.update("UPDATE ums_member SET phone_hmac = ? WHERE username = ?", h, username);
        }
    }

    /** 검색 칸이 빈 행을 채운다. 보관 칸(암호문)을 풀어 번호를 얻는다. 채운 행 수를 돌려준다 */
    public int backfill(FieldCrypto crypto) {
        int n = 0;
        while (true) {
            List<java.util.Map<String, Object>> rows = jdbc.queryForList("SELECT id, phone FROM ums_member "
                    + "WHERE phone_hmac IS NULL AND phone IS NOT NULL AND phone <> '' LIMIT 500");
            if (rows.isEmpty()) {
                break;
            }
            int done = 0;
            for (java.util.Map<String, Object> r : rows) {
                String plain = crypto.decrypt(String.valueOf(r.get("phone")), "ums_member.phone");
                String h = plain == null ? null : hmac.of(plain);
                if (h != null) {
                    jdbc.update("UPDATE ums_member SET phone_hmac = ? WHERE id = ?", h, r.get("id"));
                    done++;
                } else {
                    jdbc.update("UPDATE ums_member SET phone_hmac = ? WHERE id = ?", "v0:unindexed-" + r.get("id"), r.get("id"));   // 풀 수 없는 행은 건너뛰고 표시만(무한 반복 방지)
                }
            }
            n += done;
            if (done == 0 && rows.size() < 500) {
                break;
            }
        }
        if (n > 0) {
            LOG.info("[A4] 전화번호 검색 칸 채움: ums_member.phone_hmac {}행", n);
        }
        return n;
    }
}
