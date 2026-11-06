package com.macro.mall.security.util;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.jwt.JWTUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JwtToken생성된 유틸 클래스
 * JWT token형식:header.payload.signature
 * header형식(알고리즘, token 유형):
 * {"alg": "HS512","typ": "JWT"}
 * payload형식(사용자 이름, 생성 시간, 발급 시간):
 * {"sub":"wang","created":1489079981393,"exp":1489684781}
 * signature생성 알고리즘:
 * HMACSHA512(base64UrlEncode(header) + "." +base64UrlEncode(payload),secret)
 * Created by macro on 2018/4/26.
 * Refactored to use Hutool JWTUtil
 */
public class JwtTokenUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(JwtTokenUtil.class);
    private static final String CLAIM_KEY_USERNAME = "sub";
    private static final String CLAIM_KEY_CREATED = "created";
    @Value("${jwt.secret}")
    private String secret;
    @Value("${jwt.expiration}")
    private Long expiration;
    @Value("${jwt.tokenHead}")
    private String tokenHead;

    /**
     * 서명 키 조회
     */
    private byte[] getSigningKey() {
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * JWT 생성을 담당하는token
     */
    private String generateToken(Map<String, Object> claims) {
        // 만료 시간 설정
        long expireTime = System.currentTimeMillis() + expiration * 1000;
        claims.put("exp", expireTime);
        return JWTUtil.createToken(claims, getSigningKey());
    }

    /**
     * token에서 JWT 페이로드 조회
     */
    private Map<String, Object> getPayloadFromToken(String token) {
        try {
            // token 서명 검증
            if (!JWTUtil.verify(token, getSigningKey())) {
                LOGGER.info("JWT서명 검증 실패:{}", token);
                return null;
            }
            // 파싱token payload
            return JWTUtil.parseToken(token).getPayloads();
        } catch (Exception e) {
            LOGGER.info("JWT형식 검증 실패:{}", token);
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
                return false;
            }
            long expTime = exp instanceof Long ? (Long) exp : ((Number) exp).longValue();
            return expTime < System.currentTimeMillis();
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
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_KEY_USERNAME, userDetails.getUsername());
        claims.put(CLAIM_KEY_CREATED, new Date());
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
}
