package io.github.securekit.core.resilience;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 재시도 예산: 최근 1분 동안의 재시도 수를 새 요청 수의 일정 비율(기본 10%, 최소 3번)로 묶는다.
 * 의존성이 아플 때 재시도가 요청을 몇 배로 불려 더 아프게 만드는 것(재시도 폭주)을 막는다.
 */
public class RetryBudget {
    private static final long WINDOW = 60_000;
    private final double ratio;
    private final int minPerWindow;
    private final Deque<Long> requests = new ArrayDeque<>();
    private final Deque<Long> retries = new ArrayDeque<>();

    public RetryBudget(double ratio, int minPerWindow) {
        this.ratio = ratio;
        this.minPerWindow = minPerWindow;
    }

    public synchronized void onRequest(long now) {
        requests.addLast(now);
        trim(now);
    }

    /** 재시도해도 되면 true(그리고 한 번 쓴 것으로 센다) */
    public synchronized boolean tryRetry(long now) {
        trim(now);
        long allowed = Math.max(minPerWindow, (long) Math.floor(requests.size() * ratio));
        if (retries.size() >= allowed) return false;
        retries.addLast(now);
        return true;
    }

    public synchronized void reset() {
        requests.clear();
        retries.clear();
    }

    private void trim(long now) {
        while (!requests.isEmpty() && now - requests.peekFirst() > WINDOW) requests.removeFirst();
        while (!retries.isEmpty() && now - retries.peekFirst() > WINDOW) retries.removeFirst();
    }
}
