package com.macro.mall.portal.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * [교육용 추가 T28] 로그인 뒤 원래 보던 화면으로 보내는 이동 기능. GET /daon/go?next=/orders/9001
 *
 * 원본 mall에는 이 기능이 없다(고객 웹이 따로 있음). 11-1·11-2에서 「검사한 주소와 쓰는 주소가 다르다」를 보이려고 더했다.
 * 시작 상태는 이동 주소를 **문자열 비교**로만 검사한다: 「/」로 시작하거나 허용 호스트 주소로 시작하면 통과.
 * 검사는 문자열 앞부분을 보고, 브라우저는 같은 문자열을 URL 파서로 다시 읽는다(두 번 해석). 고치기는 11-2.
 */
@RestController
@Tag(name = "DaonRedirectController", description = "[교육용 추가 11-1] 로그인 뒤 이동")
@RequestMapping("/daon")
public class DaonRedirectController {
    /** 외부 이동을 허락한 주소(고객 웹의 공개 주소, 가짜 도메인) */
    private static final String ALLOWED = "https://shop.daon.example";

    @Operation(summary = "로그인 뒤 원래 보던 화면으로 이동")
    @GetMapping("/go")
    public ResponseEntity<Void> go(@RequestParam(value = "next", required = false) String next) {
        String location = "/";
        if (next != null && (next.startsWith("/") || next.startsWith(ALLOWED))) {    // ← 문자열 앞부분만 본다
            location = next;                                                       // ← 검사한 문자열을 그대로 쓴다(브라우저가 다시 읽는다)
        }
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, location).build();
    }
}
