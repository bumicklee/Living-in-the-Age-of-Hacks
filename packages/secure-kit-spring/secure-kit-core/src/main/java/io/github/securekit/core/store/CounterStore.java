package io.github.securekit.core.store;

import java.time.Duration;

/**
 * 속도 제한·예산·멱등·재설정 토큰·토큰 폐기 목록이 함께 쓰는 작은 저장소.
 *
 * 한 대로 돌면 {@link InMemoryCounterStore}, 여러 대면 secure-kit-redis의 RedisCounterStore.
 * 저장소에 닿지 않으면 StoreUnavailableException(503)을 던진다(기본은 막기).
 */
public interface CounterStore {
    /** 창(window) 안의 횟수를 하나 올리고 올린 뒤 값을 돌려준다. 첫 증가 때 창이 시작된다 */
    long increment(String key, Duration window);

    /** 지금 창 안의 횟수(없으면 0) */
    long count(String key);

    /** 남은 수명(ms). 없으면 0 */
    long ttlMillis(String key);

    /** 없을 때만 넣는다. 넣었으면 true(멱등 키·재설정 토큰 선점) */
    boolean putIfAbsent(String key, String value, Duration ttl);

    void put(String key, String value, Duration ttl);

    /** 없거나 수명이 끝났으면 null */
    String get(String key);

    void delete(String key);

    /** 랩 전용: 모두 지운다(POST /_kit/reset) */
    void clear();
}
