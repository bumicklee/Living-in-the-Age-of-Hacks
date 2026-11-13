package io.github.securekit.boot;

import io.github.securekit.audit.MaskingConverter;
import io.github.securekit.core.crypto.EncryptedFields;
import io.github.securekit.core.crypto.FieldCipher;
import io.github.securekit.core.event.SecurityEvents;
import io.github.securekit.core.password.ConstantTimeLogin;
import io.github.securekit.core.password.PasswordPolicy;
import io.github.securekit.core.store.CounterStore;
import io.github.securekit.core.store.InMemoryCounterStore;
import io.github.securekit.core.token.HmacJwt;
import io.github.securekit.web.KitContext;
import io.github.securekit.web.TraceIdFilter;
import io.github.securekit.web.error.KitDeniedAdvice;
import io.github.securekit.web.error.KitErrorResponse;
import io.github.securekit.web.filter.HostAllowlistFilter;
import io.github.securekit.web.filter.NoStoreAuthenticatedFilter;
import io.github.securekit.web.filter.RequestSizeLimitFilter;
import io.github.securekit.web.filter.SecurityHeadersFilter;
import io.github.securekit.web.part.ResetLinkBuilder;
import io.github.securekit.web.part.ResilientClients;
import io.github.securekit.web.part.SafeHttpClient;
import io.github.securekit.web.part.SafeRedirect;
import io.github.securekit.web.part.UploadValidator;
import io.github.securekit.web.part.WebhookVerifier;
import io.github.securekit.web.token.KitTokenFilter;
import io.github.securekit.web.token.KitTokenService;
import jakarta.servlet.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 의존성 한 줄로 켜지는 부분: 필터(①설정만 켜기)와 바꿔 쓰는 부품(③) 빈.
 * 어노테이션(②) 처리는 SecureKitAnnotationsAutoConfiguration, 랩 전용 주소는 SecureKitLabAutoConfiguration.
 *
 * 필터 순서(앞 → 뒤): 추적 번호 → 보안 헤더·CORS → Host 허용 목록 → 본문 상한 → no-store → 토큰.
 * 헤더가 Host 검사보다 앞이라 막힌 400 응답에도 보안 헤더가 붙는다.
 */
