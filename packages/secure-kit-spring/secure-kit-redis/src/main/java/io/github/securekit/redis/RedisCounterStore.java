package io.github.securekit.redis;

import io.github.securekit.core.StoreUnavailableException;
import io.github.securekit.core.event.SecurityEvents;
import io.github.securekit.core.store.CounterStore;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 여러 대가 함께 쓰는 Redis 저장소. 설정 secure-kit.store.type: redis.
 *
 * Redis에 닿지 않으면 기본은 막는다: 이벤트 store.fail_closed.unavailable + 503.
 * secure-kit.store.fail-open: true 를 명시하면 막지 않고 통과시킨다(속도 제한·예산이 잠시 꺼진다는 뜻이다).
 * 키는 모두 접두사(기본 secure-kit:)로 시작한다. 값에 개인정보 원문을 넣지 않는다(부품이 해시로 넘긴다).
 */
public class RedisCounterStore implements CounterStore {
    private final StringRedisTemplate redis;
    private final String prefix;
    private final boolean failOpen;

    public RedisCounterStore(StringRedisTemplate redis, String prefix, boolean failOpen) {
        this.redis = redis;
        this.prefix = prefix;
        this.failOpen = failOpen;
    }

    @Override
    public long increment(String key, Duration window) {
        return call(() -> {
            Long n = redis.opsForValue().increment(prefix + key);
            if (n != null && n == 1L) redis.expire(prefix + key, window);
            return n == null ? 0L : n;
        }, 0L);
    }

    @Override
    public long count(String key) {
        return call(() -> {
            String v = redis.opsForValue().get(prefix + key);
            return v == null ? 0L : Long.parseLong(v);
        }, 0L);
    }

    @Override
    public long ttlMillis(String key) {
        return call(() -> {
            Long ms = redis.getExpire(prefix + key, TimeUnit.MILLISECONDS);
            return ms == null || ms < 0 ? 0L : ms;
        }, 0L);
    }

    @Override
    public boolean putIfAbsent(String key, String value, Duration ttl) {
        return call(() -> Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(prefix + key, value, ttl)), true);
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        call(() -> {
            redis.opsForValue().set(prefix + key, value, ttl);
            return null;
        }, null);
    }

    @Override
    public String get(String key) {
        return call(() -> redis.opsForValue().get(prefix + key), null);
    }

    @Override
    public void delete(String key) {
        call(() -> redis.delete(prefix + key), null);
    }

    /** 랩 전용: 접두사로 시작하는 키만 지운다(KEYS 대신 SCAN) */
    @Override
    public void clear() {
        call(() -> {
            List<String> keys = new ArrayList<>();
            try (Cursor<String> c = redis.scan(ScanOptions.scanOptions().match(prefix + "*").count(500).build())) {
                c.forEachRemaining(keys::add);
            }
            if (!keys.isEmpty()) redis.delete(keys);
            return null;
        }, null);
    }

    private <T> T call(Supplier<T> op, T openValue) {
        try {
            return op.get();
        } catch (RuntimeException e) {
            if (failOpen) {
                SecurityEvents.info("store.fail_open.unavailable", "store", "store:redis", "Redis 오류 → 설정에 따라 통과(fail-open)");
                return openValue;
            }
            SecurityEvents.warn("store.fail_closed.unavailable", "store", "store:redis", "Redis 오류 → 막음(fail-closed)");
            throw new StoreUnavailableException();
        }
    }
}
