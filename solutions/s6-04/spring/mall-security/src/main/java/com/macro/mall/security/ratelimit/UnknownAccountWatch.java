package com.macro.mall.security.ratelimit;

import com.macro.mall.security.event.SecurityEventLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * [다온마켓 4-4 정답] 없는 아이디로 온 로그인을 있는 아이디와 같은 시간이 걸리게 하고, 훑기를 센다.
 *
 * <pre>
 * } catch (UsernameNotFoundException e) {
 *     unknownAccountWatch.equalizeAndCount(password);   // 더미 해시와 한 번 비교 + 세기
 *     throw new BadCredentialsException(...);
 * }
 * </pre>
 *
 * - 더미 해시는 앱이 뜰 때 한 번만, 실제 회원과 같은 PasswordEncoder(같은 비용)로 만든다. 요청마다 만들면 더 느려져 다시 차이가 난다.
 * - auth.login.unknown_account_burst: 한 IP에서 10분 안에 없는 아이디로 10번째, 20번째… 시도가 오면 한 줄(회원 목록 훑기 의심).
 *   없는 아이디 하나하나는 남기지 않는다. 4-3의 failed_burst와 따로 센다.
 * - SecureKitSwitch.on("login-timing") 줄은 영상용 랩 스위치다. 손으로 고칠 때는 쓰지 않는다.
 */
@Component
public class UnknownAccountWatch {
    private static final Logger LOG = LoggerFactory.getLogger(UnknownAccountWatch.class);
    static final int BURST = 10;
    private static final DefaultRedisScript<Long> INCR = new DefaultRedisScript<>(
            "local n = redis.call('INCR', KEYS[1])\n" +
            "if n == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end\n" +
            "return n", Long.class);

    private final PasswordEncoder passwordEncoder;
    private final String dummyHash;

    @Autowired
    private StringRedisTemplate redis;

    public UnknownAccountWatch(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode("daon-dummy-" + System.nanoTime());
    }

    /** 없는 아이디일 때: 실제 회원과 같은 bcrypt 비교를 한 번 하고, IP별로 센다 */
    public void equalizeAndCount(String password) {
        if (!com.macro.mall.security.securekit.SecureKitSwitch.on("login-timing")) {
            return;
        }
        passwordEncoder.matches(password == null ? "" : password, dummyHash);
        String ip = "unknown";
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            ip = com.macro.mall.common.util.ClientIp.of(attrs.getRequest());
        }
        try {
            Long n = redis.execute(INCR, List.of("rl:login:unknown:ip:" + ip), String.valueOf(LoginRateLimiter.WINDOW_SECONDS));
            if (n != null && n >= BURST && n % BURST == 0) {
                SecurityEventLogger.denied("auth.login.unknown_account_burst", "4-4")
                        .actor("anonymous")
                        .target("ip:" + ip)
                        .reason("10분 안에 없는 아이디로 " + n + "번 로그인 시도")
                        .count(n.intValue())
                        .log();
            }
        } catch (RuntimeException e) {
            LOG.warn("없는 아이디 시도를 세지 못함: {}", e.getMessage());
        }
    }
}
