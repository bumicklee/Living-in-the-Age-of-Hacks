package com.macro.mall.portal.controller;

import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.securekit.SafeRedirect;
import com.macro.mall.security.securekit.SecureKitSwitch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * [교육용 추가 T28 · 다온마켓 11-2 정답] 로그인 뒤 원래 보던 화면으로 보내는 이동 기능. GET /daon/go?next=/orders/9001
 *
 * 11-2: 문자열 비교를 SafeRedirect.resolve() 한 메서드로 바꿨다. 주소를 한 번 파싱한 URI 객체로 검사하고, 같은 객체로 Location을 만든다.
 * 거부하면 「/」로 보내고 redirect.denied.not_allowed를 남긴다. 받은 주소 원문은 로그에 넣지 않는다(공격자가 넣은 값, 로그 줄 위조 통로).
 */
@RestController
@Tag(name = "DaonRedirectController", description = "[교육용 추가 11-1] 로그인 뒤 이동")
@RequestMapping("/daon")
public class DaonRedirectController {
    /** 11-1 시작 상태의 허용 주소(스위치를 끈 옛 모습에서만 쓴다) */
    private static final String ALLOWED = "https://shop.daon.example";

    private final Set<String> allowedHosts;

    public DaonRedirectController(@Value("${daon.redirect.allowed-hosts:shop.daon.example}") String hosts) {
        this.allowedHosts = Arrays.stream(hosts.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    @Operation(summary = "로그인 뒤 원래 보던 화면으로 이동")
    @GetMapping("/go")
    public ResponseEntity<Void> go(@RequestParam(value = "next", required = false) String next) {
        if (!SecureKitSwitch.on("safe-redirect")) {
            // [랩 스위치] 끄면 11-1 시작 상태(문자열 앞부분만 비교)
            String location = next != null && (next.startsWith("/") || next.startsWith(ALLOWED)) ? next : "/";
            return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, location).build();
        }
        SafeRedirect.Decision d = SafeRedirect.resolve(next, allowedHosts);
        if (!d.allowed()) {
            SecurityEventLogger.denied("redirect.denied.not_allowed", "11-2")
                    .target("path:/daon/go")
                    .reason("허용 목록 밖 이동 주소(규칙 " + d.deniedRule() + ")")
                    .log();
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(d.location()).build();   // 검사한 바로 그 URI 객체로
    }
}
