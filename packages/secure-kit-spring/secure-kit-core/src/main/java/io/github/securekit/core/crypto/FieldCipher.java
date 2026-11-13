package io.github.securekit.core.crypto;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 개인정보 칸 암호화(AES-256-GCM)와 검색용 해시(blind index).
 *
 * - 저장 모양: enc:1:base64(논스 12바이트 + 암호문·태그). 1은 키 버전(키를 바꿀 때 2로 올린다).
 * - 논스는 매번 새로 뽑는다 → 같은 번호도 암호문이 다르다(같은 값끼리 묶어 보이지 않는다).
 * - 검색은 평문 비교가 아니라 HMAC-SHA256(검색 키, 정규화한 값)으로 한다. 암호 키와 검색 키는 따로 둔다.
 */
public class FieldCipher {
    public static final String PREFIX = "enc:1:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;
    private final byte[] indexKey;

    /** 키는 32바이트. 설정 값은 keyFrom()으로 바꾼다 */
    public FieldCipher(byte[] key, byte[] indexKey) {
        if (key.length != 32 || indexKey.length < 32) throw new IllegalArgumentException("암호 키·검색 키는 32바이트");
        this.key = new SecretKeySpec(key, "AES");
        this.indexKey = indexKey.clone();
    }

    /** "base64:..."면 그대로 풀고, 아니면 SHA-256으로 32바이트를 만든다(랩 편의. 회사에서는 KMS·OpenBao에서 받는다) */
    public static byte[] keyFrom(String configured) {
        if (configured.startsWith("base64:")) return Base64.getDecoder().decode(configured.substring(7));
        try {
            return MessageDigest.getInstance("SHA-256").digest(configured.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public String encrypt(String plain) {
        if (plain == null) return null;
        try {
            byte[] nonce = new byte[12];
            RANDOM.nextBytes(nonce);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(12 + ct.length).put(nonce).put(ct).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("암호화 실패", e);
        }
    }

    /** 형식이 틀리거나 위조됐으면 IllegalArgumentException(값은 메시지에 넣지 않는다) */
    public String decrypt(String stored) {
        if (stored == null) return null;
        if (!stored.startsWith(PREFIX)) throw new IllegalArgumentException("암호문 형식이 아님");
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, all, 0, 12));
            return new String(c.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("복호화 실패");
        }
    }

    /** 검색용 해시. 하이픈·공백을 빼고 계산한다(하이픈이 있든 없든 같은 번호면 같은 해시) */
    public String blindIndex(String value) {
        if (value == null) return null;
        String normalized = value.replaceAll("[\\s-]", "");
        try {
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(indexKey, "HmacSHA256"));
            return HexFormat.of().formatHex(m.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
