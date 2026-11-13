package com.macro.mall.portal.component;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * [다온마켓 9-5 정답] 재시도 예산: 최근 1분 동안 「새 요청 수 × 10%」(최소 3번)까지만 다시 보낸다.
 *
 * 요청마다 최대 횟수를 지켜도, 많은 요청이 한꺼번에 실패하면 재시도 전체 양이 커진다. 예산은 전체 비율에 상한을 둔다.
 * 예산이 바닥나면 다시 보내지 않고 DLQ로 보낸다(DaonPayRequestReceiver). Resilience4j에는 이 기능이 없어 손으로 쓴다.
 * 15장 secure-kit의 「재시도 폭주」 부품이 이 코드를 정리한 것이다. 앱 인스턴스 하나 기준(여러 대면 Redis 등 공용 저장소로).
 */
@Component
public class RetryBudget {
    static final double RATIO = 0.10;
    static final int MIN_PER_WINDOW = 3;
    static final long WINDOW_MS = 60_000;

    private final Deque<Long> requests = new ArrayDeque<>();
    private final Deque<Long> retries = new ArrayDeque<>();

    /** 새 요청(첫 시도) 하나 */
    public synchronized void recordRequest() {
        long now = System.currentTimeMillis();
        prune(now);
        requests.addLast(now);
    }

    /** 다시 보내도 되면 true(예산 하나 씀) */
    public synchronized boolean tryAcquireRetry() {
        long now = System.currentTimeMillis();
        prune(now);
        if (retries.size() >= allowed()) {
            return false;
        }
        retries.addLast(now);
        return true;
    }

    public synchronized int allowed() {
        return Math.max(MIN_PER_WINDOW, (int) Math.floor(requests.size() * RATIO));
    }

    public synchronized int used() {
        prune(System.currentTimeMillis());
        return retries.size();
    }

    private void prune(long now) {
        while (!requests.isEmpty() && now - requests.peekFirst() > WINDOW_MS) {
            requests.pollFirst();
        }
        while (!retries.isEmpty() && now - retries.peekFirst() > WINDOW_MS) {
            retries.pollFirst();
        }
    }
}
