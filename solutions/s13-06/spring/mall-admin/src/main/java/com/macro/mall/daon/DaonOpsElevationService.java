package com.macro.mall.daon;

import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.lab.LabClock;
import com.macro.mall.security.securekit.OpsElevationGate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * [다온마켓 13-6 정답] 시간 제한 권한 상승(JIT). 승인 서비스(A10)가 서명한 권한 상승 토큰을 확인한 뒤에만 켠다.
 *
 * activate(username, token): ⓐ 서명(transit/verify/daon-approval, 13-3과 같은 확인) ⓑ kind=ops-elevation, sub = 지금 로그인한 사람
 *                            ⓒ 만료 전 → Redis mall:ops:elevation:<사람>:<묶음> = 만료 시각(초). ops.elevation.granted(INFO)
 * isActive(username, group):  저장한 만료 시각이 랩 시계(LabClock)보다 뒤인가. 시험은 시계를 31분 밀어 만료를 본다
 *
 * 사유는 승인 서비스 표(감사용)에만 있다. 보안 이벤트 reason은 「승인된 권한 상승」으로만.
 */
@Component
public class DaonOpsElevationService implements OpsElevationGate {
    private static final String KEY = "mall:ops:elevation:";

    @Autowired
    private DaonApprovalToken tokens;
    @Autowired
    private StringRedisTemplate redis;

    @Override
    public boolean isActive(String username, String group) {
        String v = redis.opsForValue().get(KEY + username + ":" + group);
        try {
            return v != null && Long.parseLong(v) > LabClock.nowSeconds();
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** 켰으면 만료 시각(초), 아니면 null */
    public Long activate(String username, String token) {
        Map<String, Object> p = tokens.verify(token, "ops-elevation");
        if (p == null || !username.equals(p.get("sub"))) {
            SecurityEventLogger.denied("ops.denied.no_elevation", "13-6").actor("admin:" + username).target("elevation:token")
                    .reason("권한 상승 토큰이 없거나 서명·주인이 맞지 않음").log();
            return null;
        }
        long exp = p.get("exp") instanceof Number n ? n.longValue() : 0L;
        long left = exp - LabClock.nowSeconds();
        if (left <= 0) {
            return null;
        }
        String group = String.valueOf(p.get("group"));
        redis.opsForValue().set(KEY + username + ":" + group, String.valueOf(exp), Duration.ofSeconds(left + 3600));
        SecurityEventLogger.suspicious("ops.elevation.granted", "13-6").actor("admin:" + username)
                .target("approval:" + p.get("approval")).reason("승인된 권한 상승(" + group + ", 승인 " + p.get("approved_by") + ")").log();
        return exp;
    }
}
