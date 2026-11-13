package com.macro.mall.daon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.security.event.SecurityEventLogger;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;

/**
 * 다온마켓 3-5: CSP 위반 보고 받기. 관리자 화면 nginx가 /api/daon/csp-report를 여기로 넘긴다.
 *
 * - 브라우저는 로그인 없이 보고를 보낸다(secure.ignored.urls에 등록). 누구나 위조할 수 있으므로 본문 크기에 상한을 두고,
 *   보고에서 지시어 이름과 막힌 출처의 호스트(또는 inline·eval)만 꺼낸다. 보고 본문 전체는 남기지 않는다.
 * - ⑤ 탐지: csp.violation(INFO). 같은 페이지에서 반복되면 경보 후보(8장).
 */
@RestController
@RequestMapping("/daon")
public class DaonCspReportController {
    private static final int MAX_BYTES = 8 * 1024;
    private static final ObjectMapper JSON = new ObjectMapper();

    @PostMapping(value = "/csp-report", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<Void> report(HttpServletRequest request) throws IOException {
        byte[] body = request.getInputStream().readNBytes(MAX_BYTES + 1);
        if (body.length > MAX_BYTES) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }
        JsonNode root;
        try {
            root = JSON.readTree(body);
        } catch (IOException e) {
            return ResponseEntity.badRequest().build();
        }
        // report-uri 방식: {"csp-report": {...}} · Reporting API 방식: [{"type":"csp-violation","body":{...}}]
        JsonNode r = root.has("csp-report") ? root.get("csp-report")
                : root.isArray() && root.size() > 0 ? root.get(0).path("body") : root;
        String directive = text(r, "effective-directive", "effectiveDirective", "violated-directive");
        directive = directive.split(" ")[0].replaceAll("[^a-z-]", "");
        String blocked = blockedSource(text(r, "blocked-uri", "blockedURL", "blockedUri"));
        SecurityEventLogger.suspicious("csp.violation", "3-5")
                .actor("anonymous")
                .target("directive:" + (directive.isEmpty() ? "unknown" : cut(directive, 40)))
                .reason("막힌 출처: " + blocked)
                .request(request)
                .log();
        return ResponseEntity.noContent().build();
    }

    private static String text(JsonNode n, String... names) {
        for (String name : names) {
            if (n.hasNonNull(name)) {
                return n.get(name).asText("");
            }
        }
        return "";
    }

    /** inline·eval은 그대로, 주소면 호스트만 */
    private static String blockedSource(String uri) {
        if (uri.isEmpty() || uri.equals("inline") || uri.equals("eval") || uri.equals("data") || uri.equals("blob")) {
            return uri.isEmpty() ? "unknown" : uri;
        }
        try {
            String host = URI.create(uri).getHost();
            return host == null ? "unknown" : cut(host.replaceAll("[^A-Za-z0-9.:-]", ""), 60);
        } catch (IllegalArgumentException e) {
            return "unknown";
        }
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
