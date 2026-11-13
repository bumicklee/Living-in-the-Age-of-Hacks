package io.github.securekit.core.store;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 한 대(인스턴스 하나)용 메모리 저장소. 앱을 다시 띄우면 비워진다.
 * 여러 대로 늘리면 대마다 따로 세므로 Redis 저장소로 바꾼다.
 */
public class InMemoryCounterStore implements CounterStore {
    private static final class Entry {
        String value;
        long expiresAt;

        Entry(String value, long expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }
    }

    private final Map<String, Entry> map = new HashMap<>();

    private Entry live(String key, long now) {
        Entry e = map.get(key);
        if (e != null && e.expiresAt <= now) {
            map.remove(key);
            return null;
        }
        return e;
    }

    @Override
    public synchronized long increment(String key, Duration window) {
        long now = System.currentTimeMillis();
        Entry e = live(key, now);
        if (e == null) {
            map.put(key, new Entry("1", now + window.toMillis()));
            return 1;
        }
        long n = Long.parseLong(e.value) + 1;
        e.value = Long.toString(n);
        return n;
    }

    @Override
    public synchronized long count(String key) {
        Entry e = live(key, System.currentTimeMillis());
        return e == null ? 0 : Long.parseLong(e.value);
    }

    @Override
    public synchronized long ttlMillis(String key) {
        long now = System.currentTimeMillis();
        Entry e = live(key, now);
        return e == null ? 0 : e.expiresAt - now;
    }

    @Override
    public synchronized boolean putIfAbsent(String key, String value, Duration ttl) {
        long now = System.currentTimeMillis();
        if (live(key, now) != null) return false;
        map.put(key, new Entry(value, now + ttl.toMillis()));
        return true;
    }

    @Override
    public synchronized void put(String key, String value, Duration ttl) {
        map.put(key, new Entry(value, System.currentTimeMillis() + ttl.toMillis()));
    }

    @Override
    public synchronized String get(String key) {
        Entry e = live(key, System.currentTimeMillis());
        return e == null ? null : e.value;
    }

    @Override
    public synchronized void delete(String key) {
        map.remove(key);
    }

    @Override
    public synchronized void clear() {
        map.clear();
    }
}
