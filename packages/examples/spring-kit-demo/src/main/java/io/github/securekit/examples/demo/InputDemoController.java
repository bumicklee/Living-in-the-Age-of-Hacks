package io.github.securekit.examples.demo;

import io.github.securekit.core.regex.SafeRegex;
import io.github.securekit.web.part.SafeHttpClient;
import io.github.securekit.web.part.SafeRedirect;
import io.github.securekit.web.part.UploadValidator;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 입력·출력 쪽 부품: safe-http-client(1) · host-allowlist(2, 필터) · safe-redirect(3) · upload(4) · headers(5, 필터)
 * · error-response(14) · log-masking(16) · limits(17) · no-store(22, 필터)
 */
@RestController
public class InputDemoController {
    private static final Logger log = LoggerFactory.getLogger(InputDemoController.class);
    /** 쿠폰 코드: 20자 상한을 정규식 전에 보고, 되돌아가기 폭주가 없는 모양만 쓴다 */
    private static final SafeRegex COUPON = new SafeRegex("^[A-Z0-9]+(-[A-Z0-9]+)*$", 20);

    private final SafeHttpClient http;
    private final SafeRedirect redirect;
    private final UploadValidator uploads;

    public InputDemoController(SafeHttpClient http, SafeRedirect redirect, UploadValidator uploads) {
        this.http = http;
        this.redirect = redirect;
        this.uploads = uploads;
    }

    @GetMapping("/kit/public")
    public ResponseEntity<Map<String, Object>> publicPage() {
        return ResponseEntity.ok().header("Cache-Control", "public, max-age=60").body(Map.of("ok", true));
    }

    @GetMapping("/kit/fetch")
    public Map<String, Object> fetch(@RequestParam("url") String url) {
        SafeHttpClient.Response r = http.get(url);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", r.status());
        out.put("bytes", r.body().length);
        return out;
    }

    @GetMapping("/kit/redirect")
    public ResponseEntity<Void> redirect(@RequestParam(name = "next", required = false) String next) {
        return redirect.redirect(next);
    }

    /** 본문 = 파일 바이트. X-File-Name(원래 이름)은 저장에 쓰지 않는다 */
    @PostMapping("/kit/upload")
    public ResponseEntity<Map<String, Object>> upload(HttpServletRequest request) throws IOException {
        UploadValidator.Stored s = uploads.store(request.getInputStream());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stored", s.name());
        out.put("type", s.type());
        return ResponseEntity.status(201).body(out);
    }

    @PostMapping("/kit/echo")
    public Map<String, Object> echo(@RequestBody(required = false) byte[] body) {
        return Map.of("bytes", body == null ? 0 : body.length);
    }

    @GetMapping("/kit/coupon")
    public Map<String, Object> coupon(@RequestParam("code") String code) {
        return Map.of("valid", COUPON.matches(code));
    }

    /** 처리 중 내부 예외. 응답에는 일반 문구와 추적 번호만 나간다 */
    @GetMapping("/kit/error")
    public Map<String, Object> error() {
        throw new IllegalStateException("SQLSyntaxErrorException: Unknown column 'x' in 'oms_order'");
    }

    /** 일부러 본문을 그대로 찍는다(흔한 실수). 가리기 변환기가 나가기 전에 가린다 */
    @PostMapping("/kit/log")
    public ResponseEntity<Void> logBody(@RequestBody Map<String, Object> body) {
        log.info("고객 요청 본문 {}", body);
        return ResponseEntity.noContent().build();
    }
}
