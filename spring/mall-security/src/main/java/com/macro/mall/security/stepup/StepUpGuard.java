package com.macro.mall.security.stepup;

import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.lab.LabClock;
import com.macro.mall.security.securekit.SecureKitSwitch;
import com.macro.mall.security.util.JwtTokenUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

/**
 * [다온마켓 4-7 정답] 고위험 작업 앞의 재인증 확인. 토큰의 auth_time(마지막으로 비밀번호를 넣은 시각)이 오래됐으면 401.
 *
 * <pre>
 * stepUpGuard.require(Duration.ofMinutes(5), "refund_account");   // 환불 계좌·배송지·이메일·비밀번호 변경 앞
 * </pre>
 *
 * - 「로그인했다」와 「방금 본인임을 확인했다」는 다르다. 로그인 토큰이 살아 있어도 5분이 지났으면 다시 묻는다.
 * - auth_time은 토큰 갱신(4-8) 때 새 시각으로 덮어쓰지 않고 그대로 옮긴다.
 * - SecureKitSwitch.on("step-up") 줄은 영상용 랩 스위치다. 손으로 고칠 때는 쓰지 않는다.
 */
@Component
public class StepUpGuard {
    @Autowired
    private JwtTokenUtil jwtTokenUtil;
    @Value("${jwt.tokenHeader}")
    private String tokenHeader;

    public void require(Duration maxAge, String action) {
        if (!SecureKitSwitch.on("step-up")) {
            return;
        }
        HttpServletRequest req = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String token = jwtTokenUtil.stripHead(req.getHeader(tokenHeader));
        long authTime = token == null ? 0 : jwtTokenUtil.getAuthTime(token);
        long age = LabClock.nowSeconds() - authTime;
        if (authTime <= 0 || age > maxAge.toSeconds()) {
            SecurityEventLogger.suspicious("auth.stepup.required", "4-7")
                    .target("action:" + action)
                    .reason("고위험 작업에 재인증 요구(마지막 인증 " + (authTime <= 0 ? "기록 없음" : age + "초 전") + ")")
                    .log();
            throw new StepUpRequiredException();
        }
    }
}
