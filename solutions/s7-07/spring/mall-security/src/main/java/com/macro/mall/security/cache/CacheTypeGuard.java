package com.macro.mall.security.cache;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.common.service.RedisService;
import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.securekit.SecureKitSwitch;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * [다온마켓 7-7] 캐시 값의 형식을 고정해서 읽는다.
 *
 * <ul>
 *   <li>쓸 때: 객체를 JSON 글자 하나로 만들어 둔다(글자 안에 클래스 이름이 없다).</li>
 *   <li>읽을 때: 그 글자를 <b>호출한 쪽이 정한 타입</b>으로만 만든다. 저장소의 글자가 타입을 고르지 못한다.</li>
 *   <li>글자가 아니거나(예전 형식·남이 쓴 값), 허용 목록 밖의 타입이거나, 정한 타입으로 읽히지 않으면 → 그 키를 지우고 null(= 캐시에 없음, DB에서 다시 읽는다).
 *       이때 deserialize.denied.type 을 남긴다.</li>
 * </ul>
 * 방어 스위치 secure-kit.cache-type-allowlist.enabled 를 끄면 옛 방식(객체를 그대로 저장·형 변환)으로 돌아간다. 영상·실습용이다.
 */
public final class CacheTypeGuard {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final Pattern TYPE_ID = Pattern.compile("type id '([^']{1,300})'");

    private CacheTypeGuard() {
    }

    public static void put(RedisService redis, String key, Object value, long seconds) {
        if (!SecureKitSwitch.on("cache-type-allowlist")) {
            redis.set(key, value, seconds);                  // 옛 방식(스위치를 끈 모습)
            return;
        }
        try {
            redis.set(key, MAPPER.writeValueAsString(value), seconds);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("캐시 값을 글자로 만들지 못했습니다", e);
        }
    }

    /** @param targetPrefix 이벤트 target에 넣을 키 이름 앞부분(예: mall:ums:admin:*). 값·사용자 이름은 넣지 않는다 */
    @SuppressWarnings("unchecked")
    public static <T> T get(RedisService redis, String key, String targetPrefix, TypeReference<T> type) {
        if (!SecureKitSwitch.on("cache-type-allowlist")) {
            return (T) redis.get(key);                       // 옛 방식: 읽은 객체를 그대로 형 변환
        }
        Object raw;
        try {
            raw = redis.get(key);
        } catch (RuntimeException e) {                       // 직렬화기가 허용 목록 밖 타입을 거부했다
            return denied(redis, key, targetPrefix, classFrom(e));
        }
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof String json)) {
            return denied(redis, key, targetPrefix, raw.getClass().getName());   // 글자가 아니다(예전 형식이거나 남이 쓴 값)
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            return denied(redis, key, targetPrefix, "unreadable");
        }
    }

    private static <T> T denied(RedisService redis, String key, String targetPrefix, String clazz) {
        SecurityEventLogger.denied("deserialize.denied.type", "7-7")
                .actor("system:redis-cache")
                .target(targetPrefix + " class:" + clean(clazz))
                .reason("캐시 값의 타입이 허용 목록 밖")
                .log();
        redis.del(key);
        return null;
    }

    /** 클래스 이름은 저장소에 쓴 사람이 정한 값이다. 120자로 자르고 영문·숫자·.$_ 만 남겨 로그 줄 위조를 막는다 */
    static String clean(String s) {
        String t = s == null ? "unknown" : s.replaceAll("[^A-Za-z0-9.$_]", "");
        return t.length() > 120 ? t.substring(0, 120) : t;
    }

    private static String classFrom(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            Matcher m = TYPE_ID.matcher(String.valueOf(t.getMessage()));
            if (m.find()) {
                return m.group(1);
            }
        }
        return "unknown";
    }
}
