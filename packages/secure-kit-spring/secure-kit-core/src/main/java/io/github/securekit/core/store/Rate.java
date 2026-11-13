package io.github.securekit.core.store;

import java.time.Duration;

/**
 * "5/10m"처럼 쓰는 한도: 창(10분) 안에 5번까지.
 * 단위: s(초) m(분) h(시간) d(일). 설정 파일과 어노테이션이 같은 모양을 쓴다.
 */
public final class Rate {
    private final long max;
    private final Duration window;

    public Rate(long max, Duration window) {
        if (max < 1 || window.isZero() || window.isNegative()) throw new IllegalArgumentException("한도는 1 이상, 창은 0보다 커야 한다");
        this.max = max;
        this.window = window;
    }

    public static Rate parse(String text) {
        String[] p = text.trim().split("/");
        if (p.length != 2) throw new IllegalArgumentException("한도 형식은 횟수/기간(예: 5/10m): " + text);
        return new Rate(Long.parseLong(p[0].trim()), duration(p[1]));
    }

    /** "60s", "10m", "24h", "1d". 숫자만 쓰면 초 */
    public static Duration duration(String text) {
        String t = text.trim().toLowerCase();
        char unit = t.charAt(t.length() - 1);
        if (Character.isDigit(unit)) return Duration.ofSeconds(Long.parseLong(t));
        long n = Long.parseLong(t.substring(0, t.length() - 1));
        switch (unit) {
            case 's': return Duration.ofSeconds(n);
            case 'm': return Duration.ofMinutes(n);
            case 'h': return Duration.ofHours(n);
            case 'd': return Duration.ofDays(n);
            default: throw new IllegalArgumentException("기간 단위는 s·m·h·d: " + text);
        }
    }

    public long max() {
        return max;
    }

    public Duration window() {
        return window;
    }

    @Override
    public String toString() {
        return max + "/" + window.getSeconds() + "s";
    }
}
