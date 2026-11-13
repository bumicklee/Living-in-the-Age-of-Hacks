package com.macro.mall.security.token;

import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.lab.LabClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

/**
 * [다온마켓 4-8 정답] 토큰 폐기 목록과 갱신 토큰 회전. Redis 키(A3 권한: deny:* · rt:*)
 *
 * <pre>
 * deny:&lt;jti&gt;            로그아웃한 액세스 토큰. 남은 수명만큼만 둔다(영원히 두면 Redis가 커진다)
 * rt:&lt;sha256&gt;          갱신 토큰(원문은 저장하지 않는다) → "아이디|계열|auth_time"  14일
 * rt:used:&lt;sha256&gt;     이미 회전된 갱신 토큰 표시 → 계열                     14일
 * rt:fam:&lt;계열&gt;        계열에 속한 갱신 토큰 해시 모음(재사용이면 통째로 지운다)
 * deny:sub:&lt;대상&gt;:&lt;아이디&gt;  [14-4] 이 시각(초) 이전에 로그인해 받은 토큰은 모두 폐기(사고 대응 격리). 대상 = 토큰 aud(daon-portal·daon-admin)
 * </pre>
 *
 * 14-4: 사람 하나의 토큰을 「모두」 끊는 방법. jti 하나씩(deny:)은 지금 쓰는 토큰만 안다. 그래서 계정마다 끊은 시각을 두고,
 *   토큰의 auth_time(비밀번호를 넣은 시각, 갱신해도 그대로 옮긴다)이 그 시각 이전이면 액세스 토큰도 갱신 토큰도 받지 않는다.
 *   잠금을 풀어도 끊기 전에 받은 토큰은 다시 살아나지 않는다(훔쳐 간 토큰이 그대로 죽어 있다).
 *
 * - 갱신 토큰은 쓰는 순간 지우고 새 값을 준다. 지운 값이 다시 오면 탈취로 보고 그 계열을 모두 끊는다(auth.refresh.reuse_detected).
 * - isRevoked는 Redis가 응답하지 않으면 true(막는다).
 */
@Component
public class TokenService {
    private static final Logger LOG = LoggerFactory.getLogger(TokenService.class);
    public static final Duration REFRESH_TTL = Duration.ofDays(14);
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private StringRedisTemplate redis;
    @Value("${daon.jwt.audience:daon}")
    private String audience;

    /** 회전 결과: 누구의 토큰인지와 새 갱신 토큰 */
    public record Rotated(String username, String refreshToken, long authTime) {
    }

    /** 로그인 성공 때: 새 계열의 첫 갱신 토큰. authTime은 그 로그인 토큰의 auth_time(4-7, 갱신해도 그대로 옮긴다) */
    public String issueRefresh(String username, long authTime) {
        return issue(username, UUID.randomUUID().toString(), authTime);
    }

    /** 갱신 토큰을 쓰면 지우고 같은 계열의 새 값을 준다. 없거나 재사용이면 null */
    public Rotated rotate(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return null;
        }
        String hash = sha256(refreshToken);
        String value = redis.opsForValue().getAndDelete("rt:" + hash);
        if (value == null) {
            String family = redis.opsForValue().get("rt:used:" + hash);
            if (family != null) {
                killFamily(family);
                SecurityEventLogger.denied("auth.refresh.reuse_detected", "4-8")
                        .target("family:" + family.substring(0, 8))
                        .reason("이미 회전된 갱신 토큰 재사용, 계열 전체 폐기")
                        .log();
            }
            return null;
        }
        String[] parts = value.split("\\|", 3);
        long authTime = parts.length > 2 ? Long.parseLong(parts[2]) : 0L;
        if (cutOff(parts[0], authTime)) {                 // 14-4: 격리 전에 시작한 계열은 더 이어 주지 않는다
            killFamily(parts[1]);
            return null;
        }
        redis.opsForValue().set("rt:used:" + hash, parts[1], REFRESH_TTL);
        redis.opsForSet().remove("rt:fam:" + parts[1], hash);
        return new Rotated(parts[0], issue(parts[0], parts[1], authTime), authTime);
    }

    /** 로그아웃: 액세스 토큰은 남은 수명 동안 폐기 목록에, 갱신 토큰은 계열째 끊는다 */
    public void logout(String jti, long remainingSeconds, String refreshToken) {
        if (jti != null && remainingSeconds > 0) {
            redis.opsForValue().set("deny:" + jti, "1", Duration.ofSeconds(remainingSeconds));
        }
        if (refreshToken != null && !refreshToken.isBlank()) {
            String value = redis.opsForValue().get("rt:" + sha256(refreshToken));
            if (value != null) {
                killFamily(value.split("\\|", 3)[1]);
            }
        }
    }

    /** 인증 필터가 부른다. Redis가 응답하지 않으면 막는다 */
    public boolean isRevoked(String jti) {
        try {
            return Boolean.TRUE.equals(redis.hasKey("deny:" + jti));
        } catch (RuntimeException e) {
            LOG.warn("폐기 목록 확인 실패, 막습니다: {}", e.getMessage());
            return true;
        }
    }

    /** 14-4 사고 대응: 이 대상(aud)의 이 계정이 지금까지 받은 토큰을 모두 끊는다(지금 이전 auth_time). 남은 수명 동안만 둔다 */
    public void revokeAllOf(String targetAudience, String username) {
        redis.opsForValue().set("deny:sub:" + targetAudience + ":" + username, String.valueOf(LabClock.nowSeconds()), REFRESH_TTL);
    }

    /** 이 앱(aud)의 토큰 주인이 격리로 끊겼고, 이 토큰이 그 전에 로그인해 받은 것인가. Redis가 응답하지 않으면 막는다 */
    public boolean cutOff(String username, long authTime) {
        try {
            String v = redis.opsForValue().get("deny:sub:" + audience + ":" + username);
            return v != null && authTime <= Long.parseLong(v);
        } catch (RuntimeException e) {
            LOG.warn("격리 목록 확인 실패, 막습니다: {}", e.getMessage());
            return true;
        }
    }

    private String issue(String username, String family, long authTime) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        String hash = sha256(token);
        redis.opsForValue().set("rt:" + hash, username + "|" + family + "|" + authTime, REFRESH_TTL);
        redis.opsForSet().add("rt:fam:" + family, hash);
        redis.expire("rt:fam:" + family, REFRESH_TTL);
        return token;
    }

    private void killFamily(String family) {
        Set<String> hashes = redis.opsForSet().members("rt:fam:" + family);
        if (hashes != null) {
            for (String h : hashes) {
                redis.delete("rt:" + h);
            }
        }
        redis.delete("rt:fam:" + family);
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
