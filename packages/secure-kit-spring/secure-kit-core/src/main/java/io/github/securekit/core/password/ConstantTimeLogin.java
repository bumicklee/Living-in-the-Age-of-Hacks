package io.github.securekit.core.password;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 로그인 비교를 늘 같은 비용으로 한다(응답 시간으로 가입 여부가 새지 않게).
 *
 * 없는 아이디여도 미리 만든 가짜 해시와 같은 횟수로 PBKDF2를 돌린다. 결과 비교는 MessageDigest.isEqual(시간 일정).
 * 저장 모양: pbkdf2-sha256$반복$소금(base64)$해시(base64)
 */
public class ConstantTimeLogin {
    public static final int DEFAULT_ITERATIONS = 100_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final int iterations;
    private final String dummyHash;

    public ConstantTimeLogin() {
        this(DEFAULT_ITERATIONS);
    }

    public ConstantTimeLogin(int iterations) {
        this.iterations = iterations;
        this.dummyHash = hash("not-a-real-password-" + RANDOM.nextLong(), iterations);
    }

    /**
     * @param storedHash 없는 아이디면 null을 넘긴다(그래도 같은 비용으로 비교한다)
     */
    public boolean matches(String storedHash, String password) {
        boolean known = storedHash != null;
        boolean ok = verify(known ? storedHash : dummyHash, password == null ? "" : password);
        return known && ok;
    }

    public String hash(String password) {
        return hash(password, iterations);
    }

    public static String hash(String password, int iterations) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] h = pbkdf2(password, salt, iterations);
        Base64.Encoder b64 = Base64.getEncoder();
        return "pbkdf2-sha256$" + iterations + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(h);
    }

    private static boolean verify(String stored, String password) {
        String[] p = stored.split("\\$");
        if (p.length != 4 || !"pbkdf2-sha256".equals(p[0])) return false;
        byte[] salt = Base64.getDecoder().decode(p[2]);
        byte[] want = Base64.getDecoder().decode(p[3]);
        byte[] got = pbkdf2(password, salt, Integer.parseInt(p[1]));
        return MessageDigest.isEqual(want, got);
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2를 쓸 수 없다", e);
        }
    }

    /** 비밀번호와 무관한 값 비교용(재인증 토큰 등) */
    public static boolean sameBytes(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
