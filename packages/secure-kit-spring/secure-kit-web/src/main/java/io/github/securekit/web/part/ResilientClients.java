package io.github.securekit.web.part;

import io.github.securekit.core.KitResettable;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 설정(secure-kit.resilient-client)의 기본값으로 의존성마다 ResilientClient를 만든다.
 * 서킷은 의존성마다 따로라서, 의존성 하나에 create()를 한 번 부르고 그 객체를 계속 쓴다.
 */
public class ResilientClients implements KitResettable {
    private final int maxRetries;
    private final double retryRatio;
    private final int minRetries;
    private final int failureThreshold;
    private final Duration openFor;
    private final Duration timeout;
    private final List<ResilientClient> created = new CopyOnWriteArrayList<>();

    public ResilientClients(int maxRetries, double retryRatio, int minRetries, int failureThreshold,
                            Duration openFor, Duration timeout) {
        this.maxRetries = maxRetries;
        this.retryRatio = retryRatio;
        this.minRetries = minRetries;
        this.failureThreshold = failureThreshold;
        this.openFor = openFor;
        this.timeout = timeout;
    }

    public ResilientClient create(String dependencyName) {
        ResilientClient c = new ResilientClient(dependencyName, maxRetries, retryRatio, minRetries, failureThreshold, openFor, timeout);
        created.add(c);
        return c;
    }

    @Override
    public void reset() {
        created.forEach(ResilientClient::reset);
    }
}
