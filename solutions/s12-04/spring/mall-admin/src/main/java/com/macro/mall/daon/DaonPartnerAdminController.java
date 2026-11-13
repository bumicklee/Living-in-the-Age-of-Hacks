package com.macro.mall.daon;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.lab.LabClock;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * [다온마켓 12-4 정답] 연동 앱 대장과 앱 단위 일괄 폐기. 권한표에는 관리자 계정 관리(/admin/**)와 같은 권한으로 올린다(MallSecurityConfig).
 *
 * <pre>
 * GET  /daon/partner/apps                  대장: 이름·담당자·범위·상태·계약 만료·마지막 사용(며칠 전)·비밀값 회전(며칠 전)·살아 있는 비밀값·토큰 수
 * POST /daon/partner/apps/{id}/scopes      {"scopes":"orders:read:assigned orders:export"}  범위가 넓어지면 partner.app.changed(WARN)
 * POST /daon/partner/apps/{id}/revoke      앱 단위 일괄 폐기: 상태 revoked · 비밀값 둘 다 지금 거부 · 발급한 토큰 jti를 폐기 목록(Redis deny:)에
 * </pre>
 *
 * - 비밀값·토큰 값은 대장에 없다(해시·jti만 있고, 응답에도 넣지 않는다).
 * - 「새 앱 등록·범위 확대」는 한 번이라도 보안 담당에게 알린다(Midnight Blizzard: 고권한 앱 생성이 신호였다). 줄이는 변경은 알리지 않는다.
 * - 일괄 폐기는 런북(docs/runbooks/partner.export.bulk.md)의 첫 조치다. 비밀값만 지우면 이미 발급한 토큰이 수명 동안 계속 쓰이므로 토큰도 끊는다.
 * - 시각은 랩 시계(LabClock, 파트너 토큰 발급과 같은 시계). 폐기 목록은 토큰 남은 수명 + 20분만 둔다.
 */
@RestController
@Tag(name = "DaonPartnerAdminController", description = "[다온마켓 12-4] 연동 앱 대장·일괄 폐기")
@RequestMapping("/daon/partner")
public class DaonPartnerAdminController {
    /** 파트너 API가 아는 범위(새 범위를 만들면 여기에도 더한다) */
    static final Set<String> KNOWN_SCOPES = Set.of("orders:read:assigned", "orders:export");
    private static final Pattern APP_ID = Pattern.compile("[a-z0-9-]{3,40}");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private StringRedisTemplate redis;

    @Operation(summary = "연동 앱 대장")
    @GetMapping("/apps")
    public CommonResult<List<Map<String, Object>>> apps() {
        long nowMs = LabClock.nowMillis();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT a.id, a.name, a.owner, a.scopes, a.status, a.expires_at AS expiresAt, a.last_used_at AS lastUsedAt,"
                        + " TIMESTAMPDIFF(DAY, COALESCE(a.last_used_at, a.created_at), NOW()) AS idleDays,"
                        + " a.secret_rotated_at AS secretRotatedAt,"
                        + " TIMESTAMPDIFF(DAY, COALESCE(a.secret_rotated_at, a.created_at), NOW()) AS secretAgeDays,"
                        + " (a.expires_at IS NOT NULL AND a.expires_at < NOW()) AS contractEnded,"
                        + " (SELECT COUNT(*) FROM daon_partner_credential c WHERE c.app_id = a.id AND (c.retire_at_ms IS NULL OR c.retire_at_ms > ?)) AS activeSecrets,"
                        + " (SELECT COUNT(*) FROM daon_partner_token t WHERE t.app_id = a.id AND t.expires_ms > ?) AS activeTokens"
                        + " FROM daon_partner_app a ORDER BY a.id", nowMs, nowMs);
        return CommonResult.success(rows);
    }

    @Operation(summary = "연동 앱 범위 바꾸기(넓어지면 보안 이벤트)")
    @PostMapping("/apps/{id}/scopes")
    public CommonResult<Map<String, Object>> scopes(@PathVariable("id") String id, @RequestBody Map<String, String> body) {
        Map<String, Object> app = app(id);
        if (app == null) {
            return CommonResult.failed("없는 연동 앱입니다");
        }
        Set<String> want = split(body == null ? null : body.get("scopes"));
        if (want.isEmpty() || !KNOWN_SCOPES.containsAll(want)) {
            return CommonResult.validateFailed("모르는 범위가 있습니다(" + String.join(", ", KNOWN_SCOPES) + "만)");
        }
        Set<String> before = split((String) app.get("scopes"));
        Set<String> added = new LinkedHashSet<>(want);
        added.removeAll(before);
        jdbc.update("UPDATE daon_partner_app SET scopes = ? WHERE id = ?", String.join(" ", want), id);
        if (!added.isEmpty()) {
            SecurityEventLogger.denied("partner.app.changed", "12-4").actor(admin()).target("partner:" + id)
                    .reason("연동 앱 범위 확대: +" + String.join(" +", added)).log();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("scopes", String.join(" ", want));
        out.put("added", added);
        return CommonResult.success(out);
    }

    @Operation(summary = "연동 앱 일괄 폐기(비밀값·발급 토큰 모두)")
    @Transactional
    @PostMapping("/apps/{id}/revoke")
    public CommonResult<Map<String, Object>> revoke(@PathVariable("id") String id) {
        if (app(id) == null) {
            return CommonResult.failed("없는 연동 앱입니다");
        }
        long nowMs = LabClock.nowMillis();
        jdbc.update("UPDATE daon_partner_app SET status = 'revoked' WHERE id = ?", id);
        int secrets = jdbc.update("UPDATE daon_partner_credential SET retire_at_ms = ? WHERE app_id = ? AND (retire_at_ms IS NULL OR retire_at_ms > ?)",
                nowMs, id, nowMs);
        List<Map<String, Object>> live = jdbc.queryForList("SELECT jti, expires_ms FROM daon_partner_token WHERE app_id = ? AND expires_ms > ?", id, nowMs);
        for (Map<String, Object> t : live) {
            long left = ((Number) t.get("expires_ms")).longValue() - nowMs;
            redis.opsForValue().set("deny:" + t.get("jti"), "1", Duration.ofMillis(left).plusMinutes(20));
        }
        SecurityEventLogger.denied("partner.app.changed", "12-4").actor(admin()).target("partner:" + id)
                .reason("연동 앱 일괄 폐기(발급 토큰 " + live.size() + "개·비밀값 " + secrets + "개)").log();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("revokedTokens", live.size());
        out.put("retiredSecrets", secrets);
        return CommonResult.success(out);
    }

    private Map<String, Object> app(String id) {
        if (id == null || !APP_ID.matcher(id).matches()) {
            return null;
        }
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, scopes, status FROM daon_partner_app WHERE id = ?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static String admin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return "admin:" + (auth == null ? "unknown" : auth.getName());
    }

    static Set<String> split(String s) {
        Set<String> out = new LinkedHashSet<>();
        if (s != null) {
            for (String part : s.split(" ")) {
                if (!part.isBlank()) {
                    out.add(part.strip());
                }
            }
        }
        return out;
    }
}
