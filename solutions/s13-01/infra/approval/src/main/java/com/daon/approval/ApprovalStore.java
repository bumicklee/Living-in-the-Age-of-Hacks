package com.daon.approval;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * [A10] 승인 기록. 이 서비스만 쓰는 표 두 개(뜰 때 없으면 만든다).
 *
 * daon_approval_request  요청 하나 = 한 줄. kind refund(13-3·13-4)·ops-elevation(13-6)
 *   상태: APPROVED(바로 승인) · PENDING_SECOND(두 번째 승인 대기) · SCHEDULED(지연 실행 창) · READY(토큰 받을 수 있음)
 *         · HELD(서킷 브레이커가 멈춤) · EXECUTED(실행기가 끝냈다고 알림) · CANCELLED · DENIED
 * daon_approval_breaker  출금(환불) 서킷 브레이커 상태 한 줄(13-4)
 *
 * 금액은 이 표에만 있다(보안 이벤트에는 넣지 않는다). 남은 결제 금액은 이 표의 「잡아 둔 금액」으로 계산한다.
 */
@Component
public class ApprovalStore implements ApplicationRunner {
    /** 남은 결제 금액에서 빼는 상태(실행됐거나 실행될 수 있는 승인) */
    public static final String RESERVED = "'APPROVED','PENDING_SECOND','SCHEDULED','READY','HELD','EXECUTED'";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;

    public ApprovalStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS daon_approval_request ("
                + " id VARCHAR(40) NOT NULL PRIMARY KEY,"
                + " kind VARCHAR(20) NOT NULL,"
                + " return_id BIGINT NULL, order_id BIGINT NULL, order_sn VARCHAR(64) NULL,"
                + " amount DECIMAL(12,2) NULL,"
                + " target VARCHAR(64) NULL, minutes INT NULL, reason VARCHAR(200) NULL,"
                + " requested_by VARCHAR(64) NOT NULL, approved_by VARCHAR(64) NULL,"
                + " status VARCHAR(20) NOT NULL, nonce CHAR(32) NOT NULL,"
                + " confirm_hash CHAR(64) NULL, confirm_expires DATETIME NULL, confirm_tries INT NOT NULL DEFAULT 0,"
                + " execute_after DATETIME NULL,"
                + " created_at DATETIME NOT NULL, decided_at DATETIME NULL, token_at DATETIME NULL, executed_at DATETIME NULL,"
                + " INDEX idx_approval_order (order_id), INDEX idx_approval_status (status)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        jdbc.execute("CREATE TABLE IF NOT EXISTS daon_approval_breaker ("
                + " id TINYINT NOT NULL PRIMARY KEY, state VARCHAR(10) NOT NULL, opened_at DATETIME NULL,"
                + " reason VARCHAR(120) NULL, close_vote VARCHAR(64) NULL, close_vote_at DATETIME NULL"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        jdbc.update("INSERT IGNORE INTO daon_approval_breaker (id, state) VALUES (1, 'CLOSED')");
    }

    public static String newId() {
        byte[] b = new byte[10];
        RANDOM.nextBytes(b);
        return "ap" + HexFormat.of().formatHex(b);
    }

    public static String nonce() {
        byte[] b = new byte[16];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    /** 반품 신청과 그 주문(승인 서비스가 DB에서 직접 읽는다). 없으면 null */
    public Map<String, Object> returnWithOrder(long returnId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT r.id AS return_id, r.order_id, r.status AS return_status, o.order_sn, o.pay_amount, o.status AS order_status"
                        + " FROM oms_order_return_apply r JOIN oms_order o ON o.id = r.order_id WHERE r.id = ?", returnId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void insertRefund(String id, Map<String, Object> ret, BigDecimal amount, String requestedBy, String approvedBy, String status) {
        jdbc.update("INSERT INTO daon_approval_request (id, kind, return_id, order_id, order_sn, amount, requested_by, approved_by, status, nonce, created_at, decided_at)"
                        + " VALUES (?, 'refund', ?, ?, ?, ?, ?, ?, ?, ?, NOW(), IF(? IS NULL, NULL, NOW()))",
                id, ret.get("return_id"), ret.get("order_id"), ret.get("order_sn"), amount, requestedBy, approvedBy, status, nonce(), approvedBy);
    }

    public void insertDenied(String id, Map<String, Object> ret, long returnId, BigDecimal amount, String requestedBy, String reason) {
        jdbc.update("INSERT INTO daon_approval_request (id, kind, return_id, order_id, order_sn, amount, reason, requested_by, status, nonce, created_at, decided_at)"
                        + " VALUES (?, 'refund', ?, ?, ?, ?, ?, ?, 'DENIED', ?, NOW(), NOW())",
                id, returnId, ret == null ? null : ret.get("order_id"), ret == null ? null : ret.get("order_sn"), amount, reason, requestedBy, nonce());
    }

    public Map<String, Object> find(String id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM daon_approval_request WHERE id = ?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 상태를 바꾼다. from 상태일 때만(조건부 UPDATE, 10-4의 단방향 상태 전이). 바뀐 줄 수 */
    public int move(String id, String from, String to) {
        return jdbc.update("UPDATE daon_approval_request SET status = ?, decided_at = COALESCE(decided_at, NOW()) WHERE id = ? AND status = ?", to, id, from);
    }

    public void tokenIssued(String id) {
        jdbc.update("UPDATE daon_approval_request SET token_at = COALESCE(token_at, NOW()) WHERE id = ?", id);
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }
}
