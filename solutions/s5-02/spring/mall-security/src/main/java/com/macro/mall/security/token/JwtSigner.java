package com.macro.mall.security.token;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.openbao.OpenBaoClient;
import com.macro.mall.security.openbao.OpenBaoException;
import com.macro.mall.security.securekit.SecureKitSwitch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [다온마켓 5-2 정답] 토큰 서명은 금고(OpenBao transit 키 daon-jwt, ECDSA P-256)에 맡기고, 확인은 공개키로 앱 안에서 한다.
 *
 * <pre>
 * 서명: 헤더 {"alg":"ES256","typ":"JWT","kid":"daon-jwt:v2"} . 내용 . 서명  (서명은 OpenBao가 만든다. 키는 앱 밖으로 나오지 않는다)
 * 확인: 알고리즘은 ES256 하나만 받는다. 토큰 머리글이 무엇이라 하든 서버가 정한 방식으로만 확인한다.
 *       kid가 목록에 없거나 폐기된 버전이면 거절. iss·aud가 다르면 거절.
 * </pre>
 *
 * - 공개키 목록은 처음 쓸 때 한 번 받아 캐시한다. 검증마다 OpenBao를 부르지 않는다(금고가 멈춰도 이미 발급한 토큰은 확인된다).
 * - 모르는 kid가 올 때만 다시 받되 5초에 한 번까지(아무 kid나 보내서 금고를 두드리지 못하게).
 * - 금고가 응답하지 않으면 서명(로그인)은 실패한다(fail-closed). 로그에는 상태만 남기고 토큰·서명은 남기지 않는다.
 */
@Component
public class JwtSigner {
    private static final Logger LOG = LoggerFactory.getLogger(JwtSigner.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String KEY = "transit/keys/daon-jwt";
    private static final String SIGN = "transit/sign/daon-jwt";
    private static final String KID_PREFIX = "daon-jwt:v";
    public static final String ISSUER = "daon-market";
    private static final long REFRESH_MIN_GAP_MS = 5_000L;
    /** 공개키 목록을 이만큼 지나면 다시 받는다(회전·폐기가 앱에 반영되는 데 걸리는 시간의 상한. 랩은 10초, 운영은 몇 분) */
    private static final long KEYS_TTL_MS = 10_000L;

    @Autowired
    private OpenBaoClient bao;
    @Value("${daon.jwt.audience:daon-portal}")
    private String audience;

    private final Map<Integer, PublicKey> keys = new ConcurrentHashMap<>();
    private volatile int latest = 0;
    private volatile int minVersion = 1;
    private volatile long lastRefresh = 0;

    public String audience() {
        return audience;
    }

    /** 내용(claims)에 iss·aud를 더해 서명한 토큰을 만든다. 금고가 안 닿으면 예외(로그인을 막는다) */
    public String sign(Map<String, Object> claims) {
        return sign(claims, false);
    }

    private String sign(Map<String, Object> claims, boolean retried) {
        ensureKeys(false);
        Map<String, Object> payload = new LinkedHashMap<>(claims);
        payload.put("iss", ISSUER);
        payload.put("aud", audience);
        try {
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "ES256");
            header.put("typ", "JWT");
            header.put("kid", KID_PREFIX + latest);
            String signingInput = b64(JSON.writeValueAsBytes(header)) + "." + b64(JSON.writeValueAsBytes(payload));
            JsonNode res = bao.write(SIGN, Map.of(
                    "input", Base64.getEncoder().encodeToString(signingInput.getBytes(StandardCharsets.US_ASCII)),
                    "hash_algorithm", "sha2-256",
                    "marshaling_algorithm", "jws"));
            String sig = res.path("signature").asText("");       // vault:v2:<base64url(r||s)>
            String[] p = sig.split(":", 3);
            if (p.length != 3 || p[2].isEmpty()) {
                throw new OpenBaoException("서명 응답 형식이 다릅니다", 200);
            }
            int used = Integer.parseInt(p[1].substring(1));
            if (used != latest) {                                  // 회전 직후 앱이 옛 latest를 들고 있었다 → 키 목록을 새로 받고 한 번만 다시 서명
                if (retried) {
                    throw new OpenBaoException("서명 버전이 헤더와 맞지 않습니다", 200);
                }
                lastRefresh = 0;                                   // 방금 회전한 새 버전이니 간격 제한 없이 받는다
                ensureKeys(true);
                latest = used;
                return sign(claims, true);
            }
            return signingInput + "." + p[2];
        } catch (OpenBaoException e) {
            LOG.warn("[5-2] 서명을 금고에 맡기지 못했습니다(로그인을 막습니다): {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            throw new OpenBaoException("토큰 서명 실패", e);
        }
    }

    /**
     * 토큰을 확인하고 내용을 돌려준다. 거절이면 null. 서명·알고리즘·kid·iss·aud를 본다. 만료(exp)는 호출한 쪽이 본다.
     * 거절 이유별로 SECURITY 이벤트를 남긴다(토큰 원문·서명은 넣지 않는다).
     */
    public Map<String, Object> verify(String token) {
        try {
            String[] parts = token == null ? new String[0] : token.split("\\.", -1);
            if (parts.length != 3 || parts[2].isEmpty()) {
                deny("auth.token.denied_alg", "서명 칸 없음 또는 형식 오류", "-");
                return null;
            }
            Map<String, Object> header = JSON.readValue(Base64.getUrlDecoder().decode(parts[0]), new TypeReference<>() { });
            String alg = String.valueOf(header.get("alg"));
            if (!"ES256".equals(alg)) {
                deny("auth.token.denied_alg", "허용하지 않는 알고리즘", cut(alg));
                return null;
            }
            String kid = String.valueOf(header.get("kid"));
            Integer version = parseKid(kid);
            if (version == null) {
                deny("auth.token.denied_unknown_kid", "kid 형식 오류", cut(kid));
                return null;
            }
            ensureKeys(false);
            PublicKey pk = keys.get(version);
            if (pk == null) {
                ensureKeys(true);                                   // 모르는 kid: 1분에 한 번까지만 다시 받는다
                pk = keys.get(version);
            }
            if (version < minVersion) {
                suspicious("auth.token.denied_retired_key", "폐기한 키 버전", cut(kid));
                return null;
            }
            if (pk == null) {
                deny("auth.token.denied_unknown_kid", "목록에 없는 kid", cut(kid));
                return null;
            }
            Signature s = Signature.getInstance("SHA256withECDSAinP1363Format");
            s.initVerify(pk);
            s.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!s.verify(Base64.getUrlDecoder().decode(parts[2]))) {
                deny("auth.token.denied_alg", "서명이 맞지 않음", cut(kid));
                return null;
            }
            Map<String, Object> payload = JSON.readValue(Base64.getUrlDecoder().decode(parts[1]), new TypeReference<>() { });
            if (SecureKitSwitch.on("jwt-hardening")
                    && (!ISSUER.equals(payload.get("iss")) || !audience.equals(payload.get("aud")))) {
                deny("auth.token.denied_alg", "발급자 또는 대상(iss·aud)이 다름", cut(kid));
                return null;
            }
            return payload;
        } catch (Exception e) {
            deny("auth.token.denied_alg", "토큰 형식 오류", "-");
            return null;
        }
    }

