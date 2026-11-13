package io.github.securekit.core.regex;

import io.github.securekit.core.SecureKitException;
import io.github.securekit.core.event.SecurityEvents;

import java.util.regex.Pattern;

/**
 * 느린 정규식(ReDoS) 막기. 두 겹으로 막는다.
 *
 * 1. 길이를 **정규식 전에** 본다. 상한을 넘으면 400 + input.denied.too_long.
 * 2. 그래도 오래 걸리면(기본 100ms) 멈추고 형식 밖으로 본다(input.denied.regex_timeout).
 *
 * 정규식 자체도 되돌아가기 폭주가 없는 모양으로 쓴다. 예: ^[A-Z0-9]+(-[A-Z0-9]+)*$ (구분자 '-'가 있어 나누는 방법이 하나뿐)
 * 나쁜 예: ^([A-Z0-9]+)+$ (같은 글자를 나누는 방법이 길이에 따라 폭발한다)
 */
public class SafeRegex {
    private final Pattern pattern;
    private final int maxLength;
    private final long timeoutNanos;

    public SafeRegex(String regex, int maxLength) {
        this(regex, maxLength, 100);
    }

    public SafeRegex(String regex, int maxLength, long timeoutMillis) {
        this.pattern = Pattern.compile(regex);
        this.maxLength = maxLength;
        this.timeoutNanos = timeoutMillis * 1_000_000L;
    }

    /** 형식에 맞으면 true. 너무 길면 SecureKitException(400) */
    public boolean matches(String input) {
        if (input == null) return false;
        if (input.length() > maxLength) {
            SecurityEvents.warn("input.denied.too_long", "limits", "length:" + input.length(), "정규식 전에 길이 상한(" + maxLength + "자)을 넘었다");
            throw new SecureKitException(400, "입력이 너무 깁니다");
        }
        try {
            return pattern.matcher(new Deadline(input, System.nanoTime() + timeoutNanos)).matches();
        } catch (TimeoutException e) {
            SecurityEvents.warn("input.denied.regex_timeout", "limits", "length:" + input.length(), "정규식 시간 상한을 넘었다");
            return false;
        }
    }

    private static final class TimeoutException extends RuntimeException {
        TimeoutException() {
            super(null, null, false, false);
        }
    }

    /** 정규식 엔진이 글자를 읽을 때마다 시간을 본다(엔진을 바꾸지 않고 멈추는 방법) */
    private static final class Deadline implements CharSequence {
        private final CharSequence s;
        private final long deadline;

        Deadline(CharSequence s, long deadline) {
            this.s = s;
            this.deadline = deadline;
        }

        @Override
        public char charAt(int index) {
            if (System.nanoTime() > deadline) throw new TimeoutException();
            return s.charAt(index);
        }

        @Override
        public int length() {
            return s.length();
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new Deadline(s.subSequence(start, end), deadline);
        }

        @Override
        public String toString() {
            return s.toString();
        }
    }
}
