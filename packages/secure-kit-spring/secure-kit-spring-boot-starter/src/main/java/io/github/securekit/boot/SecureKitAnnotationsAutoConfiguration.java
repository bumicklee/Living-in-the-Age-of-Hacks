package io.github.securekit.boot;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.securekit.audit.AuditAspect;
import io.github.securekit.core.store.CounterStore;
import io.github.securekit.web.aspect.ApprovalAspect;
import io.github.securekit.web.aspect.ApprovalService;
import io.github.securekit.web.aspect.BudgetAspect;
import io.github.securekit.web.aspect.FailClosedAspect;
import io.github.securekit.web.aspect.IdempotencyAspect;
import io.github.securekit.web.aspect.PartnerScopeAspect;
import io.github.securekit.web.aspect.RateLimitAspect;
import io.github.securekit.web.aspect.StepUpAspect;
import io.github.securekit.web.aspect.StepUpVerifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * ② 어노테이션 한 줄 부품. 어노테이션을 붙인 곳에서만 일한다(붙이지 않으면 아무것도 하지 않는다).
 * 영상용 끄기 스위치: secure-kit.&lt;부품&gt;.enabled: false. 회사 서비스에서는 끄는 스위치를 남겨 두지 않는다.
 */
@AutoConfiguration(after = {SecureKitAutoConfiguration.class, JacksonAutoConfiguration.class, AopAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecureKitAnnotationsAutoConfiguration {
    private static final String P = "secure-kit";

    @Bean
    @ConditionalOnProperty(prefix = P + ".rate-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RateLimitAspect secureKitRateLimitAspect(CounterStore store, Environment env) {
        return new RateLimitAspect(store, env);
    }

    @Bean
    @ConditionalOnProperty(prefix = P + ".step-up", name = "enabled", havingValue = "true", matchIfMissing = true)
    public StepUpAspect secureKitStepUpAspect(ObjectProvider<StepUpVerifier> verifier) {
        return new StepUpAspect(verifier);
    }

    @Bean
    @ConditionalOnProperty(prefix = P + ".partner-scope", name = "enabled", havingValue = "true", matchIfMissing = true)
    public PartnerScopeAspect secureKitPartnerScopeAspect() {
        return new PartnerScopeAspect();
    }

    @Bean
    @ConditionalOnMissingBean
    public ApprovalService secureKitApprovalService() {
        return new ApprovalService();
    }

    @Bean
    @ConditionalOnProperty(prefix = P + ".approval", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ApprovalAspect secureKitApprovalAspect(ApprovalService approvals, ObjectProvider<ObjectMapper> json, Environment env) {
        return new ApprovalAspect(approvals, json.getIfAvailable(ObjectMapper::new), env);
    }

    @Bean
    @ConditionalOnProperty(prefix = P + ".idempotency", name = "enabled", havingValue = "true", matchIfMissing = true)
    public IdempotencyAspect secureKitIdempotencyAspect(CounterStore store, ObjectProvider<ObjectMapper> json) {
        return new IdempotencyAspect(store, json.getIfAvailable(ObjectMapper::new));
    }

    @Bean
    @ConditionalOnProperty(prefix = P + ".budget", name = "enabled", havingValue = "true", matchIfMissing = true)
    public BudgetAspect secureKitBudgetAspect(CounterStore store, Environment env) {
        return new BudgetAspect(store, env);
    }

    @Bean
    @ConditionalOnProperty(prefix = P + ".fail-closed", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FailClosedAspect secureKitFailClosedAspect() {
        return new FailClosedAspect();
    }

    @Bean
    @ConditionalOnProperty(prefix = P + ".audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    public AuditAspect secureKitAuditAspect(SecureKitProperties props) {
        return new AuditAspect(props.getAudit().getReasonHeader());
    }
}