    /** 공개키 목록(JWK 형식, kid와 공개키만). /.well-known/jwks.json이 쓴다. 폐기한 버전은 뺀다 */
    public Map<String, Object> jwks() {
        ensureKeys(false);
        java.util.List<Map<String, Object>> list = new java.util.ArrayList<>();
        new TreeMap<>(keys).forEach((v, k) -> {
            if (v >= minVersion && k instanceof java.security.interfaces.ECPublicKey ec) {
                Map<String, Object> jwk = new LinkedHashMap<>();
                jwk.put("kty", "EC");
                jwk.put("crv", "P-256");
                jwk.put("alg", "ES256");
                jwk.put("use", "sig");
                jwk.put("kid", KID_PREFIX + v);
                jwk.put("x", b64(fixed(ec.getW().getAffineX())));
                jwk.put("y", b64(fixed(ec.getW().getAffineY())));
                list.add(jwk);
            }
        });
        return Map.of("keys", list);
    }

    /** 키 목록을 금고에서 다시 읽는다. force가 아니면 비어 있을 때만. 실패해도 이미 받은 목록은 그대로 둔다 */
    private synchronized void ensureKeys(boolean force) {
        long now = System.currentTimeMillis();
        if (force ? now - lastRefresh < REFRESH_MIN_GAP_MS : !keys.isEmpty() && now - lastRefresh < KEYS_TTL_MS) {
            return;
        }
        lastRefresh = now;
        try {
            JsonNode k = bao.read(KEY);
            int min = k.path("min_decryption_version").asInt(1);
            int lat = k.path("latest_version").asInt(1);
            Map<Integer, PublicKey> fresh = new ConcurrentHashMap<>();
            k.path("keys").fields().forEachRemaining(e -> {
                String pem = e.getValue().path("public_key").asText("");
                if (!pem.isEmpty()) {
                    fresh.put(Integer.parseInt(e.getKey()), parsePem(pem));
                }
            });
            keys.clear();
            keys.putAll(fresh);
            minVersion = min;
            latest = lat;
        } catch (OpenBaoException e) {
            LOG.warn("[5-2] 공개키 목록을 받지 못했습니다(이미 받은 목록으로 확인을 이어갑니다): {}", e.getMessage());
            if (keys.isEmpty()) {
                throw e;
            }
        }
    }

    private static PublicKey parsePem(String pem) {
        try {
            String body = pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
            return KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(body)));
        } catch (Exception e) {
            throw new OpenBaoException("공개키를 읽지 못했습니다", e);
        }
    }

    private static Integer parseKid(String kid) {
        if (kid == null || !kid.startsWith(KID_PREFIX)) {
            return null;
        }
        try {
            return Integer.parseInt(kid.substring(KID_PREFIX.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static byte[] fixed(java.math.BigInteger n) {
        byte[] b = n.toByteArray();
        byte[] out = new byte[32];
        System.arraycopy(b, Math.max(0, b.length - 32), out, Math.max(0, 32 - b.length), Math.min(32, b.length));
        return out;
    }

    private static String b64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String cut(String s) {
        return s == null ? "-" : (s.length() > 40 ? s.substring(0, 40) : s).replaceAll("\\p{Cntrl}", " ");
    }

    private static void deny(String event, String reason, String target) {
        SecurityEventLogger.denied(event, "5-2").actor("anonymous").target(target).reason(reason).log();
    }

    private static void suspicious(String event, String reason, String target) {
        SecurityEventLogger.suspicious(event, "5-2").actor("anonymous").target(target).reason(reason).log();
    }
}
