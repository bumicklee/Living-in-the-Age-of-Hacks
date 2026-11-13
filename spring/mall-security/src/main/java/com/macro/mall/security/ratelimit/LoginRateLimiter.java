package com.macro.mall.security.ratelimit;

import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.securekit.SecureKitSwitch;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;

/**
 * [다온마켓 4-3 정답] 로그인 실패 제한. 계정·IP·기기 세 축을 Redis 카운터로 센다(고정 창 10분).
 *
 * <pre>
 * limiter.check(username);              // 로그인 맨 앞: 한도를 넘었으면 LoginRateLimitedException(HTTP 429)
 * ...비밀번호 확인...
 * limiter.failed(username);             // 틀렸을 때
 * limiter.succeeded(username);          // 맞았을 때: 계정 키만 지운다(IP·기기 키는 남긴다)
 * </pre>
 *
 * - 키: rl:login:acct:&lt;이름&gt;(5회) · rl:login:ip:&lt;주소&gt;(50회) · rl:login:dev:&lt;daon_did&gt;(20회). A3의 Redis 사용자 권한은 rl:* 만.
 * - INCR와 첫 EXPIRE를 Lua 한 덩어리로 묶는다. 따로 부르면 그 사이에 앱이 죽었을 때 만료 없는 키가 남는다.
 * - IP는 ClientIp가 정한다(6-4): 기본은 소켓 주소, 믿는 프록시(게이트웨이)가 넣은 X-Forwarded-For의 마지막 값만 예외. 그 밖의 X-Forwarded-For는 믿지 않는다.
 * - Redis가 응답하지 않으면 막는다(fail-closed). 대신 auth.ratelimit.store_unavailable을 남긴다.
 * - 문구는 하나: 「잠시 후 다시 시도해 주세요」. 계정이 있는지 없는지를 이 문구로 드러내지 않는다(4-4).
 * - SecureKitSwitch.on("login-ratelimit") 줄은 영상용 랩 스위치다. 손으로 고칠 때는 쓰지 않는다.
 */
@Component
public class LoginRateLimiter {
    private static final Logger LOG = LoggerFactory.getLogger(LoginRateLimiter.class);
    private static final String LESSON = "4-3";
    static final long WINDOW_SECONDS = 600;
    static final int ACCOUNT_LIMIT = 5;
    static final int IP_LIMIT = 50;
    static final int DEVICE_LIMIT = 20;
    /** 한 IP·기기에서 10분 안에 서로 다른 계정이 이만큼 실패하면 auth.login.failed_burst */
    static final int BURST_ACCOUNTS = 10;
    public static final String MESSAGE = "잠시 후 다시 시도해 주세요";

    /** KEYS = 카운터 키들, ARGV[1] = 창(초). 각 키를 1 올리고, 처음 생긴 키에만 만료를 건다. 올린 뒤 값들을 돌려준다 */
    private static final DefaultRedisScript<List> INCR_ALL = new DefaultRedisScript<>(
            "local out = {}\n" +
            "for i, k in ipairs(KEYS) do\n" +
            "  local n = redis.call('INCR', k)\n" +
            "  if n == 1 then redis.call('EXPIRE', k, ARGV[1]) end\n" +
            "  out[i] = n\n" +
            "end\n" +
            "return out", List.class);

    /** KEYS[1] = 실패 계정 모음(set), ARGV[1] = 계정, ARGV[2] = 창(초). 넣은 뒤 서로 다른 계정 수를 돌려준다 */
    private static final DefaultRedisScript<Long> ADD_ACCOUNT = new DefaultRedisScript<>(
            "local added = redis.call('SADD', KEYS[1], ARGV[1])\n" +
            "if redis.call('TTL', KEYS[1]) < 0 then redis.call('EXPIRE', KEYS[1], ARGV[2]) end\n" +
            "return redis.call('SCARD', KEYS[1])", Long.class);

    @Autowired
    private StringRedisTemplate redis;

    /** 로그인 맨 앞에서 부른다. 한도를 넘었거나 Redis가 응답하지 않으면 LoginRateLimitedException */
    public void check(String username) {
        if (!SecureKitSwitch.on("login-ratelimit")) {
            return;
        }
        Axes axes = Axes.current(username);
        List<String> keys = axes.keys();
        List<String> values;
        try {
            values = redis.opsForValue().multiGet(keys);
        } catch (RuntimeException e) {
            storeUnavailable(axes, e);
            throw new LoginRateLimitedException(WINDOW_SECONDS);
        }
        int[] limits = axes.limits();
        for (int i = 0; i < keys.size(); i++) {
            String v = values == null ? null : values.get(i);
            if (v != null && Long.parseLong(v) >= limits[i]) {
                long retry = retryAfter(keys.get(i));
                SecurityEventLogger.denied("auth.login.rate_limited", LESSON)
                        .actor("anonymous")
                        .target(axes.targetFor(i))
                        .reason("한도 초과: " + axes.axisName(i))
                        .log();
                throw new LoginRateLimitedException(retry);
            }
        }
    }

