package io.github.securekit.web.error;

import io.github.securekit.core.SecureKitException;
import io.github.securekit.web.KitContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 부품이 막은 요청(SecureKitException)을 응답으로 바꾼다. error-response 부품을 꺼도 늘 돈다.
 * 다른 예외는 건드리지 않는다(그건 KitErrorResponse의 일).
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class KitDeniedAdvice {
    @ExceptionHandler(SecureKitException.class)
    public ResponseEntity<Map<String, Object>> denied(SecureKitException e, HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        e.headers().forEach(headers::set);
        return ResponseEntity.status(e.status()).headers(headers).body(e.body(KitContext.traceId(request)));
    }
}
