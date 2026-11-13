package com.macro.mall.security.ratelimit;

import com.macro.mall.common.api.CommonResult;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * [다온마켓 4-3 정답] 로그인 한도 초과를 HTTP 429로 답한다. 원본 전역 처리기(HTTP 200 + 본문 code)보다 먼저 잡는다.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LoginRateLimitHandler {

    @ExceptionHandler(LoginRateLimitedException.class)
    public ResponseEntity<CommonResult<Object>> handle(LoginRateLimitedException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
                .body(CommonResult.failed(e.getMessage()));
    }
}
