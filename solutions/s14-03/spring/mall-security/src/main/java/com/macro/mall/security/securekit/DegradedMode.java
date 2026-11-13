package com.macro.mall.security.securekit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * [다온마켓 14-3 정답] 축소 운영 스위치. 「결제가 아픈 동안 그 기능만 끈다」를 고객 앱과 관리자 앱이 같은 값으로 본다.
 *
 * <pre>
 * Redis  mall:mode:payment = normal | degraded     (A3 권한: 두 앱 모두 mall:* 만)
 * if (degradedMode.isOn("payment")) { ...결제는 큐에 받아 두고, 돈이 나가는 쪽(환불·정산)은 멈춘다... }
 * </pre>
 *
 * - 매 요청 읽는다(앱을 다시 띄우지 않고 바로 바뀐다). 켜고 끄는 일은 관리자 앱 POST /daon/mode/payment만 한다(13-6 권한 상승 뒤).
 * - Redis가 응답하지 않으면 「평소(normal)」로 본다: 이 스위치는 기능을 줄이는 쪽이라, 값을 못 읽었다고 멀쩡한 결제를 멈추지 않는다.
 *   (돈이 나가는 쪽의 안전장치는 승인 서비스의 출금 서킷 브레이커(13-4)가 따로 맡는다.)
 * - SecureKitSwitch.on("degraded-mode") 줄은 영상용 랩 스위치다(끄면 축소 운영도 벌크헤드도 없는 14-3 시작 모습).
 */
@Component
public class DegradedMode {
    private static final Logger LOG = LoggerFactory.getLogger(DegradedMode.class);
    public static final String KEY = "mall:mode:";
    public static final String PAYMENT = "payment";

    @Autowired
    private StringRedisTemplate redis;

    public boolean isOn(String feature) {
        if (!SecureKitSwitch.on("degraded-mode")) {
            return false;
        }
        try {
            return "degraded".equals(redis.opsForValue().get(KEY + feature));
        } catch (RuntimeException e) {
            LOG.warn("축소 운영 값을 읽지 못해 평소 모드로 봅니다: {}", e.getMessage());
            return false;
        }
    }

    public void set(String feature, boolean degraded) {
        redis.opsForValue().set(KEY + feature, degraded ? "degraded" : "normal");
    }
}
