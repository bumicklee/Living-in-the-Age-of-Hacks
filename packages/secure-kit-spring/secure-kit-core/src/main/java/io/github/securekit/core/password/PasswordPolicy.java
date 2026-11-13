package io.github.securekit.core.password;

import io.github.securekit.core.event.SecurityEvents;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 새 비밀번호 규칙(NIST 800-63B 방식): 길이는 길게, 이미 유출된 비밀번호는 받지 않는다.
 * 대문자·특수문자 섞기 같은 조합 규칙은 두지 않는다(사람이 예측 가능한 변형만 늘린다).
 *
 * 유출 목록은 설정으로 바꾼다(회사에서는 큰 목록 파일이나 k-익명성 조회 서비스를 쓴다).
 */
public class PasswordPolicy {
    public static final List<String> SAMPLE_BREACHED =
            List.of("password1234", "qwerty123456", "daon-pass-1!", "1q2w3e4r5t", "iloveyou123");

    /** 판정 결과. reason: too_short · breached · null(통과) */
    public static final class Result {
        private final boolean ok;
        private final String reason;

        private Result(boolean ok, String reason) {
            this.ok = ok;
            this.reason = reason;
        }

        public boolean ok() {
            return ok;
        }

        public String reason() {
            return reason;
        }
    }

    private final int minLength;
    private final Set<String> breached = new HashSet<>();

    public PasswordPolicy(int minLength, Collection<String> breachedList) {
        this.minLength = minLength;
        for (String b : breachedList) breached.add(b.toLowerCase(Locale.ROOT));
    }

    public Result check(String password) {
        if (password == null || password.codePointCount(0, password.length()) < minLength) {
            return new Result(false, "too_short");
        }
        if (breached.contains(password.toLowerCase(Locale.ROOT))) {
            // 비밀번호 값은 남기지 않는다
            SecurityEvents.info("auth.password.rejected_breached", "password-policy", "-", "유출 목록에 있는 비밀번호");
            return new Result(false, "breached");
        }
        return new Result(true, null);
    }

    public int minLength() {
        return minLength;
    }
}
