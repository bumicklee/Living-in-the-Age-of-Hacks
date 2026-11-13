package io.github.securekit.web.error;

import io.github.securekit.core.KitMessages;
import io.github.securekit.web.KitContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 오류 응답 노출 막기(6-5). 설정 secure-kit.error-response.enabled: true 로 켠다.
 *
 * - 응답: {"code": 상태, "message": "<일반 문구>", "traceId": "<추적 번호>"} 만.
 *   예외 이름·메시지·스택·SQL 조각은 응답에 넣지 않는다.
 * - 원인은 서버 로그에 추적 번호와 함께 남긴다(사용자는 추적 번호로 문의한다).
 * - 스프링이 상태를 아는 예외(404·405·400 등)는 그 상태를 쓰고, 나머지는 500.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class KitErrorResponse {
    private static final Logger log = LoggerFactory.getLogger(KitErrorResponse.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handle(Exception e, HttpServletRequest request) {
        int status = statusOf(e);
        String traceId = KitContext.traceId(request);
        if (status >= 500) {
            log.error("처리하지 못한 오류 traceId={} path={}", traceId, request.getRequestURI(), e);
        } else {
            log.info("요청 오류 {} traceId={} path={} type={}", status, traceId, request.getRequestURI(), e.getClass().getSimpleName());
        }
        return ResponseEntity.status(status).body(body(status, traceId));
    }

    public static Map<String, Object> body(int status, String traceId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", status);
        body.put("message", KitMessages.forStatus(status));
        body.put("traceId", traceId);
        return body;
    }

    private static int statusOf(Exception e) {
        if (e instanceof ErrorResponse er) return er.getStatusCode().value();
        if (e instanceof HttpMessageNotReadableException || e instanceof TypeMismatchException) return 400;
        return 500;
    }
}
