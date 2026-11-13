package com.macro.mall.security.openbao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * [다온마켓 A4 · 수업 5-2에서 붙는 키 금고(OpenBao) 연결 부품] AppRole 로그인과 호출을 한 곳에 모은다.
 *
 * <pre>
 * JsonNode keys = bao.read("transit/keys/daon-jwt");                              // GET  /v1/transit/keys/daon-jwt
 * JsonNode res  = bao.write("transit/sign/daon-jwt", Map.of("input", b64, ...));  // POST /v1/transit/sign/daon-jwt
 * String data   = bao.read("secret/data/daon/pii-hmac").path("data").path("data").path("key").asText();
 * </pre>
 *
 * - 반환값은 OpenBao 응답의 <b>data</b> 부분이다(응답 전체가 아니다). 서명 응답이면 {@code res.path("signature")}.
 * - 접속 정보는 환경 변수 DAON_OPENBAO_ADDR · DAON_OPENBAO_ROLE(portal·admin)과 /run/openbao/&lt;역할&gt;-role-id · -secret-id 파일.
 * - 로그인 토큰은 메모리에만 두고, 만료 60초 전에 다시 로그인한다. 403이 오면 한 번 다시 로그인해 본다.
 * - 오류 메시지에는 경로와 상태 코드만 넣는다. 토큰·secret-id·요청 본문은 로그에도 남기지 않는다.
 * - 정책 daon-app이 허용하지 않는 길을 부르면 403이 온다(예: 서명 키 꺼내기). 호출 전에 막지 않는다. 정책이 막는다.
 */
@Component
public class OpenBaoClient {
    private static final Logger LOG = LoggerFactory.getLogger(OpenBaoClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration RENEW_MARGIN = Duration.ofSeconds(60);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final String addr = env("DAON_OPENBAO_ADDR", "http://openbao:8200");
    private final String role = env("DAON_OPENBAO_ROLE", "portal");
    private final Path credDir = Path.of(env("DAON_OPENBAO_CREDS", "/run/openbao"));

    private volatile String token;
    private volatile long tokenExpiresAtMillis;

    /** 이 앱에 OpenBao 접속 정보가 있는지(아키텍처 A4가 켜져 있는지) */
    public boolean configured() {
        return Files.isReadable(credDir.resolve(role + "-role-id")) && Files.isReadable(credDir.resolve(role + "-secret-id"));
    }

    public JsonNode read(String path) {
        return call("GET", path, null);
    }

    public JsonNode write(String path, Map<String, ?> body) {
        return call("POST", path, body == null ? Map.of() : body);
    }

    // ✏️ 6-3: 동적 DB 자격증명은 임대 정보(lease_id·lease_duration)가 data 밖에 있다. 응답 전체를 돌려주는 읽기를 더한다
    private JsonNode call(String method, String path, Map<String, ?> body) {
        return call(method, path, body, false);
    }

    private JsonNode call(String method, String path, Map<String, ?> body, boolean raw) {
        try {
            return exchange(method, path, body, false, raw);
        } catch (OpenBaoException e) {
            if (e.getStatus() == 403 && !path.startsWith("auth/")) {       // 토큰이 폐기됐을 수 있다 → 한 번만 다시 로그인
                invalidate();
                return exchange(method, path, body, true, raw);
            }
            throw e;
        }
    }

    private JsonNode exchange(String method, String path, Map<String, ?> body, boolean retried) {
        return exchange(method, path, body, retried, false);
    }

    private JsonNode exchange(String method, String path, Map<String, ?> body, boolean retried, boolean raw) {
        String clientToken = path.equals("auth/approle/login") ? null : token();
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(addr + "/v1/" + path)).timeout(Duration.ofSeconds(5));
            if (clientToken != null) {
                b.header("X-Vault-Token", clientToken);
            }
            if (body != null) {
                b.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body), StandardCharsets.UTF_8));
            } else {
                b.GET();
            }
            HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() / 100 != 2) {
                throw new OpenBaoException("OpenBao 호출 실패: " + method + " " + path + " → HTTP " + res.statusCode(), res.statusCode());
            }
            JsonNode root = res.body() == null || res.body().isBlank() ? JSON.createObjectNode() : JSON.readTree(res.body());
            return path.equals("auth/approle/login") ? root.path("auth") : root.path("data");
        } catch (IOException e) {
            throw new OpenBaoException("OpenBao에 닿지 않습니다: " + method + " " + path, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OpenBaoException("OpenBao 호출이 중단됐습니다: " + path, e);
        }
    }

    private String token() {
        if (token == null || System.currentTimeMillis() > tokenExpiresAtMillis - RENEW_MARGIN.toMillis()) {
            synchronized (this) {
                if (token == null || System.currentTimeMillis() > tokenExpiresAtMillis - RENEW_MARGIN.toMillis()) {
                    login();
                }
            }
        }
        return token;
    }

    private void invalidate() {
        synchronized (this) {
            token = null;
        }
    }

    private void login() {
        try {
            String roleId = Files.readString(credDir.resolve(role + "-role-id")).trim();
            String secretId = Files.readString(credDir.resolve(role + "-secret-id")).trim();
            JsonNode auth = exchange("POST", "auth/approle/login", Map.of("role_id", roleId, "secret_id", secretId), false);
            String t = auth.path("client_token").asText("");
            if (t.isEmpty()) {
                throw new OpenBaoException("OpenBao 로그인 응답에 토큰이 없습니다", 200);
            }
            long ttl = auth.path("lease_duration").asLong(3600);
            token = t;
            tokenExpiresAtMillis = System.currentTimeMillis() + ttl * 1000L;
            LOG.info("[A4] OpenBao AppRole 로그인 성공(역할 {}, 유효 {}초)", role, ttl);
        } catch (IOException e) {
            throw new OpenBaoException("OpenBao 접속 정보를 읽지 못했습니다(" + credDir + ")", e);
        }
    }

    private static String env(String name, String def) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? def : v;
    }
}
