package io.github.securekit.core.secret;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 기본값 시크릿으로 기동하지 않기(6-3). 기동 검사기와 랩 전용 /_kit/startup/check가 같은 판단을 쓴다.
 *
 * 이름이 secret·password·key·token으로 끝나는 설정 키의 값이
 * 알려진 기본값이거나 12자 미만이면 거부한다. 값은 어디에도 돌려주거나 찍지 않는다.
 */
public final class SecretRules {
    public static final Set<String> KNOWN_DEFAULTS = Set.of(
            "changeme", "change-me", "secret", "password", "root", "admin",
            "minioadmin", "guest", "test", "123456", "macro123");
    public static final int MIN_LENGTH = 12;

    private SecretRules() {
    }

    public static boolean isSecretName(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith("secret") || n.endsWith("password") || n.endsWith("key") || n.endsWith("token");
    }

    public static boolean isWeak(String value) {
        if (value == null) return true;
        String v = value.trim();
        return v.length() < MIN_LENGTH || KNOWN_DEFAULTS.contains(v.toLowerCase(Locale.ROOT));
    }

    /** 거부할 키 이름 목록(입력 순서대로) */
    public static List<String> denied(Map<String, ?> props) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, ?> e : props.entrySet()) {
            Object v = e.getValue();
            if (isSecretName(e.getKey()) && isWeak(v == null ? null : String.valueOf(v))) out.add(e.getKey());
        }
        return out;
    }
}
