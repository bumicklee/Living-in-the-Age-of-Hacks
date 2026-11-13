package com.daon.approval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/**
 * [A10] 승인 서비스의 금고(OpenBao) 연결. AppRole daon-approval 로 로그인한다.
 *
 * - 접속 정보: /run/approval/approval-role-id · approval-secret-id (approval-init이 만든 전용 볼륨. 관리자 앱에는 붙지 않는다)
 * - 정책 daon-approval: transit/sign/daon-approval(서명), transit/keys/daon-jwt(관리자 토큰 공개키 읽기),
 *   database/creds/approval(전용 DB 계정), sys/leases/renew. 관리자 앱(daon-admin)은 transit/verify/daon-approval만 갖는다.
 * - 오류 메시지에는 경로와 상태 코드만. 토큰·secret-id는 로그에 남기지 않는다.
 */
@Component
public class Bao {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final String addr = env("DAON_OPENBAO_ADDR", "http://openbao:8200");
    private final Path creds = Path.of(env("DAON_APPROVAL_CREDS", "/run/approval"));
    private volatile String token;
    private volatile long tokenUntil;

    public JsonNode read(String path) {
        return call("GET", path, null).path("data");
    }

    public JsonNode readRaw(String path) {
        return call("GET", path, null);
    }

    public JsonNode write(String path, Map<String, ?> body) {
        return call("POST", path, body).path("data");
    }

    public JsonNode writeRaw(String path, Map<String, ?> body) {
        return call("POST", path, body);
    }

    private JsonNode call(String method, String path, Map<String, ?> body) {
        try {
            return exchange(method, path, body, false);
        } catch (BaoException e) {
            if (e.status == 403) {                      // 토큰이 만료·폐기됐을 수 있다 → 한 번만 다시 로그인
                token = null;
                return exchange(method, path, body, false);
            }
            throw e;
        }
    }

    private JsonNode exchange(String method, String path, Map<String, ?> body, boolean login) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(addr + "/v1/" + path)).timeout(Duration.ofSeconds(5));
            if (!login) {
                b.header("X-Vault-Token", token());
            }
            if ("GET".equals(method)) {
                b.GET();
            } else {
                b.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body == null ? Map.of() : body)));
            }
            HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (r.statusCode() / 100 != 2) {
                throw new BaoException("금고 " + path + " → HTTP " + r.statusCode(), r.statusCode());
            }
            return r.body().isEmpty() ? JSON.createObjectNode() : JSON.readTree(r.body());
        } catch (IOException e) {
            throw new BaoException("금고에 닿지 않습니다(" + path + ")", 0);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BaoException("중단됨", 0);
        }
    }

    private synchronized String token() {
        if (token != null && System.currentTimeMillis() < tokenUntil) {
            return token;
        }
        try {
            String roleId = Files.readString(creds.resolve("approval-role-id")).trim();
            String secretId = Files.readString(creds.resolve("approval-secret-id")).trim();
            JsonNode auth = exchange("POST", "auth/approle/login", Map.of("role_id", roleId, "secret_id", secretId), true).path("auth");
            token = auth.path("client_token").asText();
            tokenUntil = System.currentTimeMillis() + Math.max(60, auth.path("lease_duration").asLong(3600) - 60) * 1000L;
            return token;
        } catch (IOException e) {
            throw new BaoException("승인 서비스 금고 접속 정보(/run/approval)를 읽지 못했습니다", 0);
        }
    }

    static String env(String name, String def) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? def : v.trim();
    }

    public static class BaoException extends RuntimeException {
        final int status;

        BaoException(String message, int status) {
            super(message);
            this.status = status;
        }
    }
}
