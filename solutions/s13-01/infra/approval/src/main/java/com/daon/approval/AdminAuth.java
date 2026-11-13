package com.daon.approval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [A10] 누가 부르는지: 관리자 앱 로그인 토큰(5-2 ES256, aud daon-admin)을 승인 서비스가 <b>스스로</b> 확인하고,
 * 계정 상태와 역할은 토큰이 아니라 DB에서 다시 읽는다(E21: 토큰 속 권한을 믿지 않는다).
 *
 * - 공개키는 금고 transit/keys/daon-jwt에서 읽는다(서명은 못 한다). 10초마다 다시 받는다(회전·폐기 반영).
 * - 관리자 앱이 넘긴 「요청자 이름」 같은 값은 받지 않는다. 요청자·승인자는 언제나 이 토큰의 sub다.
 * - 한계: 관리자 앱은 같은 키로 로그인 토큰을 서명할 수 있으므로, 관리자 앱이 통째로 뚫리면 토큰을 만들어 낼 수 있다.
 *   그래서 고액은 다른 경로(메일 확인 번호)와 다른 사람(13-4), 시간(지연 실행)을 더한다.
 */
@Component
public class AdminAuth {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String KID = "daon-jwt:v";
    private final Bao bao;
    private final JdbcTemplate jdbc;
    private final Map<Integer, PublicKey> keys = new ConcurrentHashMap<>();
    private volatile long keysAt;

    public AdminAuth(Bao bao, JdbcTemplate jdbc) {
        this.bao = bao;
        this.jdbc = jdbc;
    }

    public record Actor(long id, String username, String email, List<String> roles) {
        public String tag() {
            return "admin:" + username;
        }

        public boolean hasAny(List<String> want) {
            return roles.stream().anyMatch(want::contains);
        }
    }

    /** 확인되면 Actor, 아니면 null(호출한 쪽이 401) */
    public Actor actor(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h == null) {
            return null;
        }
        String token = h.startsWith("Bearer ") ? h.substring(7).trim() : h.trim();
        Map<String, Object> p = verify(token);
        if (p == null) {
            return null;
        }
        String sub = String.valueOf(p.get("sub"));
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, username, email, status FROM ums_admin WHERE username = ?", sub);
        if (rows.isEmpty() || !Integer.valueOf(1).equals(((Number) rows.get(0).get("status")).intValue())) {
            return null;                                   // 없는 계정·닫은 계정의 토큰(13-6)은 승인에 쓸 수 없다
        }
        long id = ((Number) rows.get(0).get("id")).longValue();
        List<String> roles = jdbc.queryForList(
                "SELECT r.name FROM ums_admin_role_relation rr JOIN ums_role r ON r.id = rr.role_id WHERE rr.admin_id = ? AND r.status = 1",
                String.class, id);
        return new Actor(id, sub, String.valueOf(rows.get(0).get("email")), roles);
    }

    private Map<String, Object> verify(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) {
                return null;
            }
            Map<String, Object> header = JSON.readValue(Base64.getUrlDecoder().decode(parts[0]), new TypeReference<>() { });
            String kid = String.valueOf(header.get("kid"));
            if (!"ES256".equals(header.get("alg")) || !kid.startsWith(KID)) {
                return null;
            }
            PublicKey pk = key(Integer.parseInt(kid.substring(KID.length())));
            if (pk == null) {
                return null;
            }
            Signature s = Signature.getInstance("SHA256withECDSAinP1363Format");
            s.initVerify(pk);
            s.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!s.verify(Base64.getUrlDecoder().decode(parts[2]))) {
                return null;
            }
            Map<String, Object> p = JSON.readValue(Base64.getUrlDecoder().decode(parts[1]), new TypeReference<>() { });
            long exp = p.get("exp") instanceof Number n ? n.longValue() : 0L;
            if (!"daon-market".equals(p.get("iss")) || !"daon-admin".equals(p.get("aud")) || exp * 1000L < System.currentTimeMillis() - 5000L) {
                return null;
            }
            return p;
        } catch (Exception e) {
            return null;
        }
    }

    private synchronized PublicKey key(int version) {
        if (keys.isEmpty() || System.currentTimeMillis() - keysAt > 10_000L || !keys.containsKey(version)) {
            if (System.currentTimeMillis() - keysAt > 2_000L) {
                keysAt = System.currentTimeMillis();
                JsonNode k = bao.read("transit/keys/daon-jwt");
                int min = k.path("min_decryption_version").asInt(1);
                keys.clear();
                k.path("keys").fields().forEachRemaining(e -> {
                    int v = Integer.parseInt(e.getKey());
                    String pem = e.getValue().path("public_key").asText("");
                    if (v >= min && !pem.isEmpty()) {
                        keys.put(v, parse(pem));
                    }
                });
            }
        }
        return keys.get(version);
    }

    private static PublicKey parse(String pem) {
        try {
            String body = pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
            return KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(body)));
        } catch (Exception e) {
            throw new IllegalStateException("공개키를 읽지 못했습니다");
        }
    }
}
