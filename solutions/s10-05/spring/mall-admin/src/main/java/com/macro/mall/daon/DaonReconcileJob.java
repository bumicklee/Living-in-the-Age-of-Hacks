package com.macro.mall.daon;

import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.securekit.SecureKitSwitch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * [다온마켓 10-5 정답] 원장 대사: 숫자가 맞는지 매일 확인한다(매일 04:00 + 관리자 수동 실행 POST /daon/reconcile/run).
 *
 * 대사 1 · 포인트: 회원 잔액(ums_member.integration)과 원장(ums_integration_change_history) 합계(더함 − 뺌)가 같은가.
 * 대사 2 · 결제: 다온마켓이 만든 결제 완료 주문(id &gt; 9300, status 1·2·3)마다 결제사 승인 기록(oms_payment_event, 10-4)이
 *         같은 금액으로 있는가. 고객이 직접 부른 「결제 성공」(F02)으로 완료된 주문은 여기서 걸린다.
 *
 * - 기준 시각(cutoff)을 하나 정하고 그 시각까지만 센다. 대사가 도는 동안 들어온 주문 때문에 어긋나지 않게.
 * - 결과는 oms_reconcile_result에 남긴다(불일치 회원·주문 ID만, 금액은 넣지 않는다).
 * - 불일치 하나에 ledger.mismatch 한 줄. 50줄이 넘으면 나머지는 요약 한 줄(count에 전체 수).
 * - 대사는 사후 확인이다. 실시간 차단은 10-2(조건부 UPDATE)·10-4(웹훅 검사)가 맡는다. 불일치를 찾으면 덮어쓰지 말고 원인을 먼저 찾는다.
 * - 원본 데모 회원(id 100 이하)은 원장이 처음부터 없어 대사에서 뺀다(다온 시드 회원 101~만).
 */
@Component
public class DaonReconcileJob {
    private static final Logger LOG = LoggerFactory.getLogger(DaonReconcileJob.class);
    static final int EVENT_CAP = 50;
    static final long FIRST_APP_ORDER = 9300;           // 시드 주문은 9300까지. 앱이 만든 주문은 9301부터(seed/README.md)

    @Autowired
    private JdbcTemplate jdbc;

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void daily() {
        run("system:reconcile");
    }

    /** 결과: {cutoff, members: [불일치 회원 ID], orders: [불일치 주문 ID]} */
    public synchronized Map<String, Object> run(String actor) {
        Map<String, Object> out = new LinkedHashMap<>();
        LocalDateTime cutoff = LocalDateTime.now().withNano(0);
        out.put("cutoff", cutoff.toString());
        if (!SecureKitSwitch.on("reconcile")) {          // [랩 스위치] 끄면 대사가 아무것도 알리지 않는다
            out.put("members", List.of());
            out.put("orders", List.of());
            out.put("note", "랩 스위치 reconcile 꺼짐");
            return out;
        }
        ensureTable();
        List<Long> members = jdbc.queryForList(
                "SELECT m.id FROM ums_member m "
                        + "LEFT JOIN ums_integration_change_history h ON h.member_id = m.id AND h.create_time <= ? "
                        + "WHERE m.id > 100 "
                        + "GROUP BY m.id, m.integration "
                        + "HAVING IFNULL(m.integration, 0) <> IFNULL(SUM(CASE h.change_type WHEN 0 THEN h.change_count ELSE -h.change_count END), 0) "
                        + "ORDER BY m.id", Long.class, Timestamp.valueOf(cutoff));
        List<Long> orders = new ArrayList<>();
        if (tableExists("oms_payment_event")) {
            orders = jdbc.queryForList(
                    "SELECT o.id FROM oms_order o WHERE o.id > ? AND o.status IN (1, 2, 3) AND o.payment_time <= ? "
                            + "AND NOT EXISTS (SELECT 1 FROM oms_payment_event e WHERE e.order_sn = o.order_sn "
                            + "AND e.event = 'payment.approved' AND e.result = 'applied' AND e.amount = o.pay_amount) "
                            + "ORDER BY o.id", Long.class, FIRST_APP_ORDER, Timestamp.valueOf(cutoff));
        }
        out.put("members", members);
        out.put("orders", orders);
        Timestamp runAt = Timestamp.valueOf(cutoff);
        int logged = 0;
        for (Long id : members) {
            jdbc.update("INSERT INTO oms_reconcile_result (run_at, kind, target) VALUES (?, 'points', ?)", runAt, "member:" + id);
            if (logged++ < EVENT_CAP) {
                SecurityEventLogger.denied("ledger.mismatch", "10-5").actor(actor).target("member:" + id)
                        .reason("포인트와 원장 합계 불일치").log();
            }
        }
        for (Long id : orders) {
            jdbc.update("INSERT INTO oms_reconcile_result (run_at, kind, target) VALUES (?, 'payment', ?)", runAt, "order:" + id);
            if (logged++ < EVENT_CAP) {
                SecurityEventLogger.denied("ledger.mismatch", "10-5").actor(actor).target("order:" + id)
                        .reason("결제 완료 주문에 결제사 승인 기록이 없거나 금액이 다름").log();
            }
        }
        if (logged > EVENT_CAP) {
            SecurityEventLogger.denied("ledger.mismatch", "10-5").actor(actor).target("reconcile:summary")
                    .reason("불일치가 많아 " + EVENT_CAP + "줄 뒤는 요약(전체 수는 count)").count(members.size() + orders.size()).log();
        }
        LOG.info("대사 끝(기준 {}): 포인트 불일치 {}명, 결제 불일치 {}건", cutoff, members.size(), orders.size());
        return out;
    }

    private void ensureTable() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS oms_reconcile_result ("
                + " id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                + " run_at DATETIME NOT NULL COMMENT '대사 기준 시각',"
                + " kind VARCHAR(20) NOT NULL COMMENT 'points·payment',"
                + " target VARCHAR(40) NOT NULL COMMENT 'member:<ID>·order:<ID>(금액은 넣지 않는다)',"
                + " KEY idx_reconcile_run (run_at)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '다온마켓 10-5 대사 불일치 목록'");
    }

    private boolean tableExists(String table) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class, table);
        return n != null && n > 0;
    }
}