    /** 비밀번호가 틀렸거나 없는 계정일 때 */
    public void failed(String username) {
        if (!SecureKitSwitch.on("login-ratelimit")) {
            return;
        }
        Axes axes = Axes.current(username);
        try {
            redis.execute(INCR_ALL, axes.keys(), String.valueOf(WINDOW_SECONDS));
            noteBurst(axes, "ip", axes.ip);
            if (axes.device != null) {
                noteBurst(axes, "dev", axes.device);
            }
        } catch (RuntimeException e) {
            storeUnavailable(axes, e);
        }
    }

    /** 로그인에 성공했을 때: 계정 키만 지운다. IP 키까지 지우면 공격자가 자기 계정으로 한 번 성공해 카운터를 비울 수 있다 */
    public void succeeded(String username) {
        if (!SecureKitSwitch.on("login-ratelimit")) {
            return;
        }
        try {
            redis.delete(Axes.accountKey(username));
        } catch (RuntimeException e) {
            LOG.warn("로그인 성공 뒤 계정 카운터를 지우지 못함: {}", e.getMessage());
        }
    }

    private void noteBurst(Axes axes, String axis, String value) {
        String setKey = "rl:login:burst:" + axis + ":" + value;
        Long distinct = redis.execute(ADD_ACCOUNT, List.of(setKey), axes.username, String.valueOf(WINDOW_SECONDS));
        if (distinct != null && distinct >= BURST_ACCOUNTS && distinct % BURST_ACCOUNTS == 0) {
            SecurityEventLogger.denied("auth.login.failed_burst", LESSON)
                    .actor("anonymous")
                    .target(axis + ":" + value)
                    .reason("10분 안에 서로 다른 계정 " + distinct + "개 실패")
                    .count(distinct.intValue())
                    .log();
        }
    }

    private long retryAfter(String key) {
        try {
            Long ttl = redis.getExpire(key);
            return ttl != null && ttl > 0 ? ttl : WINDOW_SECONDS;
        } catch (RuntimeException e) {
            return WINDOW_SECONDS;
        }
    }

    private void storeUnavailable(Axes axes, RuntimeException e) {
        LOG.warn("속도 제한 저장소 응답 없음, 로그인을 막습니다: {}", e.getMessage());
        SecurityEventLogger.denied("auth.ratelimit.store_unavailable", LESSON)
                .actor("anonymous")
                .target("ip:" + axes.ip)
                .reason("Redis 응답 없음, 로그인 막음")
                .log();
    }

    /** 요청 하나의 세 축 값 */
    private static final class Axes {
        final String username;
        final String ip;
        final String device;

        private Axes(String username, String ip, String device) {
            this.username = username;
            this.ip = ip;
            this.device = device;
        }

        static Axes current(String username) {
            String ip = "unknown";
            String device = null;
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                HttpServletRequest req = attrs.getRequest();
                ip = com.macro.mall.common.util.ClientIp.of(req);   // 6-4(E09): 믿는 프록시가 넣은 X-Forwarded-For의 마지막 값, 아니면 소켓 주소
                if (req.getCookies() != null) {
                    for (Cookie c : req.getCookies()) {
                        if ("daon_did".equals(c.getName()) && c.getValue() != null && c.getValue().matches("[A-Za-z0-9_-]{8,64}")) {
                            device = c.getValue();
                        }
                    }
                }
            }
            return new Axes(username == null ? "" : username, ip, device);
        }

        static String accountKey(String username) {
            return "rl:login:acct:" + (username == null ? "" : username);
        }

        List<String> keys() {
            List<String> k = new ArrayList<>(List.of(accountKey(username), "rl:login:ip:" + ip));
            if (device != null) {
                k.add("rl:login:dev:" + device);
            }
            return k;
        }

        int[] limits() {
            return device != null ? new int[]{ACCOUNT_LIMIT, IP_LIMIT, DEVICE_LIMIT} : new int[]{ACCOUNT_LIMIT, IP_LIMIT};
        }

        String axisName(int i) {
            return i == 0 ? "acct" : i == 1 ? "ip" : "dev";
        }

        String targetFor(int i) {
            return i == 0 ? "account" : i == 1 ? "ip:" + ip : "dev:" + device;
        }
    }
}
