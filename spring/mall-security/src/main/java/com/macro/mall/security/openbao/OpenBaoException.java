package com.macro.mall.security.openbao;

/** [다온마켓 A4] OpenBao 호출 실패. 메시지에는 토큰·AppRole 값·요청 본문을 넣지 않는다(경로와 상태 코드만). */
public class OpenBaoException extends RuntimeException {
    private final int status;

    public OpenBaoException(String message, int status) {
        super(message);
        this.status = status;
    }

    public OpenBaoException(String message, Throwable cause) {
        super(message, cause);
        this.status = -1;
    }

    /** HTTP 상태 코드. 연결 자체가 안 되면 -1 */
    public int getStatus() {
        return status;
    }
}
