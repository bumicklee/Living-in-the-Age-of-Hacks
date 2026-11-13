package com.daon.approval;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * [랩 도구] 방어 스위치(make switch, 랩-설계 12절). 관리자 앱의 SecureKitSwitch와 같은 규칙:
 * 값이 없거나 못 읽으면 켜짐, 꺼짐 값은 서명(SECUREKIT_SIG, 7-미션)이 맞을 때만 믿는다.
 * 회사 서비스로 옮길 때는 이 클래스를 지우고 보호를 늘 켠다.
 */
public final class Switches {
    private Switches() {
    }

    public static boolean on(String name) {
        Map<String, String> env = System.getenv();
        String v = env.get("SECUREKIT_" + name.toUpperCase(Locale.ROOT).replace('-', '_') + "_ENABLED");
        boolean enabled = v == null || !v.trim().equalsIgnoreCase("false");
        if (!enabled && !signed(env)) {
            return true;
        }
        return enabled;
    }

    private static boolean signed(Map<String, String> env) {
        String own = env.get("SECUREKIT_SIGNED_SWITCHES_ENABLED");
        if (own != null && own.trim().equalsIgnoreCase("false")) {
            return true;
        }
        String canonical = env.entrySet().stream()
                .filter(e -> e.getKey().startsWith("SECUREKIT_") && e.getKey().endsWith("_ENABLED") && e.getValue() != null
                        && e.getValue().trim().equalsIgnoreCase("false"))
                .map(e -> e.getKey() + "=false").sorted().collect(Collectors.joining("\n"));
        String key = env.get("DAON_SWITCH_SIGN_KEY");
        String sig = env.get("SECUREKIT_SIG");
        if (canonical.isEmpty()) {
            return true;
        }
        if (key == null || key.isBlank() || sig == null) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            StringBuilder hex = new StringBuilder();
            for (byte b : mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8))) {
                hex.append(String.format("%02x", b));
            }
            return MessageDigest.isEqual(hex.toString().getBytes(StandardCharsets.UTF_8),
                    sig.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }
}
