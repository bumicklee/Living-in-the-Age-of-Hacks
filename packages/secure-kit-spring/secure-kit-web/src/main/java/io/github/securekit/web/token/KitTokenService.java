package io.github.securekit.web.token;

import io.github.securekit.core.SecureKitException;
import io.github.securekit.core.event.SecurityEvents;
import io.github.securekit.core.store.CounterStore;
import io.github.securekit.core.token.HmacJwt;
import io.github.securekit.web.KitPrincipal;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 토큰 수명·폐기(4-8·5-2). 설정 secure-kit.token(secret·kid·ttl·audience).
 *
 * - 발급: HS256 + kid, 내용 sub·aud·iat·auth_time·exp·jti·roles. 수명은 최대 15분(900초)으로 묶는다.
 * - 확인: alg는 HS256만, exp 필수, 폐기 목록(jti) 확인. 실패 이유는 이벤트에만 쓰고 응답은 같은 401.
 * - 로그아웃: jti를 남은 수명 동안 폐기 목록에 둔다(저장소가 Redis면 여러 대가 함께 본다).
 */
public class KitTokenService {
    public static final long MAX_TTL_SECONDS = 900;
    public static final String PARTNER_AUDIENCE = "partner";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final HmacJwt jwt;
    private final CounterStore store;
    private final long ttlSeconds;
    private final String audience;

    public KitTokenService(HmacJwt jwt, CounterStore store, Duration ttl, String audience) {
        this.jwt = jwt;
        this.store = store;
        this.ttlSeconds = Math.min(ttl.getSeconds(), MAX_TTL_SECONDS);
        this.audience = audience;
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }

    /** 사람 로그인 토큰 */
    public String issue(String sub, List<String> roles) {
        Map<String, Object> c = base(sub, audience);
        c.put("roles", new ArrayList<>(roles));
        return jwt.sign(c);
    }

    /** 파트너(외부 앱) 토큰: aud=partner, sub=앱, scope=공백 구분 */
    public String issuePartner(String app, List<String> scopes) {
        Map<String, Object> c = base(app, PARTNER_AUDIENCE);
        c.put("scope", String.join(" ", scopes));
        return jwt.sign(c);
    }

    private Map<String, Object> base(String sub, String aud) {
        long now = System.currentTimeMillis() / 1000;
        byte[] id = new byte[12];
        RANDOM.nextBytes(id);
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("sub", sub);
        c.put("aud", aud);
        c.put("iat", now);
        c.put("auth_time", now);
        c.put("exp", now + ttlSeconds);
        c.put("jti", HexFormat.of().formatHex(id));
        return c;
    }

    /** 확인. 실패하면 이벤트 한 줄 + SecureKitException(401) */
    public KitPrincipal verify(String token) {
        Map<String, Object> c;
        try {
            c = jwt.verify(token, System.currentTimeMillis() / 1000);
        } catch (HmacJwt.Invalid e) {
            throw denied(e.failure());
        }
        Object aud = c.get("aud");
        if (!audience.equals(aud) && !PARTNER_AUDIENCE.equals(aud)) {
            SecurityEvents.warn("auth.token.wrong_audience", "token", "aud:" + aud, "받지 않는 aud");
            throw unauthorized();
        }
        String jti = String.valueOf(c.get("jti"));
        if (store.get(revokedKey(jti)) != null) {
            SecurityEvents.warn("auth.token.revoked_used", "token", "jti:" + head(jti), "로그아웃(폐기)한 토큰을 다시 썼다");
            throw unauthorized();
        }
        return new KitPrincipal(String.valueOf(c.get("sub")), roles(c.get("roles")), String.valueOf(aud),
                scopes(c.get("scope")), jti, ((Number) c.get("exp")).longValue());
    }

    /** 로그아웃: 남은 수명 동안 폐기 목록에 둔다 */
    public void revoke(KitPrincipal p) {
        long left = Math.max(1, p.exp() - System.currentTimeMillis() / 1000);
        store.put(revokedKey(p.jti()), "1", Duration.ofSeconds(left));
        SecurityEvents.info("auth.token.revoked", "token", "jti:" + head(p.jti()), "로그아웃");
    }

    private static SecureKitException denied(HmacJwt.Failure f) {
        switch (f) {
            case DENIED_ALG:
                SecurityEvents.warn("auth.token.denied_alg", "token", "header:alg", "HS256이 아닌 alg(none 등)");
                break;
            case EXPIRED:
                SecurityEvents.info("auth.token.expired", "token", "-", "만료된 토큰");
                break;
            default:
                SecurityEvents.warn("auth.token.invalid", "token", "-", "토큰 확인 실패: " + f.name().toLowerCase());
        }
        return unauthorized();
    }

    private static SecureKitException unauthorized() {
        return new SecureKitException(401, "로그인이 필요합니다");
    }

    private static String revokedKey(String jti) {
        return "token:revoked:" + jti;
    }

    private static String head(String jti) {
        return jti.length() > 8 ? jti.substring(0, 8) : jti;
    }

    private static List<String> roles(Object v) {
        List<String> out = new ArrayList<>();
        if (v instanceof List<?> l) for (Object o : l) out.add(String.valueOf(o));
        return out;
    }

    private static Set<String> scopes(Object v) {
        if (!(v instanceof String s) || s.isBlank()) return Set.of();
        return new HashSet<>(Arrays.asList(s.trim().split("\\s+")));
    }
}
