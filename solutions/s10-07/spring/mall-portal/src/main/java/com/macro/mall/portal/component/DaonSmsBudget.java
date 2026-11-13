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
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

/**
 * [다온마켓 10-7 정답] 인증 문자 발송 예산(SMS 펌핑, T27). 10-미션에서 secure-kit의 @Budget으로 뽑는다.
 *
 * 세 겹(랩 값, Redis rl:sms:budget:*)
 *   번호  60초에 1번, 하루 5번
 *   IP    10분에 30번
 *   전체  한 시간에 20건. 넘으면 그 시간이 끝날 때까지 발송을 멈춘다(rl:sms:budget:tripped) + budget.tripped.global 한 번
 * 국가 제한: 국내 휴대전화 형식(01X-XXXX-XXXX)만. 그 밖은 문자 사업자를 부르기 전에 400.
 *
 * - 번호 원문은 로그에 넣지 않는다. target은 phone:&lt;SHA-256 앞 8자&gt;(8-5 규칙).
 * - 전체 상한은 「사고를 멈추는 스위치」다. 걸리면 정상 사용자도 못 받는다 → 사람이 본다(경보 P1).
 * - Redis가 응답하지 않으면 보내지 않는다(fail-closed, 9-2).
 */
@Component
public class DaonSmsBudget {
    static final Pattern KR_MOBILE = Pattern.compile("^01[016789]-?\\d{3,4}-?\\d{4}$");
    static final int PER_PHONE_DAY = 5;
    static final int PER_IP_10MIN = 30;           // 계획은 10. 재연이 한 IP(127.0.0.1)에서 오므로 전체 상한(20)이 먼저 보이게 30으로 둔다
    static final int GLOBAL_PER_HOUR = 20;
    static final String TRIPPED = "rl:sms:budget:tripped";

    public enum Verdict { OK, BAD_FORMAT, LIMITED, TRIPPED }

    private static final DefaultRedisScript<Long> INCR = new DefaultRedisScript<>(
            "local n = redis.call('INCR', KEYS[1])\n" +
            "if n == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end\n" +
            "return n", Long.class);

    @Autowired
    private StringRedisTemplate redis;

    public Verdict check(String telephone, String ip) {
        if (!SecureKitSwitch.on("sms-budget")) {
            return Verdict.OK;
        }
        String t = telephone == null ? "" : telephone.trim();
        String target = "phone:" + hash8(t);
        if (!KR_MOBILE.matcher(t).matches()) {
            deny(target, "허용하지 않는 번호 형식(국내 휴대전화만)");
            return Verdict.BAD_FORMAT;
        }
        String digits = t.replaceAll("\\D", "");
        try {
            if (Boolean.TRUE.equals(redis.hasKey(TRIPPED))) {
                deny(target, "전체 발송 상한으로 멈춘 시간");
                return Verdict.TRIPPED;
            }
            if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("rl:sms:budget:phone60:" + digits, "1", Duration.ofSeconds(60)))) {
                deny(target, "번호당 발송 한도(60초 1번)");
                return Verdict.LIMITED;
            }
            if (incr("rl:sms:budget:phoneday:" + digits, 86400) > PER_PHONE_DAY) {
                deny(target, "번호당 발송 한도(하루 " + PER_PHONE_DAY + "번)");
                return Verdict.LIMITED;
            }
            if (incr("rl:sms:budget:ip:" + ip, 600) > PER_IP_10MIN) {
                deny(target, "IP당 발송 한도(10분 " + PER_IP_10MIN + "번)");
                return Verdict.LIMITED;
            }
            LocalDateTime now = LocalDateTime.now();
            long hourLeft = Math.max(1, ChronoUnit.SECONDS.between(now, now.truncatedTo(ChronoUnit.HOURS).plusHours(1)));
            long n = incr("rl:sms:budget:global:" + now.truncatedTo(ChronoUnit.HOURS).toString(), 3600);
            if (n > GLOBAL_PER_HOUR) {
                if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(TRIPPED, "1", Duration.ofSeconds(hourLeft)))) {
                    SecurityEventLogger.denied("budget.tripped.global", "10-7")
                            .actor("system:sms")
                            .target("budget:sms")
                            .reason("전체 시간당 발송 상한(" + GLOBAL_PER_HOUR + "건) 도달, 이 시간 끝까지 발송 중지")
                            .log();
                }
                return Verdict.TRIPPED;
            }
            return Verdict.OK;
        } catch (RuntimeException e) {
            deny(target, "예산 저장소 응답 없음(보내지 않음)");
            return Verdict.TRIPPED;
        }
    }

    private long incr(String key, long seconds) {
        Long n = redis.execute(INCR, List.of(key), String.valueOf(seconds));
        return n == null ? 0 : n;
    }

    private static void deny(String target, String reason) {
        SecurityEventLogger.denied("budget.denied.sms", "10-7")
                .target(target)
                .reason(reason)
                .log();
    }

    static String hash8(String s) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h).substring(0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
