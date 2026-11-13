package com.macro.mall.portal.partner;

import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.lab.LabClock;
import com.macro.mall.security.securekit.PartnerClientCert;
import com.macro.mall.security.securekit.PartnerScopeInterceptor;
import com.macro.mall.security.securekit.SecureKitSwitch;
import com.macro.mall.security.token.JwtSigner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * [다온마켓 12-2 정답] 파트너 토큰 발급(클라이언트 자격 증명)과 비밀값 회전. 파트너 입구(A9)로만 받는다(PartnerScopeInterceptor ①).
 *
 * <pre>
 * POST /partner/token              grant_type=client_credentials&client_id=courier-01&client_secret=…[&scope=orders:export]
 *   → {"access_token":"…","token_type":"Bearer","expires_in":900,"scope":"orders:read:assigned"}
 * POST /partner/credentials/rotate client_id=…&client_secret=(지금 값)
 *   → {"client_secret":"(새 값, 한 번만 보임)","overlap_seconds":600}
 * </pre>
 *
 * - 토큰: 서명은 5-2의 키 체계(OpenBao transit, kid), 대상 aud=daon-partner. 내용 partner_id·scope·jti·exp(15분). 갱신 토큰은 주지 않는다
 *   (파트너는 비밀값으로 다시 받는다: 짧은 수명이 곧 회전이다).
 * - 범위: 요청한 범위(없으면 orders:read:assigned 하나)가 대장(daon_partner_app.scopes) 안에 있어야 한다. 내보내기(orders:export)는
 *   기본 발급에서 빠진다. 꼭 필요한 파트너만 따로 청한다.
 * - 회전: 앱마다 비밀값 둘(현재·다음)까지. 새 값을 만들면 옛 값은 겹침 기간(랩 10분) 뒤에 거부된다(파트너가 새 값으로 바꿀 시간).
 *   회전 중에 또 회전하면 409. 비밀값은 SHA-256만 저장한다(사람이 고른 비밀번호가 아니라 32바이트 무작위 값이라 느린 해시가 필요 없다).
 * - 시각은 랩 시계(LabClock): 시험이 16분·11분을 건너뛰어 만료·겹침 끝을 본다.
 * - ✏️ 12-3: 발급 요청도 파트너 입구의 클라이언트 인증서(랩 내부 CA, 30일)를 내야 한다. 인증서 주인(CN)이 client_id와 같아야 하고,
 *   그 인증서 지문을 토큰의 cnf 클레임 {"x5t#S256": 지문}에 넣는다(RFC 8705). 토큰은 짧게(15분), 인증서는 길게(30일).
 *   [랩 스위치] token-binding을 끄면 인증서 없이 발급하고 cnf를 넣지 않는다(무기명 토큰).
 * - ✏️ 12-4: 폐기한 앱(status revoked)에는 발급하지 않는다(partner.denied.revoked). 발급에 성공하면 대장의 마지막 사용 시각을 갱신한다
 *   (make partner-audit가 「90일 넘게 안 쓴 앱」을 이 칸으로 찾는다). [랩 스위치] partner-revoke를 끄면 상태를 보지 않는다.
 */
@RestController
@Tag(name = "DaonPartnerTokenController", description = "[다온마켓 12-2] 파트너 토큰 발급·비밀값 회전")
@RequestMapping("/partner")
public class DaonPartnerTokenController {
    public static final long TOKEN_SECONDS = 900;          // 15분
    public static final long OVERLAP_SECONDS = 600;        // 회전 겹침 10분(랩 값)
    static final String DEFAULT_SCOPE = "orders:read:assigned";
    private static final Pattern CLIENT_ID = Pattern.compile("[a-z0-9-]{3,40}");
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JwtSigner signer;

    @Operation(summary = "파트너 토큰 발급(클라이언트 자격 증명)")
    @PostMapping("/token")
    public ResponseEntity<Map<String, Object>> token(HttpServletRequest request,
                                                     @RequestParam(value = "grant_type", required = false) String grantType,
                                                     @RequestParam(value = "client_id", required = false) String clientId,
                                                     @RequestParam(value = "client_secret", required = false) String clientSecret,
                                                     @RequestParam(value = "scope", required = false) String scope) {
        if (!"client_credentials".equals(grantType)) {
            return oauthError(HttpStatus.BAD_REQUEST, "unsupported_grant_type");
        }
        Map<String, Object> app = app(clientId);
        if (app != null && SecureKitSwitch.on("partner-revoke") && "revoked".equals(app.get("status"))) {
            SecurityEventLogger.denied("partner.denied.revoked", "12-4").request(request)
                    .actor("partner:" + clientId).target("path:/partner/token").reason("폐기한 앱이 토큰 발급을 요청함").log();
            return oauthError(HttpStatus.UNAUTHORIZED, "invalid_client");
        }
        if (app == null || !secretOk(clientId, clientSecret)) {
            badSecret(request, app == null ? null : clientId);
            return oauthError(HttpStatus.UNAUTHORIZED, "invalid_client");
        }
        Set<String> allowed = split((String) app.get("scopes"));
        Set<String> wanted = split(scope == null || scope.isBlank() ? DEFAULT_SCOPE : scope);
        if (wanted.isEmpty() || !allowed.containsAll(wanted)) {
            return oauthError(HttpStatus.BAD_REQUEST, "invalid_scope");
        }
        String granted = String.join(" ", wanted);
        String cnf = null;
        if (SecureKitSwitch.on("token-binding")) {
            PartnerClientCert.Info cert = PartnerClientCert.of(request);
            if (cert == null || !clientId.equals(cert.commonName())) {
                SecurityEventLogger.denied("token.denied.binding_mismatch", "12-3").request(request)
                        .actor("partner:" + clientId).target("path:/partner/token")
                        .reason(cert == null ? "발급 요청에 확인된 클라이언트 인증서 없음" : "인증서 주인(CN)과 client_id가 다름").log();
                return oauthError(HttpStatus.UNAUTHORIZED, "invalid_client");
            }
            cnf = cert.thumbprint();
        }
        long now = LabClock.nowSeconds();
        String jti = UUID.randomUUID().toString();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", "partner:" + clientId);
        claims.put("partner_id", clientId);
        claims.put("scope", granted);
        claims.put("jti", jti);
        claims.put("iat", now);
        claims.put("exp", now + TOKEN_SECONDS);
        if (cnf != null) {
            claims.put("cnf", Map.of("x5t#S256", cnf));
        }
        String token = signer.sign(claims, PartnerScopeInterceptor.AUDIENCE);
        jdbc.update("INSERT INTO daon_partner_token (jti, app_id, scopes, cnf, issued_ms, expires_ms) VALUES (?, ?, ?, ?, ?, ?)",
                jti, clientId, granted, cnf, now * 1000, (now + TOKEN_SECONDS) * 1000);
        jdbc.update("UPDATE daon_partner_app SET last_used_at = NOW() WHERE id = ?", clientId);   // 대장: 마지막 사용
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", token);
        body.put("token_type", "Bearer");
        body.put("expires_in", TOKEN_SECONDS);
        body.put("scope", granted);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(body);
    }

