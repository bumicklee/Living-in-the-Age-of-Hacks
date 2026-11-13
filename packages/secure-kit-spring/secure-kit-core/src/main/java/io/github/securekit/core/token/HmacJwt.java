package io.github.securekit.core.token;

import io.github.securekit.core.json.MiniJson;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HS256 JWT 만들기·확인하기(라이브러리 없이 읽히게).
 *
 * 확인 순서가 중요하다.
 * 1. 머리의 alg는 서버가 정한 HS256만 받는다(alg=none·RS256 바꿔치기를 서명 확인 전에 막는다).
 * 2. kid가 서버 키 번호와 같아야 한다.
 * 3. 서명 비교는 시간 일정한 MessageDigest.isEqual.
 * 4. exp가 없으면 받지 않는다. 지났으면 받지 않는다.
 */
public class HmacJwt {
    public enum Failure { MALFORMED, DENIED_ALG, WRONG_KID, BAD_SIGNATURE, NO_EXP, EXPIRED }

    /** 확인 실패. 어떤 이유인지는 이벤트에만 쓰고 응답은 같은 401이다 */
    public static final class Invalid extends RuntimeException {
        private final Failure failure;

        Invalid(Failure failure) {
            super(failure.name(), null, false, false);
            this.failure = failure;
        }

        public Failure failure() {
            return failure;
        }
    }

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder D64 = Base64.getUrlDecoder();

    private final byte[] secret;
    private final String kid;

    public HmacJwt(String secret, String kid) {
        if (secret == null || secret.length() < 32) throw new IllegalArgumentException("토큰 비밀값은 32자 이상");
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.kid = kid;
    }

    public String sign(Map<String, Object> claims) {
        Map<String, Object> head = new LinkedHashMap<>();
        head.put("alg", "HS256");
        head.put("typ", "JWT");
        head.put("kid", kid);
        String data = enc(MiniJson.write(head)) + "." + enc(MiniJson.write(claims));
        return data + "." + B64.encodeToString(mac(data));
    }

    /** 확인한 내용(claims). 실패하면 {@link Invalid} */
    public Map<String, Object> verify(String token, long nowSeconds) {
        String[] p = token == null ? new String[0] : token.split("\\.", -1);
        if (p.length != 3) throw new Invalid(Failure.MALFORMED);
        Map<String, Object> head;
        Map<String, Object> claims;
        try {
            head = MiniJson.parseObject(dec(p[0]));
            claims = MiniJson.parseObject(dec(p[1]));
        } catch (RuntimeException e) {
            throw new Invalid(Failure.MALFORMED);
        }
        if (!"HS256".equals(head.get("alg"))) throw new Invalid(Failure.DENIED_ALG);
        if (!kid.equals(head.get("kid"))) throw new Invalid(Failure.WRONG_KID);
        byte[] sig;
        try {
            sig = D64.decode(p[2]);
        } catch (IllegalArgumentException e) {
            throw new Invalid(Failure.MALFORMED);
        }
        if (!MessageDigest.isEqual(sig, mac(p[0] + "." + p[1]))) throw new Invalid(Failure.BAD_SIGNATURE);
        if (!(claims.get("exp") instanceof Number exp)) throw new Invalid(Failure.NO_EXP);
        if (exp.longValue() <= nowSeconds) throw new Invalid(Failure.EXPIRED);
        return claims;
    }

    private byte[] mac(String data) {
        try {
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(secret, "HmacSHA256"));
            return m.doFinal(data.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String enc(String json) {
        return B64.encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String dec(String part) {
        return new String(D64.decode(part), StandardCharsets.UTF_8);
    }
}
