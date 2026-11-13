package io.github.securekit.core.mask;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 로그·화면에 나가기 전에 가린다.
 *
 * - 전화번호: 010-0000-1234 → 010-****-1234 (끝 4자리만 남긴다)
 * - 이름이 password·token·secret 등인 칸의 값: password=*** · "token":"***"
 * - JWT 모양(eyJ….….…): [JWT]
 * - Authorization: Bearer …: Bearer ***
 */
public final class Masking {
    public static final List<String> DEFAULT_FIELDS =
            List.of("password", "newPassword", "token", "secret", "card", "authorization", "apiKey");

    private static final Pattern PHONE =
            Pattern.compile("(?<!\\d)(01[016789])[-. ]?(\\d{3,4})[-. ]?(\\d{4})(?!\\d)");
    private static final Pattern JWT =
            Pattern.compile("eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]*");
    private static final Pattern BEARER = Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]+");

    private static volatile Pattern fields = fieldPattern(DEFAULT_FIELDS);

    private Masking() {
    }

    /** 가릴 칸 이름을 바꾼다(설정 secure-kit.log-masking.fields) */
    public static void useFields(List<String> names) {
        fields = fieldPattern(names);
    }

    private static Pattern fieldPattern(List<String> names) {
        StringBuilder alt = new StringBuilder();
        for (String n : names) {
            if (alt.length() > 0) alt.append('|');
            alt.append(Pattern.quote(n.toLowerCase(Locale.ROOT)));
        }
        // "password":"값"  ·  password=값  ·  password: 값
        return Pattern.compile("(?i)(\"?(?:" + alt + ")\"?\\s*[:=]\\s*)(\"[^\"]*\"|[^,\\s}&\\]]+)");
    }

    /** 로그 한 줄 전체를 가린다 */
    public static String apply(String line) {
        if (line == null || line.isEmpty()) return line;
        Matcher m = fields.matcher(line);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String quote = m.group(2).startsWith("\"") ? "\"" : "";
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + quote + "***" + quote));
        }
        m.appendTail(sb);
        String out = BEARER.matcher(sb.toString()).replaceAll("$1***");
        out = JWT.matcher(out).replaceAll("[JWT]");
        return PHONE.matcher(out).replaceAll("$1-****-$3");
    }

    /** 전화번호 하나(화면 표시용). 모양이 아니면 전부 가린다 */
    public static String phone(String raw) {
        if (raw == null) return null;
        Matcher m = PHONE.matcher(raw);
        if (m.find()) return m.group(1) + "-****-" + m.group(3);
        return "***";
    }
}
