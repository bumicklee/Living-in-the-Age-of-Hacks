package com.macro.mall.security.secret;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** [다온마켓 6-3] 알려진 기본값 목록(resources/daon/known-default-secrets.txt). 6-2 점검 규칙과 같은 목록이다. */
public final class KnownDefaultSecrets {
    private static final Set<String> VALUES = load();

    private KnownDefaultSecrets() {
    }

    public static boolean contains(String value) {
        return value != null && VALUES.contains(value.trim().toLowerCase(Locale.ROOT));
    }

    private static Set<String> load() {
        Set<String> out = new HashSet<>();
        try (InputStream in = KnownDefaultSecrets.class.getResourceAsStream("/daon/known-default-secrets.txt")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = r.readLine()) != null) {
                    String s = line.strip();
                    if (!s.isEmpty() && !s.startsWith("#")) {
                        out.add(s.toLowerCase(Locale.ROOT));
                    }
                }
            }
        } catch (IOException e) {
            // 목록을 못 읽으면 빈 목록이 된다. 그래서 목록이 비어 있으면 StartupSecretCheck가 스스로 시작을 멈춘다(실패하면 막는다)
        }
        return out;
    }

    public static boolean isEmpty() {
        return VALUES.isEmpty();
    }
}
