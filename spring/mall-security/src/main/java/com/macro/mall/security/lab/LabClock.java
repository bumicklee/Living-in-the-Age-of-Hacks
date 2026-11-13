package com.macro.mall.security.lab;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * [랩 도구 A13] 시간 건너뛰기. 시험이 「16분 뒤」를 기다리지 않고 확인할 수 있게, 앱이 보는 시각을 앞으로 민다.
 *
 * <pre>
 * long now = LabClock.nowMillis();   // System.currentTimeMillis() 대신
 * </pre>
 *
 * - 켜는 법: 컨테이너 환경 변수 DAON_LAB_CLOCK=true(infra/compose.lab.yml이 넣는다). 꺼져 있으면 언제나 실제 시각이다.
 * - 미는 값: Redis 키 lab:clock:offset(밀리초). 시험 도우미 replay/_auth.py의 skip_minutes()가 쓰고 지운다.
 * - Redis에 묻는 것은 1초에 한 번뿐이다. 값을 못 읽으면 0(실제 시각)으로 본다.
 * - 회사 서비스로 옮길 때는 이 클래스를 지우고 System.currentTimeMillis()(또는 java.time.Clock 주입)로 바꾼다.
 */
@Component
public class LabClock {
    private static final Logger LOG = LoggerFactory.getLogger("DAON-LAB");
    public static final String KEY = "lab:clock:offset";
    private static final boolean ENABLED = "true".equalsIgnoreCase(System.getenv("DAON_LAB_CLOCK"));

    private static volatile StringRedisTemplate redis;
    private static volatile long cachedOffset;
    private static volatile long cachedAt;

    @Autowired(required = false)
    public void setRedis(StringRedisTemplate template) {
        redis = template;
        if (ENABLED) {
            LOG.info("[랩 도구] 시간 건너뛰기 켜짐(Redis {}). 운영에는 없는 장치입니다", KEY);
        }
    }

    public static long nowMillis() {
        return System.currentTimeMillis() + offset();
    }

    public static long nowSeconds() {
        return nowMillis() / 1000L;
    }

    private static long offset() {
        if (!ENABLED || redis == null) {
            return 0L;
        }
        long real = System.currentTimeMillis();
        if (real - cachedAt < 1000L) {
            return cachedOffset;
        }
        long value = 0L;
        try {
            String v = redis.opsForValue().get(KEY);
            if (v != null && !v.isBlank()) {
                value = Long.parseLong(v.trim());
            }
        } catch (RuntimeException e) {
            value = 0L;
        }
        cachedOffset = value;
        cachedAt = real;
        return value;
    }
}
