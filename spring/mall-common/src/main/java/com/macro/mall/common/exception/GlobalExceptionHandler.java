package com.macro.mall.common.exception;

import cn.hutool.core.util.StrUtil;
import com.macro.mall.common.api.CommonResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 전역 예외 처리 클래스
 * Created by macro on 2020/2/27.
 */
@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ResponseBody
    @ExceptionHandler(value = ApiException.class)
    public CommonResult handle(ApiException e) {
        if (e.getErrorCode() != null) {
            return CommonResult.failed(e.getErrorCode());
        }
        return CommonResult.failed(e.getMessage());
    }

    @ResponseBody
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public CommonResult handleValidException(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        String message = null;
        if (bindingResult.hasErrors()) {
            FieldError fieldError = bindingResult.getFieldError();
            if (fieldError != null) {
                message = fieldError.getField()+fieldError.getDefaultMessage();
            }
        }
        return CommonResult.validateFailed(message);
    }

    @ResponseBody
    @ExceptionHandler(value = BindException.class)
    public CommonResult handleValidException(BindException e) {
        BindingResult bindingResult = e.getBindingResult();
        String message = null;
        if (bindingResult.hasErrors()) {
            FieldError fieldError = bindingResult.getFieldError();
            if (fieldError != null) {
                message = fieldError.getField()+fieldError.getDefaultMessage();
            }
        }
        return CommonResult.validateFailed(message);
    }

    /**
     * [다온마켓 6-5] 데이터베이스 예외: 응답에는 일반 문구와 trace_id만. 원인(SQL 문구·표 이름)은 같은 trace_id로 서버 로그에만 남긴다.
     * 보호 스위치 secure-kit.error-mask.enabled=false(환경 변수 SECUREKIT_ERROR_MASK_ENABLED)면 옛 동작(문구를 그대로 응답)이다.
     * 예상 밖의 모든 예외를 한꺼번에 500으로 바꾸는 일(공통 500, /error)은 9-2에서 한다. 여기서는 DB 예외만 숨긴다.
     */
    @ResponseBody
    @ExceptionHandler(value = {SQLException.class, DataAccessException.class})
    public ResponseEntity<Object> handleDatabaseException(Exception e) {
        if (!maskOn()) {
            String message = e.getMessage();
            if (StrUtil.isNotEmpty(message) && message.contains("denied")) {
                message = "데모 환경에서는 수정 권한이 없습니다. 데이터를 수정하려면 로컬에 관리자 서비스를 구축하세요!";
            }
            return ResponseEntity.ok(CommonResult.failed(message));
        }
        String traceId = MDC.get("trace_id");
        LOGGER.error("[{}] 데이터베이스 예외(응답에는 내보내지 않음)", traceId, e);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 500);
        body.put("message", "요청을 처리하지 못했습니다");
        body.put("trace_id", traceId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private static boolean maskOn() {
        String v = System.getenv("SECUREKIT_ERROR_MASK_ENABLED");
        return v == null || !v.trim().equalsIgnoreCase("false");
    }
}
