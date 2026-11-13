package io.github.securekit.examples.demo;

import io.github.securekit.core.KitMessages;
import io.github.securekit.core.SecureKitException;
import io.github.securekit.core.password.ConstantTimeLogin;
import io.github.securekit.core.password.PasswordPolicy;
import io.github.securekit.web.KitPrincipal;
import io.github.securekit.web.annotation.RateLimited;
import io.github.securekit.web.annotation.RequireStepUp;
import io.github.securekit.web.part.ResetLinkBuilder;
import io.github.securekit.web.token.KitTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 로그인·토큰·비밀번호 쪽 부품: rate-limit(6) · password-policy(7) · constant-time-login(8) · reset-link(9) · step-up(10) · token(11)
 */
@RestController
public class AuthDemoController {
    private final DemoUsers users;
    private final ConstantTimeLogin login;
    private final KitTokenService tokens;
    private final PasswordPolicy policy;
    private final ResetLinkBuilder resetLinks;
    private final MailOutbox outbox;

    public AuthDemoController(DemoUsers users, ConstantTimeLogin login, KitTokenService tokens, PasswordPolicy policy,
                              ResetLinkBuilder resetLinks, MailOutbox outbox) {
        this.users = users;
        this.login = login;
        this.tokens = tokens;
        this.policy = policy;
        this.resetLinks = resetLinks;
        this.outbox = outbox;
    }

    /** 없는 아이디와 틀린 비밀번호가 같은 상태·같은 문구·같은 비용 */
    @PostMapping("/kit/login")
    @RateLimited("login")
    public Map<String, Object> login(@RequestBody Map<String, Object> body) {
        String username = text(body, "username");
        if (!login.matches(users.hashOf(username), text(body, "password"))) {
            throw new SecureKitException(401, KitMessages.LOGIN_FAILED);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("token", tokens.issue(username, users.roles(username)));
        out.put("expiresIn", tokens.ttlSeconds());
        return out;
    }

    @PostMapping("/kit/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        tokens.revoke(KitPrincipal.requireUser(request));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/kit/me")
    public Map<String, Object> me(HttpServletRequest request) {
        KitPrincipal p = KitPrincipal.requireUser(request);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sub", p.sub());
        out.put("roles", p.roles());
        return out;
    }

    @PostMapping("/kit/password/check")
    public ResponseEntity<Map<String, Object>> checkPassword(@RequestBody Map<String, Object> body) {
        PasswordPolicy.Result r = policy.check(text(body, "password"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", r.ok());
        if (!r.ok()) out.put("reason", r.reason());
        return ResponseEntity.status(r.ok() ? 200 : 422).body(out);
    }

    /** 가입 여부와 상관없이 같은 202 */
    @PostMapping("/kit/reset/request")
    public ResponseEntity<Map<String, Object>> resetRequest(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String username = text(body, "username");
        if (users.exists(username)) {
            String link = resetLinks.issue(username, request);
            outbox.send(username + "@shop.example", "비밀번호 재설정 안내",
                    "아래 링크에서 새 비밀번호를 정하세요: " + link + "\n링크는 15분 동안 한 번만 쓸 수 있습니다.");
        }
        return ResponseEntity.accepted().body(Map.of("message", "안내 메일을 보냈습니다(가입된 경우)"));
    }

    @PostMapping("/kit/reset/confirm")
    public ResponseEntity<Map<String, Object>> resetConfirm(@RequestBody Map<String, Object> body) {
        String newPassword = text(body, "newPassword");
        PasswordPolicy.Result r = policy.check(newPassword);
        if (!r.ok()) return ResponseEntity.status(422).body(Map.of("ok", false, "reason", r.reason()));
        String username = resetLinks.consume(text(body, "token"));
        users.changePassword(username, newPassword);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/kit/account/email")
    @RequireStepUp("account.email")
    public Map<String, Object> changeEmail(@RequestBody Map<String, Object> body) {
        // 예제라 저장하지 않는다. 실제로는 새 주소 확인 메일을 보내고 옛 주소에도 알린다
        return Map.of("ok", true);
    }

    static String text(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        return v == null ? null : v.toString();
    }
}
