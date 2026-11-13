package com.macro.mall.portal.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * [랩 도구] 10장 돈 기능 표 맞추기(시작 지점 모습).
 *
 * 수업 이동(make goto)은 파일만 바꾸고 DB는 그대로 둔다. 10-2를 마친 뒤 10-1이나 그 앞으로 돌아오면
 * 10-2가 건 「쿠폰 1인 1장」 유니크 제약(uk_daon_coupon_member)이 DB에 남아 재연이 레드로 나오지 않는다.
 * 그래서 시작 지점의 이 파일은 그 제약이 있으면 지운다. 10-2 정답이 이 파일을 「제약을 거는」 모습으로 바꾼다.
 * 고치는 코드가 아니라 랩 상태를 수업 지점에 맞추는 도구다. DB 권한이 없으면(6-3 이전) 아무것도 하지 않는다.
 */
@Component
public class DaonMoneySchema implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger("DAON-LAB");
    static final String COUPON_UNIQUE = "uk_daon_coupon_member";

    @Autowired
    private JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (hasIndex("sms_coupon_history", COUPON_UNIQUE)) {
                jdbc.execute("ALTER TABLE sms_coupon_history DROP INDEX " + COUPON_UNIQUE);
                LOG.info("[랩 도구] 10-2 이전 지점이라 쿠폰 1인 유니크 제약({})을 지웠습니다", COUPON_UNIQUE);
            }
        } catch (RuntimeException e) {
            LOG.warn("[랩 도구] 쿠폰 유니크 제약을 확인하지 못했습니다: {}", e.getClass().getSimpleName());
        }
    }

    private boolean hasIndex(String table, String index) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?", Integer.class, table, index);
        return n != null && n > 0;
    }
}
