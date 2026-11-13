package io.github.securekit.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 부품이 요청을 막을 때 던지는 예외. HTTP 상태와 사용자에게 보일 짧은 한국어 문구만 담는다.
 *
 * 응답 본문은 늘 {"code", "message", "traceId"} 모양이고, 부품이 필요한 칸(예: stepUp)만 덧붙인다.
 * 내부 사정(예외 이름·SQL·값)은 문구에 넣지 않는다.
 */
public class SecureKitException extends RuntimeException {
    private final int status;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private final Map<String, Object> extra = new LinkedHashMap<>();

    public SecureKitException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }

    /** 응답 머리 하나를 더한다(예: Retry-After) */
    public SecureKitException header(String name, String value) {
        headers.put(name, value);
        return this;
    }

    /** 응답 본문에 칸 하나를 더한다(예: stepUp: required) */
    public SecureKitException extra(String key, Object value) {
        extra.put(key, value);
        return this;
    }

    public Map<String, String> headers() {
        return headers;
    }

    /** 응답 본문. 하위 클래스는 모양을 바꿀 수 있다(승인 대기 202 등) */
    public Map<String, Object> body(String traceId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", status);
        body.put("message", getMessage());
        body.put("traceId", traceId);
        body.putAll(extra);
        return body;
    }

    /** 막은 것은 버그가 아니다. 스택을 모으지 않아 가볍게 한다 */
    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