    @Operation(summary = "파트너 비밀값 회전(새 값 발급, 옛 값은 겹침 기간 뒤 거부)")
    @PostMapping("/credentials/rotate")
    public ResponseEntity<Map<String, Object>> rotate(HttpServletRequest request,
                                                      @RequestParam(value = "client_id", required = false) String clientId,
                                                      @RequestParam(value = "client_secret", required = false) String clientSecret) {
        Map<String, Object> app = app(clientId);
        if (app == null || !secretOk(clientId, clientSecret)) {
            badSecret(request, app == null ? null : clientId);
            return oauthError(HttpStatus.UNAUTHORIZED, "invalid_client");
        }
        long nowMs = LabClock.nowMillis();
        Integer pending = jdbc.queryForObject("SELECT COUNT(*) FROM daon_partner_credential WHERE app_id = ? AND retire_at_ms IS NOT NULL AND retire_at_ms > ?",
                Integer.class, clientId, nowMs);
        if (pending != null && pending > 0) {
            return oauthError(HttpStatus.CONFLICT, "rotation_in_progress");   // 비밀값은 둘(현재·다음)까지
        }
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String fresh = "lab-partner-secret-" + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        long retireAt = nowMs + OVERLAP_SECONDS * 1000;
        jdbc.update("UPDATE daon_partner_credential SET retire_at_ms = ? WHERE app_id = ? AND retire_at_ms IS NULL", retireAt, clientId);
        jdbc.update("INSERT INTO daon_partner_credential (app_id, secret_sha256, created_ms) VALUES (?, ?, ?)", clientId, sha256Hex(fresh), nowMs);
        jdbc.update("UPDATE daon_partner_app SET secret_rotated_at = NOW() WHERE id = ?", clientId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("client_secret", fresh);
        body.put("overlap_seconds", OVERLAP_SECONDS);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(body);
    }

    /** 대장의 앱 한 줄(id·scopes·status). 형식이 이상한 client_id는 찾지 않는다 */
    Map<String, Object> app(String clientId) {
        if (clientId == null || !CLIENT_ID.matcher(clientId).matches()) {
            return null;
        }
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, scopes, status FROM daon_partner_app WHERE id = ?", clientId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 지금 쓸 수 있는 비밀값 가운데 하나와 같나(겹침 기간 안의 옛 값 포함). 비교는 상수 시간 */
    boolean secretOk(String clientId, String secret) {
        if (secret == null || secret.isEmpty()) {
            return false;
        }
        byte[] given = sha256Hex(secret).getBytes(StandardCharsets.US_ASCII);
        long nowMs = LabClock.nowMillis();
        boolean ok = false;
        for (Map<String, Object> row : jdbc.queryForList("SELECT secret_sha256, retire_at_ms FROM daon_partner_credential WHERE app_id = ?", clientId)) {
            Object retire = row.get("retire_at_ms");
            boolean usable = retire == null || ((Number) retire).longValue() > nowMs;
            boolean same = MessageDigest.isEqual(given, String.valueOf(row.get("secret_sha256")).getBytes(StandardCharsets.US_ASCII));
            ok |= usable && same;
        }
        return ok;
    }

    /** 틀린 비밀값. 없는 client_id는 받은 값을 로그에 넣지 않는다(아무 글자나 로그로 흘려보내지 않게) */
    static void badSecret(HttpServletRequest request, String knownClientId) {
        String who = knownClientId == null ? "partner:unknown" : "partner:" + knownClientId;
        SecurityEventLogger.denied("partner.denied.bad_credential", "12-2").request(request)
                .actor(who).target(who).reason("파트너 비밀값이 틀림(또는 없는 파트너)").log();
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

    static String sha256Hex(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static ResponseEntity<Map<String, Object>> oauthError(HttpStatus status, String code) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        return ResponseEntity.status(status).header("Cache-Control", "no-store").body(body);
    }
}
