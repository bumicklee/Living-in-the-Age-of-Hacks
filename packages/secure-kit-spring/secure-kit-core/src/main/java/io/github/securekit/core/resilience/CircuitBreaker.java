package io.github.securekit.core.resilience;

/**
 * 서킷 브레이커: 연속 실패가 기준(기본 5번)에 닿으면 정해진 시간(기본 20초) 동안 부르지 않는다.
 * 시간이 지나면 한 번만 시험 삼아 부르고(half-open), 성공하면 닫고 실패하면 다시 연다.
 */
public class CircuitBreaker {
    private final int threshold;
    private final long openMillis;
    private int failures;
    private long openedAt = -1;
    private boolean trial;

    public CircuitBreaker(int threshold, long openMillis) {
        this.threshold = threshold;
        this.openMillis = openMillis;
    }

    /** 지금 불러도 되는가 */
    public synchronized boolean allow(long now) {
        if (openedAt < 0) return true;
        if (now - openedAt >= openMillis && !trial) {
            trial = true;          // 한 번만 시험
            return true;
        }
        return false;
    }

    public synchronized void success() {
        failures = 0;
        openedAt = -1;
        trial = false;
    }

    /** 실패를 센다. 이번 실패로 **새로** 열렸으면 true(이벤트는 처음 열릴 때 한 번) */
    public synchronized boolean failure(long now) {
        failures++;
        if (trial) {
            trial = false;
            openedAt = now;
            return false;
        }
        if (openedAt < 0 && failures >= threshold) {
            openedAt = now;
            return true;
        }
        return false;
    }

    public synchronized boolean isOpen(long now) {
        return openedAt >= 0 && !(now - openedAt >= openMillis && !trial);
    }

    public synchronized void reset() {
        failures = 0;
        openedAt = -1;
        trial = false;
    }
}
