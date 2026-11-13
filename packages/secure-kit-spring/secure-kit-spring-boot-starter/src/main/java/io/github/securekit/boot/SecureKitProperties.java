package io.github.securekit.boot;

import io.github.securekit.core.mask.Masking;
import io.github.securekit.core.password.PasswordPolicy;
import io.github.securekit.web.filter.SecurityHeadersFilter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * application.yml의 secure-kit.* (Express 설정 객체와 같은 키).
 *
 * 이름이 정해지지 않은 규칙(rate-limit.&lt;이름&gt;, budget.&lt;이름&gt;, approval.policies.&lt;정책&gt;)은
 * 여기 두지 않고 각 애스펙트가 Environment에서 그 이름으로 읽는다.
 */
@ConfigurationProperties("secure-kit")
public class SecureKitProperties {
    private final Toggle lab = new Toggle(false);
    private final HostAllowlist hostAllowlist = new HostAllowlist();
    private final Headers headers = new Headers();
    private final Cors cors = new Cors();
    private final Toggle errorResponse = new Toggle(false);
    private final Toggle rateLimit = new Toggle(true);
    private final Password passwordPolicy = new Password();
    private final ResetLink resetLink = new ResetLink();
    private final Toggle stepUp = new Toggle(true);
    private final Token token = new Token();
    private final FieldCrypto fieldCrypto = new FieldCrypto();
    private final Audit audit = new Audit();
    private final LogMasking logMasking = new LogMasking();
    private final Limits limits = new Limits();
    private final Upload upload = new Upload();
    private final SafeRedirect safeRedirect = new SafeRedirect();
    private final SafeHttpClient safeHttpClient = new SafeHttpClient();
    private final Resilient resilientClient = new Resilient();
    private final Toggle idempotency = new Toggle(true);
    private final Webhook webhook = new Webhook();
    private final Toggle budget = new Toggle(true);
    private final CacheControl cacheControl = new CacheControl();
    private final Toggle partnerScope = new Toggle(true);
    private final Toggle approval = new Toggle(true);
    private final Toggle failClosed = new Toggle(true);
    private final Store store = new Store();

    public Toggle getLab() { return lab; }
    public HostAllowlist getHostAllowlist() { return hostAllowlist; }
    public Headers getHeaders() { return headers; }
    public Cors getCors() { return cors; }
    public Toggle getErrorResponse() { return errorResponse; }
    public Toggle getRateLimit() { return rateLimit; }
    public Password getPasswordPolicy() { return passwordPolicy; }
    public ResetLink getResetLink() { return resetLink; }
    public Toggle getStepUp() { return stepUp; }
    public Token getToken() { return token; }
    public FieldCrypto getFieldCrypto() { return fieldCrypto; }
    public Audit getAudit() { return audit; }
    public LogMasking getLogMasking() { return logMasking; }
    public Limits getLimits() { return limits; }
    public Upload getUpload() { return upload; }
    public SafeRedirect getSafeRedirect() { return safeRedirect; }
    public SafeHttpClient getSafeHttpClient() { return safeHttpClient; }
    public Resilient getResilientClient() { return resilientClient; }
    public Toggle getIdempotency() { return idempotency; }
    public Webhook getWebhook() { return webhook; }
    public Toggle getBudget() { return budget; }
    public CacheControl getCacheControl() { return cacheControl; }
    public Toggle getPartnerScope() { return partnerScope; }
    public Toggle getApproval() { return approval; }
    public Toggle getFailClosed() { return failClosed; }
    public Store getStore() { return store; }

    /** enabled 하나만 있는 부품. lab은 expose도 같은 뜻으로 받는다 */
    public static class Toggle {
        private boolean enabled;

        public Toggle() {
        }

        Toggle(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isExpose() { return enabled; }
        public void setExpose(boolean expose) { this.enabled = expose; }
    }

    public static class HostAllowlist {
        private boolean enabled;
        private List<String> hosts = new ArrayList<>(List.of("localhost", "127.0.0.1"));
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public List<String> getHosts() { return hosts; }
        public void setHosts(List<String> hosts) { this.hosts = hosts; }
    }

    public static class Headers {
        private boolean enabled;
        private String csp = SecurityHeadersFilter.DEFAULT_CSP;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getCsp() { return csp; }
        public void setCsp(String csp) { this.csp = csp; }
    }

    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>();
        private List<String> allowedMethods = new ArrayList<>(List.of("GET", "POST", "PUT", "DELETE"));
        private List<String> allowedHeaders = new ArrayList<>(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        public List<String> getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(List<String> v) { this.allowedOrigins = v; }
        public List<String> getAllowedMethods() { return allowedMethods; }
        public void setAllowedMethods(List<String> v) { this.allowedMethods = v; }
        public List<String> getAllowedHeaders() { return allowedHeaders; }
        public void setAllowedHeaders(List<String> v) { this.allowedHeaders = v; }
    }

    public static class Password {
        private int minLength = 10;
        private List<String> breached = new ArrayList<>(PasswordPolicy.SAMPLE_BREACHED);
        private int iterations = 100_000;
        public int getMinLength() { return minLength; }
        public void setMinLength(int v) { this.minLength = v; }
        public List<String> getBreached() { return breached; }
        public void setBreached(List<String> v) { this.breached = v; }
        public int getIterations() { return iterations; }
        public void setIterations(int v) { this.iterations = v; }
    }

