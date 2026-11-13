package io.github.securekit.web.part;

import io.github.securekit.core.KitResettable;
import io.github.securekit.core.SecureKitException;
import io.github.securekit.core.event.SecurityEvents;
import io.github.securekit.core.resilience.CircuitBreaker;
import io.github.securekit.core.resilience.RetryBudget;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 재시도 폭주 막기(9-5). 설정 secure-kit.resilient-client.
 *
 * - 요청마다 재시도는 최대 1번(기본). 그것도 재시도 예산(최근 1분 새 요청의 10%, 최소 3번) 안에서만.
 * - 연속 실패 5번이면 서킷이 20초 열린다. 열려 있으면 부르지 않고 503 + X-Circuit: open.
 * - 처음 열릴 때 resilience.circuit.opened 한 줄. 서킷이 닫힌 채 실패하면 502.
 * - 의존성 하나에 이 클라이언트 하나를 둔다(서킷은 의존성마다 따로).
 */
public class ResilientClient implements KitResettable {
    private final String name;
    private final int maxRetries;
    private final CircuitBreaker circuit;
    private final RetryBudget budget;
    private final Duration timeout;
    private final HttpClient http;

    public ResilientClient(String name, int maxRetries, double retryRatio, int minRetries,
                           int failureThreshold, Duration openFor, Duration timeout) {
        this.name = name;
        this.maxRetries = maxRetries;
        this.circuit = new CircuitBreaker(failureThreshold, openFor.toMillis());
        this.budget = new RetryBudget(retryRatio, minRetries);
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    /** GET. 성공(5xx가 아님)하면 본문 */
    public String get(String url) {
        long now = System.currentTimeMillis();
        budget.onRequest(now);
        if (!circuit.allow(now)) throw open();
        for (int attempt = 0; ; attempt++) {
            String body = once(url);
            if (body != null) {
                circuit.success();
                return body;
            }
            long t = System.currentTimeMillis();
            if (circuit.failure(t)) {
                SecurityEvents.warn("resilience.circuit.opened", "resilient-client", "dependency:" + name, "연속 실패로 서킷을 열었다");
            }
            if (circuit.isOpen(t)) throw open();
            if (attempt >= maxRetries || !budget.tryRetry(t)) {
                throw new SecureKitException(502, "연결된 서비스가 응답하지 않습니다");
            }
        }
    }

    /** 한 번 부른다. 실패(5xx·연결 오류·시간 초과)면 null */
    private String once(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(timeout).GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            return res.statusCode() >= 500 ? null : res.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static SecureKitException open() {
        return new SecureKitException(503, "잠시 후 다시 시도하세요").header("X-Circuit", "open");
    }

    @Override
    public void reset() {
        circuit.reset();
        budget.reset();
    }
}
