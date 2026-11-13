package com.example.shop;

import io.github.securekit.audit.Audited;
import io.github.securekit.web.annotation.RateLimited;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 가게 API. 15-4에서 바뀌는 곳은 로그인(속도 제한)과 회원 상세(감사 기록) 두 줄뿐이다 */
@RestController
@RequestMapping("/api")
public class ShopController {
    private final ShopAuth auth;
    private final ShopData data;

    public ShopController(ShopAuth auth, ShopData data) {
        this.auth = auth;
        this.data = data;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("ok", true);
    }

    @PostMapping("/login")
    @RateLimited("login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String token = auth.login(body.get("username"), body.get("password"));
        if (token == null) {
            return ResponseEntity.status(401).body(Map.of("message", "아이디 또는 비밀번호가 올바르지 않습니다"));
        }
        return ResponseEntity.ok(Map.of("token", token));
    }

    @GetMapping("/orders/{id}")
    public Map<String, Object> order(@PathVariable("id") String id) {
        return data.order(id);
    }

    @GetMapping("/admin/members/{id}")
    @Audited(action = "member.read", resource = "member")
    public ResponseEntity<Map<String, Object>> member(@PathVariable("id") long id) {
        Map<String, Object> m = data.member(id);
        return m == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(m);
    }

    /** 시험용: 로그인 상태를 비운다 */
    @PostMapping("/_reset")
    public ResponseEntity<Void> reset() {
        auth.reset();
        return ResponseEntity.noContent().build();
    }
}
