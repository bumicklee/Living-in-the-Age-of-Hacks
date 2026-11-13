package io.github.securekit.web.aspect;

import io.github.securekit.core.KitResettable;
import io.github.securekit.core.SecureKitException;
import io.github.securekit.core.event.SecurityEvents;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 승인 요청 보관함(13-3·13-4). 랩은 메모리에 둔다. 회사에서는 승인 서비스(DB + 알림)로 바꾼다.
 *
 * 규칙: 요청자 본인은 승인할 수 없다 · 승인 번호는 요청 내용(지문)에 묶인다 · 한 번 쓰면 끝.
 */
public class ApprovalService implements KitResettable {
    public enum Status { PENDING, APPROVED, USED }

    /** 승인 한 건 */
    public static final class Approval {
        final String id;
        final String policy;
        final String requester;
        final String fingerprint;
        Status status = Status.PENDING;
        String approver;

        Approval(String id, String policy, String requester, String fingerprint) {
            this.id = id;
            this.policy = policy;
            this.requester = requester;
            this.fingerprint = fingerprint;
        }

        public String id() { return id; }
        public Status status() { return status; }
        public String requester() { return requester; }
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private final Map<String, Approval> approvals = new LinkedHashMap<>();

    public synchronized Approval open(String policy, String requester, String fingerprint) {
        byte[] b = new byte[6];
        RANDOM.nextBytes(b);
        Approval a = new Approval("ap-" + HexFormat.of().formatHex(b), policy, requester, fingerprint);
        approvals.put(a.id, a);
        return a;
    }

    /** 승인. 없으면 404 · 요청자 본인이면 403 + approval.denied.self_approval */
    public synchronized Approval approve(String id, String approver) {
        Approval a = approvals.get(id);
        if (a == null) throw new SecureKitException(404, "승인 요청을 찾을 수 없습니다");
        if (approver == null || approver.isBlank() || approver.equals(a.requester)) {
            SecurityEvents.warn("approval.denied.self_approval", "approval", "approval:" + id, "요청자 본인 승인 시도");
            throw new SecureKitException(403, "요청자 본인은 승인할 수 없습니다");
        }
        if (a.status != Status.PENDING) throw new SecureKitException(409, "이미 처리한 승인 요청입니다");
        a.status = Status.APPROVED;
        a.approver = approver;
        SecurityEvents.info("approval.granted", "approval", "approval:" + id, "두 번째 사람이 승인");
        return a;
    }

    /** 승인 번호를 쓴다. 승인 전 403 · 내용이 다르면 403 · 이미 썼으면 409 */
    public synchronized void use(String id, String fingerprint) {
        Approval a = approvals.get(id);
        if (a == null) throw new SecureKitException(404, "승인 요청을 찾을 수 없습니다");
        if (a.status == Status.USED) {
            SecurityEvents.warn("approval.denied.reused", "approval", "approval:" + id, "이미 쓴 승인 번호");
            throw new SecureKitException(409, "이미 사용한 승인입니다");
        }
        if (a.status != Status.APPROVED) {
            SecurityEvents.warn("approval.denied.not_approved", "approval", "approval:" + id, "승인 전 실행 시도");
            throw new SecureKitException(403, "아직 승인되지 않았습니다");
        }
        if (!a.fingerprint.equals(fingerprint)) {
            SecurityEvents.warn("approval.denied.mismatch", "approval", "approval:" + id, "승인 때와 요청 내용이 다르다");
            throw new SecureKitException(403, "승인한 내용과 다릅니다");
        }
        a.status = Status.USED;
    }

    @Override
    public synchronized void reset() {
        approvals.clear();
    }
}
