package com.macro.mall.daon;

import com.macro.mall.service.UmsAdminCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * [13장 시작 모습] 직원 계정과 역할(앱이 뜰 때 멱등. 시드 SQL은 새 볼륨에서만 들어가서 앱이 맞춘다, 12장 파트너 시드와 같은 방식).
 *
 * [교육용 추가] 역할 101 재무 담당(고액 환불 두 번째 승인, 13-4) · 102 운영 책임(권한 상승 승인, 13-6)
 *              직원 staff_finance(재무 담당) · staff_lead(운영 책임). 비밀번호는 다른 직원과 같은 가짜 값(daon-pass-1!)
 * [회사 설정]  공용 운영 계정 daon_ops(슈퍼 관리자, 팀 위키에 비밀번호가 적혀 있다는 설정, 가짜 값 lab-fake-ops-1!) — T35
 * [원본 기본값] 원본 데모 관리자 8개(admin·macro·test …, F19)는 시드 그대로 열려 있다
 *
 * 시작 모습은 이 계정들을 「열어 둔다」. 13-6 정답이 닫은 뒤 수업 이동으로 되돌아오면 다시 연다(수업 이동이 DB 상태도 맞추게).
 * ✏️ 13-6에서 공용·데모 계정을 닫는다(지우지 않는다: 그 이름으로 들어오는 시도가 신호가 된다).
 */
@Component
public class DaonStaffSeed {
    private static final Logger LOG = LoggerFactory.getLogger("DAON-LAB");
    /** daon-pass-1! (seed/spring/20-daon-accounts.sql의 직원과 같은 가짜 해시) */
    static final String STAFF_HASH = "$2a$10$u0wE8GEVqNE2AP6u02Lrw.MzzCBfWTdG51t.vJLpjX0hwkbwj7izu";
    /** lab-fake-ops-1! (공용 운영 계정의 가짜 비밀번호, bcrypt cost 10) */
    static final String OPS_HASH = "$2a$10$L/UHOs5mOGkXqdv8H4D0re6H410YOkJsg9d07lENTCSyz0hSB2ZoC";
    /** 원본 데모 관리자(mall.sql). admin은 랩 도구·시험이 쓰는 계정이라 13-6에서도 닫지 않는다(회사라면 닫는다) */
    static final List<String> DEMO = List.of("test", "admin", "macro", "productAdmin", "orderAdmin", "test123", "test256", "test1267");
    static final String SHARED = "daon_ops";

    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected UmsAdminCacheService cache;

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        try {
            ensureAccounts();
            apply();
        } catch (RuntimeException e) {
            LOG.warn("[13장] 직원 계정을 맞추지 못했습니다: {}", e.getMessage());
        }
    }

    protected void ensureAccounts() {
        jdbc.update("INSERT IGNORE INTO ums_role (id, name, description, admin_count, create_time, status, sort) VALUES"
                + " (101, '재무 담당', '고액 환불 두 번째 승인(13-4). 교육용 가짜 역할', 0, NOW(), 1, 0),"
                + " (102, '운영 책임', '운영 권한 상승 승인(13-6). 교육용 가짜 역할', 0, NOW(), 1, 0)");
        // 두 역할 모두 로그인 정보·로그아웃(31·32), 재무 담당은 반품 신청 보기(9)
        for (long[] rr : new long[][]{{101, 31}, {101, 32}, {101, 9}, {102, 31}, {102, 32}}) {
            jdbc.update("INSERT INTO ums_role_resource_relation (role_id, resource_id) SELECT ?, ? FROM DUAL"
                    + " WHERE NOT EXISTS (SELECT 1 FROM ums_role_resource_relation WHERE role_id = ? AND resource_id = ?)", rr[0], rr[1], rr[0], rr[1]);
        }
        jdbc.update("INSERT IGNORE INTO ums_admin (id, username, password, nick_name, email, note, create_time, status) VALUES"
                        + " (103, 'staff_finance', ?, '재무 담당', 'finance@daon.example', '다온마켓 재무 담당 직원(가짜)', NOW(), 1),"
                        + " (104, 'staff_lead', ?, '운영 책임', 'lead@daon.example', '다온마켓 운영 책임 직원(가짜)', NOW(), 1),"
                        + " (105, 'daon_ops', ?, '운영 공용', 'ops@daon.example', '[회사 설정] 운영팀 공용 계정. 비밀번호는 팀 위키에', NOW(), 1)",
                STAFF_HASH, STAFF_HASH, OPS_HASH);
        for (long[] ar : new long[][]{{103, 101}, {104, 102}, {105, 5}}) {
            jdbc.update("INSERT INTO ums_admin_role_relation (admin_id, role_id) SELECT ?, ? FROM DUAL"
                    + " WHERE NOT EXISTS (SELECT 1 FROM ums_admin_role_relation WHERE admin_id = ? AND role_id = ?)", ar[0], ar[1], ar[0], ar[1]);
        }
    }

    /** 시작 모습: 공용·데모 계정을 연다 */
    protected void apply() {
        setStatus(SHARED, 1);
        DEMO.forEach(u -> setStatus(u, 1));
    }

    protected void setStatus(String username, int status) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM ums_admin WHERE username = ? AND (status IS NULL OR status <> ?)", Long.class, username, status);
        for (Long id : ids) {
            jdbc.update("UPDATE ums_admin SET status = ? WHERE id = ?", status, id);
            cache.delAdmin(id);                      // 관리자 정보 캐시(Redis)에 옛 상태가 남지 않게
        }
    }
}
