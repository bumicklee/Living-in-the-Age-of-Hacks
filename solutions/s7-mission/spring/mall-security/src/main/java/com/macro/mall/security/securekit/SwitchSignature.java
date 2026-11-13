package com.macro.mall.security.securekit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * [다온마켓 7-미션] 앱이 믿는 설정(방어 스위치 파일)의 서명 확인.
 *
 * <p>make switch 가 꺼진 스위치 줄(NAME=false)을 정렬해 이은 글자의 HMAC-SHA256(16진수)을 SECUREKIT_SIG 로 함께 적는다.
 * 앱은 같은 방식으로 다시 계산해 맞는지 본다. 맞지 않으면(파일을 손으로 고쳤거나 서명이 없으면) <b>꺼짐 값을 믿지 않는다</b>: 기본값(켜짐)으로 뜬다.
 * 서명 키(DAON_SWITCH_SIGN_KEY)는 파일 밖(compose 환경)에 있다. 파일만 고친 사람은 서명을 만들 수 없다.</p>
 */
public final class SwitchSignature {
    static final String SIG = "SECUREKIT_SIG";
    static final String KEY = "DAON_SWITCH_SIGN_KEY";

    private SwitchSignature() {
    }

    /** 환경에서 꺼진 스위치 줄(NAME=false)을 정렬해 이은 글자. 서명 대상이다 */
    static String canonical(Map<String, String> env) {
        List<String> off = env.entrySet().stream()
                .filter(e -> e.getKey().startsWith("SECUREKIT_") && e.getKey().endsWith("_ENABLED") && e.getValue() != null
                        && e.getValue().trim().toLowerCase(Locale.ROOT).equals("false"))
                .map(e -> e.getKey() + "=false")
                .sorted()
                .collect(Collectors.toList());
        return String.join("\n", off);
    }

    static boolean hasOffSwitch(Map<String, String> env) {
        return !canonical(env).isEmpty();
    }

    /** 꺼진 스위치가 없으면 확인할 것이 없다(참). 있으면 서명이 맞아야 참 */
    static boolean valid(Map<String, String> env) {
        String canonical = canonical(env);
        if (canonical.isEmpty()) {
            return true;
        }
        String key = env.get(KEY);
        String sig = env.get(SIG);
        if (key == null || key.isBlank() || sig == null) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expect = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : expect) {
                hex.append(String.format("%02x", b));
            }
            return MessageDigest.isEqual(hex.toString().getBytes(StandardCharsets.UTF_8), sig.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;               // 확인하지 못하면 믿지 않는다
        }
    }

    static boolean valid() {
        return valid(System.getenv());
    }
}
