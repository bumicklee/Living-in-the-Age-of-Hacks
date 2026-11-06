package com.macro.mall.common.exception;

import com.macro.mall.common.api.IErrorCode;

/**
 * 다양한 API 예외를 던지는 데 쓰는 단언(Assert) 처리 클래스
 * Created by macro on 2020/2/27.
 */
public class Asserts {
    public static void fail(String message) {
        throw new ApiException(message);
    }

    public static void fail(IErrorCode errorCode) {
        throw new ApiException(errorCode);
    }
}
