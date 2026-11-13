package com.macro.mall.daon;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.mapper.UmsAdminMapper;
import com.macro.mall.mapper.UmsMemberMapper;
import com.macro.mall.model.UmsAdmin;
import com.macro.mall.model.UmsAdminExample;
import com.macro.mall.model.UmsMember;
import com.macro.mall.security.audit.AuditLog;
import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.token.TokenService;
import com.macro.mall.security.trace.TraceId;
import com.macro.mall.service.UmsAdminCacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * [다온마켓 14-4 + 15-2 정답] 사고 대응: 격리 · 자격증명 교체 · 골라 복구. 첫 한 시간 런북과 캡스톤 대응 순서의 API.
 *
 * POST /daon/incident/lock-actor       {"memberId":117,…} 또는 {"admin":"staff_night",…}  + incidentId·reason
 * POST /daon/incident/unlock-actor     같은 모양. 잠금만 푼다(그 전 토큰은 계속 폐기)
 * POST /daon/incident/reset-password   {"admin":"staff_night","incidentId":"CAP-15","reason":"…"}  ✏️ 15-2
 * POST /daon/incident/restore-columns  {"incidentId":"CAP-15","reason":"…","rows":[{"orderId":9204,"values":{"receiver_name":"…",…}}]}  ✏️ 15-2
 *
 * ✏️ 15-2 직원 계정 격리(14-4는 회원만): 직원 상태 0 → 새 로그인 거부(13-6과 같은 문구 + auth.denied.disabled_account),
 *    관리자 앱 캐시를 지우고 관리자 토큰(aud daon-admin)을 계정 단위로 모두 끊는다. 자기 계정은 잠그지 못한다.
 * ✏️ 15-2 비밀번호 초기화: 새어 나간 비밀번호를 무효로 하는 것이 시크릿 정리(저장소에서 지우기)보다 먼저다(6-2 원칙).
 *    새 임시 비밀번호는 서버가 무작위로 만들고 응답에 한 번만 준다(랩 도구는 화면에 찍지 않고 .daon/에 둔다). 로그·감사에는 없다.
 * ✏️ 15-2 골라 복구: 전체 되돌리기가 아니라 감사 로그로 찾은 주문의 받는 사람 칸만, 사람이 확인한 행만 백업 값으로 쓴다.
 *    값은 백업에 있던 그대로(5-3 암호문 포함)라 이 API가 풀거나 다시 암호화하지 않는다. 허용한 칸 밖은 거절한다.
 *    되돌리는 쓰기도 감사 로그(incident.restore.columns, 주문마다 한 줄)와 incident.data.restored(INFO)로 남는다.
 * 모두 고위험 기능이다: 권한표 + 13-6 권한 상승(묶음 incident-response, OpsElevationGate의 /daon/incident/**). 사유·사고 번호 필수.
 */
@RestController
@Tag(name = "DaonIncidentController", description = "[다온마켓 14-4·15-2] 사고 대응 격리·자격증명 교체·골라 복구")
@RequestMapping("/daon/incident")
public class DaonIncidentController {
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    static final String PORTAL_AUDIENCE = "daon-portal";
    static final String ADMIN_AUDIENCE = "daon-admin";
    static final String MEMBER_CACHE = "mall:ums:member:";
    /** 골라 복구가 쓸 수 있는 칸(주문의 받는 사람 정보). 금액·상태 칸은 이 API로 되돌리지 않는다 */
    static final Set<String> RESTORABLE = Set.of("receiver_name", "receiver_phone", "receiver_post_code", "receiver_province",
            "receiver_city", "receiver_region", "receiver_detail_address");
    static final int MAX_ROWS = 100;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private UmsMemberMapper memberMapper;
    @Autowired
    private UmsAdminMapper adminMapper;
    @Autowired
    private UmsAdminCacheService adminCacheService;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbc;

    private record Ticket(String incident, String reason) {
    }

    @Operation(summary = "격리: 회원 또는 직원 잠금 + 토큰 일괄 폐기(권한 상승·사유 필수)")
    @PostMapping("/lock-actor")
    public ResponseEntity<CommonResult<Map<String, Object>>> lockActor(@RequestBody Map<String, Object> body) {
        return body.containsKey("admin") ? changeAdmin(body, true) : change(body, true);
    }

    @Operation(summary = "격리 풀기: 잠금만 푼다(그 전 토큰은 계속 폐기)")
    @PostMapping("/unlock-actor")
    public ResponseEntity<CommonResult<Map<String, Object>>> unlockActor(@RequestBody Map<String, Object> body) {
        return body.containsKey("admin") ? changeAdmin(body, false) : change(body, false);
    }

    @Operation(summary = "직원 비밀번호 초기화: 새 임시 비밀번호(응답에 한 번) + 토큰 일괄 폐기")
    @PostMapping("/reset-password")
    public ResponseEntity<CommonResult<Map<String, Object>>> resetPassword(@RequestBody Map<String, Object> body) {
        Ticket t = ticket(body);
        if (t == null) {
            return bad("사고 번호(영문·숫자·하이픈)와 사유(100자까지)가 필요합니다");
        }
        UmsAdmin admin = adminByName(String.valueOf(body.get("admin")));
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResult.failed("직원 계정을 찾을 수 없습니다"));
        }
        String temp = tempPassword();
        UmsAdmin change = new UmsAdmin();
        change.setId(admin.getId());
        change.setPassword(passwordEncoder.encode(temp));
        adminMapper.updateByPrimaryKeySelective(change);
        adminCacheService.delAdmin(admin.getId());
        tokenService.revokeAllOf(ADMIN_AUDIENCE, admin.getUsername());          // 바꾸기 전에 받은 토큰도 끊는다
        audit("incident.reset-password", "admin:" + admin.getUsername(), 1, t);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("admin", admin.getUsername());
        out.put("temporaryPassword", temp);                                   // 한 번만. 본인 확인 뒤 다른 경로로 전한다
        out.put("tokens", "revoked(지금 이전 로그인)");
        return ResponseEntity.ok(CommonResult.success(out));
    }

    @Operation(summary = "골라 복구: 확인한 주문의 받는 사람 칸만 백업 값으로(감사 로그에 남김)")
    @PostMapping("/restore-columns")
    @SuppressWarnings("unchecked")
    public ResponseEntity<CommonResult<Map<String, Object>>> restoreColumns(@RequestBody Map<String, Object> body) {
        Ticket t = ticket(body);
        if (t == null) {
            return bad("사고 번호(영문·숫자·하이픈)와 사유(100자까지)가 필요합니다");
        }
        if (!(body.get("rows") instanceof List<?> rows) || rows.isEmpty() || rows.size() > MAX_ROWS) {
            return bad("rows(1~" + MAX_ROWS + "개)가 필요합니다");
        }
        List<Long> done = new ArrayList<>();
        for (Object o : rows) {
            if (!(o instanceof Map<?, ?> row) || !(row.get("values") instanceof Map<?, ?> values) || values.isEmpty()) {
                return bad("행마다 orderId와 values가 필요합니다");
            }
            long orderId;
            try {
                orderId = Long.parseLong(String.valueOf(row.get("orderId")));
            } catch (NumberFormatException e) {
                return bad("orderId는 숫자입니다");
            }
            for (Object col : values.keySet()) {
                if (!RESTORABLE.contains(String.valueOf(col))) {
                    return bad("되돌릴 수 없는 칸입니다: " + col);           // 칸 이름은 허용 목록에서만(SQL에 그대로 넣기 전에)
                }
            }
            StringBuilder sql = new StringBuilder("UPDATE oms_order SET ");
            List<Object> args = new ArrayList<>();
            for (Map.Entry<?, ?> e : ((Map<Object, Object>) values).entrySet()) {
                sql.append(args.isEmpty() ? "" : ", ").append(e.getKey()).append(" = ?");
                args.add(e.getValue() == null ? null : String.valueOf(e.getValue()));
            }
            sql.append(", modify_time = NOW() WHERE id = ?");
            args.add(orderId);
            if (jdbc.update(sql.toString(), args.toArray()) == 1) {
                done.add(orderId);
                audit("incident.restore.columns", "order:" + orderId, 1, t);   // 칸 이름·값은 넣지 않는다
            }
        }
        SecurityEventLogger.suspicious("incident.data.restored", "15-2")
                .actor("admin:" + me())
                .target("incident:" + t.incident())
                .reason("골라 복구: 주문 " + done.size() + "건의 받는 사람 칸")
                .log();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("restored", done);
        out.put("count", done.size());
        return ResponseEntity.ok(CommonResult.success(out));
    }

    // ── 회원(14-4) ──
    private ResponseEntity<CommonResult<Map<String, Object>>> change(Map<String, Object> body, boolean lock) {
        Long memberId;
        try {
            memberId = Long.valueOf(String.valueOf(body.get("memberId")));
        } catch (NumberFormatException e) {
            return bad("memberId(숫자) 또는 admin(직원 아이디)이 필요합니다");
        }
        Ticket t = ticket(body);
        if (t == null) {
            return bad("사고 번호(영문·숫자·하이픈)와 사유(100자까지)가 필요합니다");
        }
        UmsMember member = memberMapper.selectByPrimaryKey(memberId);
        if (member == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResult.failed("회원을 찾을 수 없습니다"));
        }
        UmsMember change = new UmsMember();
        change.setId(memberId);
        change.setStatus(lock ? 0 : 1);
        memberMapper.updateByPrimaryKeySelective(change);                      // ① 새 로그인 거부(또는 다시 허용)
        redis.delete(MEMBER_CACHE + member.getUsername());                     // ② 고객 앱 회원 캐시 → 다음 요청부터 새 상태
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("memberId", memberId);
        out.put("status", lock ? "locked" : "unlocked");
        if (lock) {
            tokenService.revokeAllOf(PORTAL_AUDIENCE, member.getUsername());   // ③ 그 전에 받은 토큰·갱신 토큰 모두
            out.put("tokens", "revoked(지금 이전 로그인)");
            lockedEvent("member:" + memberId, t);
        } else {
            out.put("tokens", "그대로 폐기(잠금 전 토큰은 살아나지 않는다)");
        }
        audit(lock ? "incident.lock-actor" : "incident.unlock-actor", "member:" + memberId, 1, t);
        return ResponseEntity.ok(CommonResult.success(out));
    }

    // ── 직원(15-2) ──
    private ResponseEntity<CommonResult<Map<String, Object>>> changeAdmin(Map<String, Object> body, boolean lock) {
        Ticket t = ticket(body);
        if (t == null) {
            return bad("사고 번호(영문·숫자·하이픈)와 사유(100자까지)가 필요합니다");
        }
        UmsAdmin admin = adminByName(String.valueOf(body.get("admin")));
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResult.failed("직원 계정을 찾을 수 없습니다"));
        }
        if (admin.getUsername().equals(me())) {
            return bad("자기 계정은 잠글 수 없습니다(다른 대응자가 잠근다)");
        }
        UmsAdmin change = new UmsAdmin();
        change.setId(admin.getId());
        change.setStatus(lock ? 0 : 1);
        adminMapper.updateByPrimaryKeySelective(change);                       // ① 새 로그인 거부(13-6 닫힌 계정과 같은 문구)
        adminCacheService.delAdmin(admin.getId());                             // ② 관리자 앱 캐시 → 다음 요청부터 새 상태
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("admin", admin.getUsername());
        out.put("status", lock ? "locked" : "unlocked");
        if (lock) {
            tokenService.revokeAllOf(ADMIN_AUDIENCE, admin.getUsername());     // ③ 지금 쓰는 관리자 토큰·갱신 토큰 모두
            out.put("tokens", "revoked(지금 이전 로그인)");
            lockedEvent("admin:" + admin.getUsername(), t);
        } else {
            out.put("tokens", "그대로 폐기(잠금 전 토큰은 살아나지 않는다)");
        }
        audit(lock ? "incident.lock-actor" : "incident.unlock-actor", "admin:" + admin.getUsername(), 1, t);
        return ResponseEntity.ok(CommonResult.success(out));
    }

    // ── 공통 ──
    private static Ticket ticket(Map<String, Object> body) {
        String incident = body.get("incidentId") == null ? "" : String.valueOf(body.get("incidentId")).trim();
        String reason = body.get("reason") == null ? "" : String.valueOf(body.get("reason")).trim();
        if (!incident.matches("[A-Za-z0-9-]{3,40}") || reason.isEmpty() || reason.length() > 100) {
            return null;
        }
        return new Ticket(incident, reason);
    }

    private UmsAdmin adminByName(String username) {
        if (username == null || !username.matches("[A-Za-z0-9_.-]{1,64}")) {
            return null;
        }
        UmsAdminExample ex = new UmsAdminExample();
        ex.createCriteria().andUsernameEqualTo(username);
        List<UmsAdmin> found = adminMapper.selectByExample(ex);
        return found.isEmpty() ? null : found.get(0);
    }

    private static String me() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private static String tempPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder("tmp-");
        for (int i = 0; i < 16; i++) {
            sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return sb.append("!7").toString();
    }

    private static void lockedEvent(String target, Ticket t) {
        SecurityEventLogger.suspicious("incident.actor.locked", "14-4")
                .actor("admin:" + me())
                .target(target)
                .reason("사고 대응 격리(사고 번호 " + t.incident() + ")")
                .log();
    }

    private static void audit(String action, String target, int count, Ticket t) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("ts", OffsetDateTime.now(ZONE).toString());
        e.put("trace_id", TraceId.current());
        e.put("actor", "admin:" + me());
        e.put("action", action);
        e.put("target", target);
        e.put("count", count);
        e.put("reason", t.incident() + " " + t.reason());
        e.put("result", "ok");
        AuditLog.write(e);
    }

    private static ResponseEntity<CommonResult<Map<String, Object>>> bad(String message) {
        return ResponseEntity.badRequest().body(CommonResult.validateFailed(message));
    }
}
