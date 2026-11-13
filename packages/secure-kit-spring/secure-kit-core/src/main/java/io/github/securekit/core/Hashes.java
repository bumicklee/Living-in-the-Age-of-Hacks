package io.github.securekit.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 이벤트 target 등에 원문 대신 넣는 짧은 해시 */
public final class Hashes {
    private Hashes() {
    }

    public static String sha256Hex(String value) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** sha256 16진수 앞 8자(예: phone:1a2b3c4d) */
    public static String short8(String value) {
        return sha256Hex(value).substring(0, 8);
    }
}
