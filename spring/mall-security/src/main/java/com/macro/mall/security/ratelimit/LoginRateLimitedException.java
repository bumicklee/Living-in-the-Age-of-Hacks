package com.macro.mall.security.ratelimit;

/**
 * [다온마켓 4-3 정답] 로그인 한도 초과. LoginRateLimitHandler가 HTTP 429와 Retry-After로 바꾼다.
 * 문구는 하나뿐이다(계정이 있는지 드러내지 않는다).
 */
public class LoginRateLimitedException extends RuntimeException {
    private final long retryAfterSeconds;

    public LoginRateLimitedException(long retryAfterSeconds) {
        super(LoginRateLimiter.MESSAGE);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
