package io.github.securekit.core;

/** 상태별 일반 문구. 응답에는 이 문구만 나간다(원인은 서버 로그와 추적 번호로 찾는다) */
public final class KitMessages {
    public static final String GENERIC_ERROR = "요청을 처리하지 못했습니다";
    public static final String LOGIN_FAILED = "아이디 또는 비밀번호가 올바르지 않습니다";
    public static final String TRY_LATER = "잠시 후 다시 시도하세요";

    private KitMessages() {
    }

    public static String forStatus(int status) {
        switch (status) {
            case 400: return "요청 형식이 올바르지 않습니다";
            case 401: return "로그인이 필요합니다";
            case 403: return "권한이 없습니다";
            case 404: return "찾을 수 없습니다";
            case 405: return "허용하지 않는 요청 방법입니다";
            case 409: return "이미 처리한 요청입니다";
            case 413: return "요청이 너무 큽니다";
            case 415: return "지원하지 않는 형식입니다";
            case 422: return "요청 내용을 처리할 수 없습니다";
            case 429: return TRY_LATER;
            case 502: return "연결된 서비스가 응답하지 않습니다";
            case 503: return TRY_LATER;
            default:
                return status >= 500 ? GENERIC_ERROR : "요청을 처리할 수 없습니다";
        }
    }
}
