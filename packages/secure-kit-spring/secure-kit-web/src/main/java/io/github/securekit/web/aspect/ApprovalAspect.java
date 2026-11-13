package io.github.securekit.web.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.securekit.core.SecureKitException;
import io.github.securekit.core.event.SecurityEvents;
import io.github.securekit.web.KitContext;
import io.github.securekit.web.annotation.RequiresApproval;
import io.github.securekit.web.support.ArgFields;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

/**
 * &#64;RequiresApproval 처리. 기준 금액 이상이면 승인 번호 없이 진행하지 않는다.
 * 승인 번호는 요청 본문 지문에 묶인다(승인 뒤 금액을 바꿔 다시 보내도 403).
 */
@Aspect
@Order(KitOrder.APPROVAL)
public class ApprovalAspect {
    public static final String HEADER = "X-Approval-Id";
    private final ApprovalService approvals;
    private final ObjectMapper json;
    private final Environment env;

    public ApprovalAspect(ApprovalService approvals, ObjectMapper json, Environment env) {
        this.approvals = approvals;
        this.json = json;
        this.env = env;
    }

    @Around("@annotation(io.github.securekit.web.annotation.RequiresApproval)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        RequiresApproval ann = ArgFields.method(pjp).getAnnotation(RequiresApproval.class);
        long threshold = env.getProperty("secure-kit.approval.policies." + ann.policy() + ".threshold", Long.class, ann.threshold());
        Object amount = ArgFields.field(pjp.getArgs(), ann.amount());
        if (!(amount instanceof Number n)) throw new SecureKitException(400, "금액이 필요합니다");
        if (n.longValue() < threshold) return pjp.proceed();

        HttpServletRequest req = ArgFields.request();
        String requester = KitContext.subject(req);
        if ("anonymous".equals(requester)) throw new SecureKitException(401, "로그인이 필요합니다");
        String fingerprint = ArgFields.fingerprint(json, ArgFields.body(pjp));
        String approvalId = req.getHeader(HEADER);
        if (approvalId == null || approvalId.isBlank()) {
            ApprovalService.Approval a = approvals.open(ann.policy(), requester, fingerprint);
            SecurityEvents.info("approval.pending.second_approver", "approval", "approval:" + a.id(),
                    "기준 이상(" + ann.policy() + ") → 두 번째 승인 대기");
            throw new ApprovalPendingException(a.id());
        }
        approvals.use(approvalId.trim(), fingerprint);
        return pjp.proceed();
    }
}
