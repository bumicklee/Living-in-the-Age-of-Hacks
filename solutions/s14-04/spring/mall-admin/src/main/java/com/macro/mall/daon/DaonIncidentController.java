package com.macro.mall.daon;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.mapper.UmsMemberMapper;
import com.macro.mall.model.UmsMember;
import com.macro.mall.security.audit.AuditLog;
import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.token.TokenService;
import com.macro.mall.security.trace.TraceId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * [다온마켓 14-4 정답] 사고 대응 격리: 한 회원 계정을 잠그고 그 계정이 가진 토큰을 모두 끊는다. 첫 한 시간 런북의 「격리」.
 *
 * POST /daon/incident/lock-actor     {"memberId":117,"incidentId":"INC-14-4","reason":"모의 침해 대응(가짜)"}
 * POST /daon/incident/unlock-actor   {"memberId":117,"incidentId":"INC-14-4","reason":"조사 끝, 본인 확인 완료"}
 *
 * lock-actor가 하는 일(순서대로)
 *   ① 회원 상태 0(잠금) → 새 로그인 거부(고객 앱 login이 같은 문구로 거부 + auth.denied.disabled_account)
 *   ② 고객 앱의 회원 캐시(Redis mall:ums:member:&lt;아이디&gt;)를 지운다 → 지금 쓰는 토큰도 다음 요청부터 401(잠긴 계정)
 *   ③ 토큰 일괄 폐기(4-8 폐기 목록의 계정 단위, deny:sub:daon-portal:&lt;아이디&gt;) → 잠금을 풀어도 그 전 토큰·갱신 토큰은 죽어 있다
 *   ④ incident.actor.locked(INFO) · 감사 로그 incident.lock-actor(사유·사고 번호). 대응 행동도 타임라인의 한 줄이 된다
 * 고위험 기능이다: 권한표(관리자 계정 관리와 같은 권한) + 13-6 권한 상승(묶음 incident-response). 사유·사고 번호 필수.
 * 응답에는 회원 ID와 결과만(이름·전화 등 개인정보 없음). /ops/**(로그인 없는 운영 도구 8095)가 아니라 관리자 API에 둔다.
 */
@RestController
@Tag(name = "DaonIncidentController", description = "[다온마켓 14-4] 사고 대응 격리")
@RequestMapping("/daon/incident")
public class DaonIncidentController {
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    static final String PORTAL_AUDIENCE = "daon-portal";
    static final String MEMBER_CACHE = "mall:ums:member:";

    @Autowired
    private UmsMemberMapper memberMapper;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private StringRedisTemplate redis;

    @Operation(summary = "회원 격리: 잠금 + 토큰 일괄 폐기(권한 상승·사유 필수)")
    @PostMapping("/lock-actor")
    public ResponseEntity<CommonResult<Map<String, Object>>> lockActor(@RequestBody Map<String, Object> body) {
        return change(body, true);
    }

    @Operation(summary = "격리 풀기: 잠금만 푼다(그 전 토큰은 계속 폐기)")
    @PostMapping("/unlock-actor")
    public ResponseEntity<CommonResult<Map<String, Object>>> unlockActor(@RequestBody Map<String, Object> body) {
        return change(body, false);
    }

    private ResponseEntity<CommonResult<Map<String, Object>>> change(Map<String, Object> body, boolean lock) {
        Long memberId;
        try {
            memberId = Long.valueOf(String.valueOf(body.get("memberId")));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(CommonResult.validateFailed("memberId(숫자)가 필요합니다"));
        }
        String incident = body.get("incidentId") == null ? "" : String.valueOf(body.get("incidentId")).trim();
        String reason = body.get("reason") == null ? "" : String.valueOf(body.get("reason")).trim();
        if (!incident.matches("[A-Za-z0-9-]{3,40}") || reason.isEmpty() || reason.length() > 100) {
            return ResponseEntity.badRequest().body(CommonResult.validateFailed("사고 번호(영문·숫자·하이픈)와 사유(100자까지)가 필요합니다"));
        }
        UmsMember member = memberMapper.selectByPrimaryKey(memberId);
        if (member == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResult.failed("회원을 찾을 수 없습니다"));
        }
        String who = SecurityContextHolder.getContext().getAuthentication().getName();
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
            SecurityEventLogger.suspicious("incident.actor.locked", "14-4")
                    .actor("admin:" + who)
                    .target("member:" + memberId)
                    .reason("사고 대응 격리(사고 번호 " + incident + ")")
                    .log();
        } else {
            out.put("tokens", "그대로 폐기(잠금 전 토큰은 살아나지 않는다)");
        }
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("ts", OffsetDateTime.now(ZONE).toString());
        e.put("trace_id", TraceId.current());
        e.put("actor", "admin:" + who);
        e.put("action", lock ? "incident.lock-actor" : "incident.unlock-actor");
        e.put("target", "member:" + memberId);
        e.put("count", 1);
        e.put("reason", incident + " " + reason);
        e.put("result", "ok");
        AuditLog.write(e);
        return ResponseEntity.ok(CommonResult.success(out));
    }
}
