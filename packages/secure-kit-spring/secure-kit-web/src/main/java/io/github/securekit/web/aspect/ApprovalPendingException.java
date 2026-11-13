package io.github.securekit.web.aspect;

import io.github.securekit.core.SecureKitException;

import java.util.LinkedHashMap;
import java.util.Map;

/** 승인 대기: 202 {"status":"PENDING_APPROVAL","approvalId":"…"}. 오류가 아니라 「아직 하지 않았다」는 응답이다 */
public class ApprovalPendingException extends SecureKitException {
    private final String approvalId;

    public ApprovalPendingException(String approvalId) {
        super(202, "다른 담당자의 승인이 필요합니다");
        this.approvalId = approvalId;
    }

    @Override
    public Map<String, Object> body(String traceId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "PENDING_APPROVAL");
        body.put("approvalId", approvalId);
        body.put("message", getMessage());
        body.put("traceId", traceId);
        return body;
    }
}
