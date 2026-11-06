package com.macro.mall.common.api;

/**
 * API반환 코드 인터페이스
 * Created by macro on 2019/4/19.
 */
public interface IErrorCode {
    /**
     * 반환 코드
     */
    long getCode();

    /**
     * 반환 정보
     */
    String getMessage();
}
