package com.macro.mall.common.api;

/**
 * API반환 코드 래퍼 클래스
 * Created by macro on 2019/4/19.
 */
public enum ResultCode implements IErrorCode {
    SUCCESS(200, "작업 성공"),
    FAILED(500, "작업 실패"),
    VALIDATE_FAILED(404, "파라미터 검증 실패"),
    UNAUTHORIZED(401, "로그인하지 않았거나 token이 만료되었습니다"),
    FORBIDDEN(403, "관련 권한 없음");
    private long code;
    private String message;

    private ResultCode(long code, String message) {
        this.code = code;
        this.message = message;
    }

    public long getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
