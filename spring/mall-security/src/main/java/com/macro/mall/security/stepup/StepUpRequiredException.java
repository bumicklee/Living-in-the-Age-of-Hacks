package com.macro.mall.security.stepup;

/** [다온마켓 4-7 정답] 재인증이 필요하다. StepUpHandler가 HTTP 401 + data "step_up_required"로 바꾼다 */
public class StepUpRequiredException extends RuntimeException {
    public StepUpRequiredException() {
        super("본인 확인을 다시 해 주세요");
    }
}