    public static class ResetLink {
        private String baseUrl;
        private Duration ttl = Duration.ofMinutes(15);
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String v) { this.baseUrl = v; }
        public Duration getTtl() { return ttl; }
        public void setTtl(Duration v) { this.ttl = v; }
    }

    public static class Token {
        private boolean enabled;
        private String secret;
        private String kid = "k1";
        private Duration ttl = Duration.ofMinutes(15);
        private String audience = "kit";
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public String getSecret() { return secret; }
        public void setSecret(String v) { this.secret = v; }
        public String getKid() { return kid; }
        public void setKid(String v) { this.kid = v; }
        public Duration getTtl() { return ttl; }
        public void setTtl(Duration v) { this.ttl = v; }
        public String getAudience() { return audience; }
        public void setAudience(String v) { this.audience = v; }
    }

    public static class FieldCrypto {
        private boolean enabled;
        private String key;
        private String indexKey;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public String getKey() { return key; }
        public void setKey(String v) { this.key = v; }
        public String getIndexKey() { return indexKey; }
        public void setIndexKey(String v) { this.indexKey = v; }
    }

    public static class Audit {
        private boolean enabled = true;
        private String reasonHeader = "X-Access-Reason";
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public String getReasonHeader() { return reasonHeader; }
        public void setReasonHeader(String v) { this.reasonHeader = v; }
    }

    public static class LogMasking {
        private boolean enabled = true;
        private List<String> fields = new ArrayList<>(Masking.DEFAULT_FIELDS);
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public List<String> getFields() { return fields; }
        public void setFields(List<String> v) { this.fields = v; }
    }

    public static class Limits {
        private boolean enabled;
        private DataSize maxBody = DataSize.ofMegabytes(1);
        /** 경로별 상한. yml에서는 "[/kit/upload]": 3MB 처럼 대괄호로 감싼다 */
        private Map<String, DataSize> paths = new LinkedHashMap<>();
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public DataSize getMaxBody() { return maxBody; }
        public void setMaxBody(DataSize v) { this.maxBody = v; }
        public Map<String, DataSize> getPaths() { return paths; }
        public void setPaths(Map<String, DataSize> v) { this.paths = v; }
    }

    public static class Upload {
        private DataSize maxSize = DataSize.ofMegabytes(2);
        private String dir = System.getProperty("java.io.tmpdir") + "/secure-kit-uploads";
        public DataSize getMaxSize() { return maxSize; }
        public void setMaxSize(DataSize v) { this.maxSize = v; }
        public String getDir() { return dir; }
        public void setDir(String v) { this.dir = v; }
    }

    public static class SafeRedirect {
        private List<String> allowedHosts = new ArrayList<>();
        public List<String> getAllowedHosts() { return allowedHosts; }
        public void setAllowedHosts(List<String> v) { this.allowedHosts = v; }
    }

    public static class SafeHttpClient {
        private Duration timeout = Duration.ofSeconds(3);
        private DataSize maxBody = DataSize.ofMegabytes(1);
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration v) { this.timeout = v; }
        public DataSize getMaxBody() { return maxBody; }
        public void setMaxBody(DataSize v) { this.maxBody = v; }
    }

    public static class Resilient {
        private int maxRetries = 1;
        private double retryRatio = 0.1;
        private int minRetriesPerMinute = 3;
        private int failureThreshold = 5;
        private Duration openFor = Duration.ofSeconds(20);
        private Duration timeout = Duration.ofSeconds(2);
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int v) { this.maxRetries = v; }
        public double getRetryRatio() { return retryRatio; }
        public void setRetryRatio(double v) { this.retryRatio = v; }
        public int getMinRetriesPerMinute() { return minRetriesPerMinute; }
        public void setMinRetriesPerMinute(int v) { this.minRetriesPerMinute = v; }
        public int getFailureThreshold() { return failureThreshold; }
        public void setFailureThreshold(int v) { this.failureThreshold = v; }
        public Duration getOpenFor() { return openFor; }
        public void setOpenFor(Duration v) { this.openFor = v; }
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration v) { this.timeout = v; }
    }

    public static class Webhook {
        private String secret;
        private Duration tolerance = Duration.ofMinutes(5);
        public String getSecret() { return secret; }
        public void setSecret(String v) { this.secret = v; }
        public Duration getTolerance() { return tolerance; }
        public void setTolerance(Duration v) { this.tolerance = v; }
    }

    public static class CacheControl {
        private boolean enabled;
        private List<String> sessionCookies = new ArrayList<>(List.of("JSESSIONID"));
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public List<String> getSessionCookies() { return sessionCookies; }
        public void setSessionCookies(List<String> v) { this.sessionCookies = v; }
    }

    public static class Store {
        /** memory(한 대) · redis(여러 대) */
        private String type = "memory";
        private boolean failOpen;
        private String prefix = "secure-kit:";
        public String getType() { return type; }
        public void setType(String v) { this.type = v; }
        public boolean isFailOpen() { return failOpen; }
        public void setFailOpen(boolean v) { this.failOpen = v; }
        public String getPrefix() { return prefix; }
        public void setPrefix(String v) { this.prefix = v; }
    }
}
