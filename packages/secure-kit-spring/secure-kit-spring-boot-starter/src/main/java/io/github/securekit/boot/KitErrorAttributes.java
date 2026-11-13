package io.github.securekit.boot;

import io.github.securekit.web.KitContext;
import io.github.securekit.web.error.KitErrorResponse;
import jakarta.servlet.RequestDispatcher;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

/**
 * Spring Boot 기본 오류 화면(/error)의 본문을 {"code","message","traceId"}로 바꾼다.
 * 필터에서 난 예외처럼 컨트롤러 조언(advice)이 못 받는 오류도 내부 문구가 나가지 않는다.
 */
public class KitErrorAttributes extends DefaultErrorAttributes {
    @Override
    public Map<String, Object> getErrorAttributes(WebRequest request, ErrorAttributeOptions options) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE, RequestAttributes.SCOPE_REQUEST);
        Object trace = request.getAttribute(KitContext.TRACE_ATTR, RequestAttributes.SCOPE_REQUEST);
        int code = status instanceof Integer i ? i : 500;
        return KitErrorResponse.body(code, trace == null ? "-" : trace.toString());
    }
}
