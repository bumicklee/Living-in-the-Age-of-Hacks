package com.macro.mall.security.util;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.token.JwtSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import com.macro.mall.security.lab.LabClock;
import com.macro.mall.security.securekit.SecureKitSwitch;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JwtToken생성된 유틸 클래스
 * JWT token형식:header.payload.signature
 * header형식(알고리즘, token 유형, 키 버전):
 * {"alg": "ES256","typ": "JWT","kid": "daon-jwt:v1"}   ✏️ 5-2: 서명은 키 금고(OpenBao)가 한다
 * payload형식(사용자 이름, 생성 시간, 발급 시간):
 * {"sub":"wang","created":1489079981393,"exp":1489684781}
 * signature생성 알고리즘:
 * ECDSA P-256 + SHA-256(JWS r||s 형식). 비밀 키는 앱 밖에 있고 앱은 공개키로 확인만 한다
 * Created by macro on 2018/4/26.
 * Refactored to use Hutool JWTUtil
 */
public class JwtTokenUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(JwtTokenUtil.class);
    private static final String CLAIM_KEY_USERNAME = "sub";
    private static final String CLAIM_KEY_CREATED = "created";
    /** 다온마켓 4-8: 액세스 토큰 수명(초). 손으로 고칠 때는 application.yml의 jwt.expiration을 900으로 바꾼다 */
    public static final long ACCESS_SECONDS = 900;
    /** 다온마켓 5-2: 서명은 금고(OpenBao)가, 확인은 공개키로. jwt.secret 설정은 없어졌다 */
    @Autowired
    private JwtSigner signer;
    @Value("${jwt.expiration}")
    private Long expiration;
    @Value("${jwt.tokenHead}")
    private String tokenHead;

    /**
     * JWT 생성을 담당하는token
     */
    private String generateToken(Map<String, Object> claims) {
        // 만료 시간 설정
        // 다온마켓 4-8: 수명 15분, exp는 초(JWT 표준), 토큰마다 jti(하나를 골라 끊을 수 있게)
        if (SecureKitSwitch.on("token-revoke")) {
            long nowSeconds = LabClock.nowSeconds();
            claims.put("exp", nowSeconds + Math.min(expiration, ACCESS_SECONDS));
            claims.put("jti", UUID.randomUUID().toString());
            return signer.sign(claims);
        }
        long expireTime = System.currentTimeMillis() + expiration * 1000;
        claims.put("exp", expireTime);
        return signer.sign(claims);
    }

    /**
     * token에서 JWT 페이로드 조회
     */
    private Map<String, Object> getPayloadFromToken(String token) {
        // 다온마켓 5-2: 검증 방식은 서버가 정한다(ES256 하나, 키는 kid로 고른 공개키, iss·aud 확인). 토큰 머리글이 정하지 않는다.
        // 로그에는 토큰을 남기지 않는다(거절 이유는 SECURITY 이벤트로 JwtSigner가 남긴다)
        try {
            return signer.verify(token);
        } catch (Exception e) {
            LOGGER.info("JWT 검증 중 오류(토큰은 남기지 않음): {}", e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * token에서 로그인 사용자 이름 조회
     */
    public String getUserNameFromToken(String token) {
        String username;
        try {
            Map<String, Object> payload = getPayloadFromToken(token);
            username = payload != null ? (String) payload.get(CLAIM_KEY_USERNAME) : null;
        } catch (Exception e) {
            username = null;
        }
        return username;
    }

    /**
     * token이 아직 유효한지 검증
     *
     * @param token       클라이언트가 전달한token
     * @param userDetails 데이터베이스에서 조회한 사용자 정보
     */
    public boolean validateToken(String token, UserDetails userDetails) {
        String username = getUserNameFromToken(token);
        return username != null && username.equals(userDetails.getUsername())
                && !isTokenExpired(token)
                && userDetails.isEnabled();
    }

    /**
     * token이 이미 만료되었는지 판단
     */
    private boolean isTokenExpired(String token) {
        try {
            // exp 필드를 직접 확인해 만료 여부 판단
            Map<String, Object> payload = getPayloadFromToken(token);
            if (payload == null) {
                return true;
            }
            Object exp = payload.get("exp");
            if (exp == null) {
                // 다온마켓 5-2: 만료 시각이 없는 토큰은 영원히 통한다 → 받지 않는다
                if (SecureKitSwitch.on("jwt-hardening")) {
                    SecurityEventLogger.denied("auth.token.denied_no_exp", "5-2").actor("anonymous").target("-")
                            .reason("exp 없는 토큰").log();
                    return true;
                }
                return false;
            }
            long expTime = exp instanceof Long ? (Long) exp : ((Number) exp).longValue();
            // 다온마켓 4-8: 초 단위 exp(새 토큰)와 밀리초 exp(예전 토큰)를 구별하고, 랩 시계로 비교한다(시험이 16분을 건너뛴다)
            long expMillis = expTime < 100_000_000_000L ? expTime * 1000 : expTime;
            return expMillis < LabClock.nowMillis();
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * token에서 만료 시간 조회
     */
    private Date getExpiredDateFromToken(String token) {
        Map<String, Object> payload = getPayloadFromToken(token);
        if (payload == null) {
            return null;
        }
        Object exp = payload.get("exp");
        if (exp instanceof Long) {
            return new Date((Long) exp);
        } else if (exp instanceof Integer) {
            return new Date(((Integer) exp).longValue());
        }
        return null;
    }

    /**
     * 사용자 정보로 생성token
     */
    public String generateToken(UserDetails userDetails) {
        // 다온마켓 4-7: 비밀번호를 넣은 지금이 auth_time(초). 고위험 작업은 이 시각을 본다(StepUpGuard)
        return generateToken(userDetails, LabClock.nowSeconds());
    }

    /** 다온마켓 4-7: auth_time을 지정해 만든다. 재인증하면 지금, 토큰 갱신(4-8)이면 원래 값을 그대로 옮긴다 */
    public String generateToken(UserDetails userDetails, long authTime) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_KEY_USERNAME, userDetails.getUsername());
        claims.put(CLAIM_KEY_CREATED, new Date());
        claims.put("auth_time", authTime);
        return generateToken(claims);
    }

    /**
     * 기존 token이 만료되지 않았을 때는 갱신할 수 있음
     *
     * @param oldToken tokenHead가 붙은token
     */
    public String refreshHeadToken(String oldToken) {
        if (StrUtil.isEmpty(oldToken)) {
            return null;
        }
        String token = oldToken.substring(tokenHead.length());
        if (StrUtil.isEmpty(token)) {
            return null;
        }
        // token검증 실패
        Map<String, Object> payload = getPayloadFromToken(token);
        if (payload == null) {
            return null;
        }
        // token이 이미 만료되었으면 갱신을 지원하지 않음
        if (isTokenExpired(token)) {
            return null;
        }
        // token을 30분 이내에 방금 갱신했다면 기존 값을 반환token
        if (tokenRefreshJustBefore(token, 30 * 60)) {
            return token;
        } else {
            payload.put(CLAIM_KEY_CREATED, new Date());
            return generateToken(payload);
        }
    }

    /**
     * token이 지정 시간 내에 방금 갱신되었는지 판단
     *
     * @param token 기존token
     * @param time  지정 시간(초)
     */
    private boolean tokenRefreshJustBefore(String token, int time) {
        Map<String, Object> payload = getPayloadFromToken(token);
        if (payload == null) {
            return false;
        }
        Object created = payload.get(CLAIM_KEY_CREATED);
        Date createdDate = null;
        if (created instanceof Long) {
            createdDate = new Date((Long) created);
        } else if (created instanceof Date) {
            createdDate = (Date) created;
        }
        if (createdDate == null) {
            return false;
        }
        Date refreshDate = new Date();
        // 갱신 시간이 생성 시간 기준 지정 시간 이내
        return refreshDate.after(createdDate) && refreshDate.before(DateUtil.offsetSecond(createdDate, time));
    }

    /** 다온마켓 4-7: 마지막으로 비밀번호를 넣은 시각(초). 없거나 서명이 틀리면 0 */
    public long getAuthTime(String token) {
        Map<String, Object> payload = getPayloadFromToken(token);
        Object t = payload == null ? null : payload.get("auth_time");
        return t instanceof Number n ? n.longValue() : 0L;
    }

    /** "Bearer " 머리말을 뗀 토큰 */
    public String stripHead(String header) {
        return header != null && header.startsWith(tokenHead) ? header.substring(tokenHead.length()) : null;
    }

    /** 다온마켓 4-8: 토큰 ID(jti). 없거나 서명이 틀리면 null */
    public String getJti(String token) {
        Map<String, Object> payload = getPayloadFromToken(token);
        Object jti = payload == null ? null : payload.get("jti");
        return jti == null ? null : String.valueOf(jti);
    }

    /** 다온마켓 4-8: 남은 수명(초). 폐기 목록에 이만큼만 둔다 */
    public long getRemainingSeconds(String token) {
        Map<String, Object> payload = getPayloadFromToken(token);
        Object exp = payload == null ? null : payload.get("exp");
        if (!(exp instanceof Number n)) {
            return 0;
        }
        long expMillis = n.longValue() < 100_000_000_000L ? n.longValue() * 1000 : n.longValue();
        return Math.max(0, (expMillis - LabClock.nowMillis()) / 1000);
    }
}
