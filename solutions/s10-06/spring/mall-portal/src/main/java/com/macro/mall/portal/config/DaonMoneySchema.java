package com.macro.mall.portal.config;

import com.macro.mall.security.securekit.SecureKitSwitch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * [다온마켓 10-2·10-4 정답] 돈 기능 표의 DB 제약(앱이 뜰 때 한 번, 멱등). 「마이그레이션 파일 하나」 자리다.
 *
 * - 쿠폰 1인 1장: sms_coupon_history(coupon_id, member_id) 유니크 제약. 두 요청이 동시에 와도 DB가 두 번째 INSERT를 거절한다.
 *   시드 쿠폰은 모두 1인 한도 1장이다. 1인 한도가 2장 이상인 쿠폰은 이 제약으로 못 막는다(회원·쿠폰별 카운터 행을 조건부 UPDATE, 그림으로만).
 * - 이미 중복 행이 있으면(10-1 재연 뒤) 제약을 걸 수 없다. 앱은 그대로 뜨고 경고만 남긴다 → make wipe 뒤 다시 띄운다.
 * - [랩 스위치] money-race를 끄면 제약을 지운다(영상의 「끄면 레드」). 시작 지점 파일은 늘 지운다(수업 이동 되돌리기).
 * - 10-4: 결제 웹훅 기록 표 oms_payment_event. (payment_id, event) 유니크가 웹훅 멱등의 바탕이다. 수업 이동으로 돌아가도 지우지 않는다(쓰는 코드가 없을 뿐).
 * - 10-6: 한정 상품 1인 구매 누적 표 daon_purchase_counter.
 * - 권한: 앱 DB 계정(6-3 동적 계정)에 이 표의 ALTER·INDEX만 더 줬다(infra/openbao/init.sh).
 */
@Component
public class DaonMoneySchema implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger("DAON-LAB");
    static final String COUPON_UNIQUE = "uk_daon_coupon_member";

    @Autowired
    private JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        ensurePaymentEvent();
        ensurePurchaseCounter();
        try {
            boolean has = hasIndex("sms_coupon_history", COUPON_UNIQUE);
            if (SecureKitSwitch.on("money-race")) {
                if (!has) {
                    Integer dup = jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT coupon_id, member_id FROM sms_coupon_history "
                            + "GROUP BY coupon_id, member_id HAVING COUNT(*) > 1) d", Integer.class);
                    if (dup != null && dup > 0) {
                        LOG.warn("[10-2] 같은 회원·쿠폰 이력이 {}쌍 겹쳐 있어 유니크 제약을 걸 수 없습니다. make wipe 뒤 다시 띄우세요", dup);
                    } else {
                        jdbc.execute("ALTER TABLE sms_coupon_history ADD UNIQUE KEY " + COUPON_UNIQUE + " (coupon_id, member_id)");
                        LOG.info("[10-2] 쿠폰 1인 유니크 제약({})을 걸었습니다", COUPON_UNIQUE);
                    }
                }
            } else if (has) {
                jdbc.execute("ALTER TABLE sms_coupon_history DROP INDEX " + COUPON_UNIQUE);
                LOG.warn("[랩 스위치] money-race 꺼짐: 쿠폰 1인 유니크 제약을 지웠습니다");
            }
        } catch (RuntimeException e) {
            LOG.warn("[10-2] 쿠폰 유니크 제약을 맞추지 못했습니다: {} (DB 권한 확인: infra/openbao/init.sh)", e.getClass().getSimpleName());
        }
    }

    /** 10-4: 표 하나 만들기(있으면 그대로) */
    void ensurePaymentEvent() {
        try {
            jdbc.execute("CREATE TABLE IF NOT EXISTS oms_payment_event ("
                    + " id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                    + " payment_id VARCHAR(40) NOT NULL COMMENT '결제사 결제 번호',"
                    + " event VARCHAR(40) NOT NULL COMMENT '웹훅 이벤트 이름',"
                    + " order_sn VARCHAR(64) NOT NULL,"
                    + " amount DECIMAL(10,2) NULL COMMENT '결제사가 승인한 금액',"
                    + " result VARCHAR(20) NOT NULL COMMENT 'received·applied·amount_mismatch·bad_transition·no_order·ignored',"
                    + " received_at DATETIME NOT NULL,"
                    + " UNIQUE KEY uk_payment_event (payment_id, event),"
                    + " KEY idx_payment_event_order (order_sn)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '다온마켓 10-4 결제 웹훅 기록(멱등)'");
        } catch (RuntimeException e) {
            LOG.warn("[10-4] oms_payment_event 표를 만들지 못했습니다: {} (DB 권한 확인: infra/openbao/init.sh)", e.getClass().getSimpleName());
        }
    }

    /** 10-6: 한정 상품 1인 구매 누적 수량(회원 × 상품 한 행) */
    void ensurePurchaseCounter() {
        try {
            jdbc.execute("CREATE TABLE IF NOT EXISTS daon_purchase_counter ("
                    + " member_id BIGINT NOT NULL,"
                    + " product_id BIGINT NOT NULL,"
                    + " qty INT NOT NULL DEFAULT 0,"
                    + " PRIMARY KEY (member_id, product_id)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '다온마켓 10-6 한정 상품 1인 구매 누적'");
        } catch (RuntimeException e) {
            LOG.warn("[10-6] daon_purchase_counter 표를 만들지 못했습니다: {}", e.getClass().getSimpleName());
        }
    }

    boolean hasIndex(String table, String index) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?", Integer.class, table, index);
        return n != null && n > 0;
    }
}
