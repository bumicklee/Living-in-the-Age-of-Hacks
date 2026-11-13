package com.macro.mall.common.exception;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.common.api.CommonResult;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.multipart.MultipartException;

import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 전역 예외 처리 클래스
 * Created by macro on 2020/2/27.
 */
@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    /** [다온마켓 9-2] 운영 이벤트 로거(SECURITY와 같은 키, 다른 로거). error.unhandled 한 줄 */
    private static final Logger OPS = LoggerFactory.getLogger("OPS");
    private static final ObjectMapper JSON = new ObjectMapper();

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

    /**
     * [다온마켓 9-2 정답, F46] 마지막 처리기: 어느 처리기도 맡지 않은 예외는 모두 HTTP 500 + 일반 문구 + trace_id.
     * - 예외가 컨테이너까지 올라가면 /error로 넘어가고, /error가 인증 대상이면 장애가 401(로그인 문제)로 둔갑한다.
     * - 응답에는 예외 문구·클래스 이름을 싣지 않는다(6-5). 원인은 같은 trace_id로 서버 로그에만.
     * - Spring이 이미 상태 코드를 아는 예외(404·405·400 등, ErrorResponse)와 Spring Security 예외(401·403)는 그대로 던져 원래 처리에 맡긴다.
     *   다른 @ControllerAdvice(재인증·속도 제한)는 우선순위가 높아 먼저 고른다. 이 클래스 안에서는 더 구체적인 처리기가 먼저다.
     * - 보호 스위치 secure-kit.error-path.enabled=false(SECUREKIT_ERROR_PATH_ENABLED)면 옛 동작(그대로 던짐).
     */
    @ExceptionHandler(value = Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception e, HttpServletRequest request) throws Exception {
        if (!switchOn("SECUREKIT_ERROR_PATH_ENABLED") || passThrough(e)) {
            throw e;
        }
        String traceId = MDC.get("trace_id");
        Object h = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        String where = h instanceof HandlerMethod hm ? hm.getBeanType().getSimpleName() + "." + hm.getMethod().getName() : "unknown";
        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("event", "error.unhandled");
        ev.put("lesson", "9-2");
        ev.put("target", "handler:" + where);
        ev.put("reason", e.getClass().getSimpleName());
        ev.put("ts", OffsetDateTime.now(ZoneId.of("Asia/Seoul")).toString());
        ev.put("trace_id", traceId);
        try {
            OPS.error(JSON.writeValueAsString(ev));
        } catch (JsonProcessingException ignored) {
            OPS.error("{\"event\":\"error.unhandled\",\"lesson\":\"9-2\"}");
        }
        LOGGER.error("[{}] 처리되지 않은 예외({}) → 500", traceId, where, e);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 500);
        body.put("message", "일시적인 오류입니다");
        body.put("trace_id", traceId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    /** 원래 처리(상태 코드가 정해진 예외, 보안 예외)에 맡길 예외 */
    static boolean passThrough(Exception e) {
        if (e instanceof ErrorResponse || e instanceof ServletException || e instanceof TypeMismatchException
                || e instanceof HttpMessageNotReadableException || e instanceof AsyncRequestTimeoutException
                || e instanceof MultipartException) {
            return true;
        }
        if (AnnotatedElementUtils.hasAnnotation(e.getClass(), ResponseStatus.class)) {
            return true;
        }
        return e.getClass().getName().startsWith("org.springframework.security.");
    }

    private static boolean maskOn() {
        return switchOn("SECUREKIT_ERROR_MASK_ENABLED");
    }

    private static boolean switchOn(String env) {
        String v = System.getenv(env);
        return v == null || !v.trim().equalsIgnoreCase("false");
    }
}
