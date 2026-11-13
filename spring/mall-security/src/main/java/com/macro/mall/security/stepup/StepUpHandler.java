package com.macro.mall.security.stepup;

import com.macro.mall.common.api.CommonResult;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** [다온마켓 4-7 정답] 재인증 필요를 HTTP 401로 답한다. 고객 웹은 data가 step_up_required면 재인증 창을 띄운다 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StepUpHandler {
    @ExceptionHandler(StepUpRequiredException.class)
    public ResponseEntity<CommonResult<String>> handle(StepUpRequiredException e) {
        CommonResult<String> body = CommonResult.unauthorized("step_up_required");
        body.setMessage(e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }
}
