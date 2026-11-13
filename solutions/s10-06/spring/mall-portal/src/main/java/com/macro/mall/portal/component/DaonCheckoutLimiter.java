package com.macro.mall.portal.component;

import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.securekit.SecureKitSwitch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * [다온마켓 10-6 정답] 결제 시도를 「요청 하나」가 아니라 「흐름 전체」로 센다(T27 카드 테스팅). 4-3 로그인 제한과 같은 Redis 카운터(rl:*).
 *
 * 세 축(랩 값)
 *   계정    rl:checkout:acct:&lt;회원&gt;   10분에 5번
 *   IP      rl:checkout:ip:&lt;주소&gt;     10분에 20번
 *   카드 지문 rl:checkout:card:&lt;지문&gt;  하루에 서로 다른 계정 3개까지(카드 번호는 받지도 남기지도 않는다. 지문만)
 * 카드 지문은 결제사의 토큰 지문 조회를 흉내 내 「fp_ + 토큰 SHA-256 앞 16자」로 만든다(가짜 결제사와 같은 규칙, stubs/README.md).
 *
 * 실패율: 10분 창마다 결제 시도·거절을 세고, 시도 10번 이상에서 거절이 절반을 넘으면 payment.suspected.card_testing 한 번(창마다 한 번).
 * 기준선은 랩에서는 고정값이다. 운영에서는 시간대별 기준선을 쓴다(세일 날마다 울리지 않게).
 * Redis가 응답하지 않으면 막는다(fail-closed, 9-2).
 */
@Component
public class DaonCheckoutLimiter {
    static final long WINDOW = 600;
    static final long DAY = 86400;
    static final int ACCOUNT_LIMIT = 5;
    static final int IP_LIMIT = 20;
    static final int CARD_ACCOUNTS = 3;
    static final int RATE_MIN_TRIES = 10;
    static final double RATE_FAIL = 0.5;

    /** KEYS[1] = 카운터, ARGV[1] = 창(초). 올린 값을 돌려준다(처음 생긴 키에만 만료) */
    private static final DefaultRedisScript<Long> INCR = new DefaultRedisScript<>(
            "local n = redis.call('INCR', KEYS[1])\n" +
            "if n == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end\n" +
            "return n", Long.class);
    /** KEYS[1] = 카드 지문의 계정 모음, ARGV[1] = 계정, ARGV[2] = 창. 이 계정이 이미 있으면 0, 새로 넣었으면 넣은 뒤 개수 */
    private static final DefaultRedisScript<Long> ADD_ACCOUNT = new DefaultRedisScript<>(
            "if redis.call('SISMEMBER', KEYS[1], ARGV[1]) == 1 then return 0 end\n" +
            "local n = redis.call('SCARD', KEYS[1])\n" +
            "if n >= tonumber(ARGV[3]) then return -1 end\n" +
            "redis.call('SADD', KEYS[1], ARGV[1])\n" +
            "if redis.call('TTL', KEYS[1]) < 0 then redis.call('EXPIRE', KEYS[1], ARGV[2]) end\n" +
            "return n + 1", Long.class);

    @Autowired
    private StringRedisTemplate redis;

    /** 한도를 넘었으면 넘은 축 이름(계정·IP·카드 지문), 아니면 null. 이벤트는 여기서 남긴다 */
    public String check(Long memberId, String ip, String cardToken) {
        if (!SecureKitSwitch.on("checkout-limits")) {
            return null;
        }
        try {
            if (incr("rl:checkout:acct:" + memberId, WINDOW) > ACCOUNT_LIMIT) {
                return deny(memberId, "계정");
            }
            if (incr("rl:checkout:ip:" + ip, WINDOW) > IP_LIMIT) {
                return deny(memberId, "IP");
            }
            if (cardToken != null && !cardToken.isBlank()) {
                Long n = redis.execute(ADD_ACCOUNT, List.of("rl:checkout:card:" + fingerprint(cardToken)),
                        "member:" + memberId, String.valueOf(DAY), String.valueOf(CARD_ACCOUNTS));
                if (n != null && n < 0) {
                    return deny(memberId, "카드 지문");
                }
            }
            return null;
        } catch (RuntimeException e) {
            SecurityEventLogger.denied("checkout.denied.rate_limit", "10-6").actor("member:" + memberId)
                    .target("path:/pay/start").reason("카운터 저장소 응답 없음(막음)").log();
            return "저장소";
        }
    }

    /** 결제사 결과 하나를 실패율 창에 더한다. 기준을 넘으면 창마다 한 번 payment.suspected.card_testing */
    public void record(boolean declined) {
        if (!SecureKitSwitch.on("checkout-limits")) {
            return;
        }
        try {
            long bucket = System.currentTimeMillis() / 1000 / WINDOW;
            long tries = incr("rl:checkout:tries:" + bucket, WINDOW * 2);
            long fails = declined ? incr("rl:checkout:fails:" + bucket, WINDOW * 2) : parse(redis.opsForValue().get("rl:checkout:fails:" + bucket));
            if (tries >= RATE_MIN_TRIES && fails > tries * RATE_FAIL
                    && Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("rl:checkout:alerted:" + bucket, "1", java.time.Duration.ofSeconds(WINDOW * 2)))) {
                SecurityEventLogger.denied("payment.suspected.card_testing", "10-6")
                        .actor("system:checkout")
                        .target("path:/pay/start")
                        .reason("10분 결제 실패율 기준선 초과(시도 " + tries + "번 중 거절이 절반 넘음)")
                        .count((int) fails)
                        .log();
            }
        } catch (RuntimeException ignored) {
            // 실패율은 탐지용이다. 저장소 오류로 결제를 막지는 않는다(막는 것은 check)
        }
    }

    public static String fingerprint(String cardToken) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(cardToken.getBytes(StandardCharsets.UTF_8));
            return "fp_" + HexFormat.of().formatHex(h).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private long incr(String key, long window) {
        Long n = redis.execute(INCR, List.of(key), String.valueOf(window));
        return n == null ? 0 : n;
    }

    private static long parse(String v) {
        try {
            return v == null ? 0 : Long.parseLong(v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String deny(Long memberId, String axis) {
        SecurityEventLogger.denied("checkout.denied.rate_limit", "10-6")
                .actor("member:" + memberId)
                .target("path:/pay/start")
                .reason(axis + " 축 결제 시도 한도")
                .log();
        return axis;
    }
}
