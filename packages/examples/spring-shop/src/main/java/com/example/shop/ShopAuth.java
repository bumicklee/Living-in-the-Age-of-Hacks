package com.example.shop;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 가게 자체 로그인(회사마다 다른 부분). 토큰은 shop-&lt;무작위&gt;, 메모리에 둔다.
 * /api/admin/** 는 이 토큰이 있어야 한다. 누가 불렀는지는 요청 속성 secure-kit.actor 에 적어 둔다
 * (secure-kit을 붙이면 감사 기록·보안 이벤트가 이 값을 actor로 쓴다. 붙이기 전에는 아무도 읽지 않는다).
 */
@Configuration
public class ShopAuth implements WebMvcConfigurer, HandlerInterceptor {
    public static final String ACTOR_ATTR = "secure-kit.actor";
    /** shop-admin / shop-pass-1! (가짜). PBKDF2-SHA256 100,000번 */
    private static final Map<String, String> USERS = Map.of(
            "shop-admin", "100000$9L/tUG9hqNZ3PPCv32HQkQ==$EpalY/Dk4G7qsF+WrijwsDIniTRniCHkkRZNjuvbf+I=");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, String> sessions = new ConcurrentHashMap<>();

    /** 맞으면 새 토큰, 틀리면 null */
    public String login(String username, String password) {
        String stored = username == null ? null : USERS.get(username);
        if (stored == null || password == null || !matches(stored, password)) return null;
        byte[] b = new byte[16];
        RANDOM.nextBytes(b);
        String token = "shop-" + HexFormat.of().formatHex(b);
        sessions.put(token, username);
        return token;
    }

    public void reset() {
        sessions.clear();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/admin/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String auth = request.getHeader("Authorization");
        String user = auth != null && auth.startsWith("Bearer ") ? sessions.get(auth.substring(7).trim()) : null;
        if (user == null) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"로그인이 필요합니다\"}");
            return false;
        }
        request.setAttribute(ACTOR_ATTR, "admin:" + user);
        return true;
    }

    private static boolean matches(String stored, String password) {
        String[] p = stored.split("\\$");
        try {
            byte[] salt = Base64.getDecoder().decode(p[1]);
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, Integer.parseInt(p[0]), 256);
            byte[] got = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return MessageDigest.isEqual(Base64.getDecoder().decode(p[2]), got);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