@AutoConfiguration(before = ErrorMvcAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(SecureKitProperties.class)
public class SecureKitAutoConfiguration {
    private static final Logger SECURITY = LoggerFactory.getLogger("SECURITY");
    private static final String P = "secure-kit";

    public SecureKitAutoConfiguration(SecureKitProperties props) {
        // 막으면 남긴다: 이벤트를 로거 SECURITY로 → "WARN SECURITY {json}"
        SecurityEvents.usePrinter((level, json) -> {
            if ("WARN".equals(level)) SECURITY.warn(json);
            else SECURITY.info(json);
        });
        KitContext.installEventContext();
        MaskingConverter.configure(props.getLogMasking().isEnabled(), props.getLogMasking().getFields());
    }

    // ── 저장소 ──
    @Bean
    @ConditionalOnMissingBean
    public CounterStore secureKitCounterStore() {
        return new InMemoryCounterStore();
    }

    // ── ① 필터 ──
    @Bean
    public FilterRegistrationBean<Filter> secureKitTraceIdFilter() {
        return filter(new TraceIdFilter(), 0);
    }

    @Bean
    @ConditionalOnProperty(prefix = P + ".headers", name = "enabled", havingValue = "true")
    public FilterRegistrationBean<Filter> secureKitHeadersFilter(SecureKitProperties props) {
        SecureKitProperties.Cors c = props.getCors();
        return filter(new SecurityHeadersFilter(true, props.getHeaders().getCsp(),
                c.getAllowedOrigins(), c.getAllowedMethods(), c.getAllowedHeaders()), 10);
    }

    @Bean
    @ConditionalOnProperty(prefix = P + ".host-allowlist", name = "enabled", havingValue = "true")
    public FilterRegistrationBean<Filter> secureKitHostAllowlistFilter(SecureKitProperties props) {
        return filter(new HostAllowlistFilter(props.getHostAllowlist().getHosts()), 20);
    }

    @Bean
    @ConditionalOnProperty(prefix = P + ".limits", name = "enabled", havingValue = "true")
    public FilterRegistrationBean<Filter> secureKitRequestSizeLimitFilter(SecureKitProperties props) {
        Map<String, Long> paths = new LinkedHashMap<>();
        props.getLimits().getPaths().forEach((k, v) -> paths.put(k, v.toBytes()));
        return filter(new RequestSizeLimitFilter(props.getLimits().getMaxBody().toBytes(), paths), 30);
    }

    @Bean
    @ConditionalOnProperty(prefix = P + ".cache-control", name = "enabled", havingValue = "true")
    public FilterRegistrationBean<Filter> secureKitNoStoreFilter(SecureKitProperties props) {
        return filter(new NoStoreAuthenticatedFilter(props.getCacheControl().getSessionCookies()), 40);
    }

    @Bean
    @ConditionalOnProperty(prefix = P + ".token", name = "enabled", havingValue = "true")
    public KitTokenService secureKitTokenService(SecureKitProperties props, CounterStore store) {
        SecureKitProperties.Token t = props.getToken();
        return new KitTokenService(new HmacJwt(t.getSecret(), t.getKid()), store, t.getTtl(), t.getAudience());
    }

    @Bean
    @ConditionalOnProperty(prefix = P + ".token", name = "enabled", havingValue = "true")
    public FilterRegistrationBean<Filter> secureKitTokenFilter(KitTokenService tokens) {
        return filter(new KitTokenFilter(tokens), 50);
    }

    private static FilterRegistrationBean<Filter> filter(Filter f, int step) {
        FilterRegistrationBean<Filter> reg = new FilterRegistrationBean<>(f);
        reg.setName("secureKit" + f.getClass().getSimpleName());
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + step);
        return reg;
    }

    // ── 오류 응답 ──
    @Bean
    public KitDeniedAdvice secureKitDeniedAdvice() {
        return new KitDeniedAdvice();
    }

    @Bean
    @ConditionalOnProperty(prefix = P + ".error-response", name = "enabled", havingValue = "true")
    public KitErrorResponse secureKitErrorResponse() {
        return new KitErrorResponse();
    }

    /** 컨트롤러 밖 오류(/error)도 같은 모양. server.error.include-* 설정이 있어도 덮어쓴다 */
    @Bean
    @ConditionalOnProperty(prefix = P + ".error-response", name = "enabled", havingValue = "true")
    public ErrorAttributes secureKitErrorAttributes() {
        return new KitErrorAttributes();
    }

    // ── ③ 바꿔 쓰는 부품 ──
    @Bean
    @ConditionalOnMissingBean
    public SafeHttpClient safeHttpClient(SecureKitProperties props) {
        SecureKitProperties.SafeHttpClient c = props.getSafeHttpClient();
        return new SafeHttpClient((int) c.getTimeout().toMillis(), (int) c.getMaxBody().toBytes());
    }

    @Bean
    @ConditionalOnMissingBean
    public SafeRedirect safeRedirect(SecureKitProperties props) {
        return new SafeRedirect(props.getSafeRedirect().getAllowedHosts());
    }

    @Bean
    @ConditionalOnMissingBean
    public UploadValidator uploadValidator(SecureKitProperties props) {
        return new UploadValidator(props.getUpload().getMaxSize().toBytes(), Path.of(props.getUpload().getDir()));
    }

    @Bean
    @ConditionalOnMissingBean
    public PasswordPolicy passwordPolicy(SecureKitProperties props) {
        return new PasswordPolicy(props.getPasswordPolicy().getMinLength(), props.getPasswordPolicy().getBreached());
    }

    @Bean
    @ConditionalOnMissingBean
    public ConstantTimeLogin constantTimeLogin(SecureKitProperties props) {
        return new ConstantTimeLogin(props.getPasswordPolicy().getIterations());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = P + ".reset-link", name = "base-url")
    public ResetLinkBuilder resetLinkBuilder(SecureKitProperties props, CounterStore store) {
        return new ResetLinkBuilder(props.getResetLink().getBaseUrl(), store, props.getResetLink().getTtl());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = P + ".webhook", name = "secret")
    public WebhookVerifier webhookVerifier(SecureKitProperties props, CounterStore store) {
        return new WebhookVerifier(props.getWebhook().getSecret(), props.getWebhook().getTolerance(), store);
    }

    @Bean
    @ConditionalOnMissingBean
    public ResilientClients resilientClients(SecureKitProperties props) {
        SecureKitProperties.Resilient r = props.getResilientClient();
        return new ResilientClients(r.getMaxRetries(), r.getRetryRatio(), r.getMinRetriesPerMinute(),
                r.getFailureThreshold(), r.getOpenFor(), r.getTimeout());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = P + ".field-crypto", name = "enabled", havingValue = "true")
    public EncryptedFields encryptedFields(SecureKitProperties props) {
        SecureKitProperties.FieldCrypto f = props.getFieldCrypto();
        if (f.getKey() == null || f.getIndexKey() == null) {
            throw new IllegalStateException("secure-kit.field-crypto.key·index-key가 필요하다");
        }
        return new EncryptedFields(new FieldCipher(FieldCipher.keyFrom(f.getKey()), FieldCipher.keyFrom(f.getIndexKey())));
    }
}
