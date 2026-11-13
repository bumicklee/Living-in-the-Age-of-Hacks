package com.macro.mall.portal.controller;

import com.google.re2j.Pattern;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.securekit.SecureKitSwitch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * [교육용 추가 T23 · 9-3 정답] 쿠폰 코드 모양 확인. GET /daon/coupon/check?code=DAON-2026-SPRING
 *
 * 순서가 중요하다: ① 길이 상한(20자)을 먼저 본다 ② 정규식은 선형 시간 엔진(RE2/J)으로 돌린다.
 * RE2/J는 입력 길이에 비례하는 시간만 쓴다(역참조·앞뒤 보기는 지원하지 않는다. 이 패턴은 쓰지 않는다).
 * 패턴을 다시 쓰는 방법도 있다: ^[A-Z0-9]+(-[A-Z0-9]+)*$ 는 java.util.regex에서도 갈래가 생기지 않는다.
 * SecureKitSwitch.on("input-limits") 줄은 영상용 랩 스위치다(끄면 옛 정규식, 길이 상한 없음).
 */
@RestController
@Tag(name = "DaonCouponController", description = "[교육용 추가 9-1] 쿠폰 코드 모양 확인")
@RequestMapping("/daon/coupon")
public class DaonCouponController {
    static final int MAX_LEN = 20;
    private static final Pattern CODE = Pattern.compile("^(([A-Z0-9]|[A-Z0-9]{2}|[A-Z0-9]{3})+)+(-[A-Z0-9]+)*$"); // RE2/J: 같은 패턴, 선형 시간
    private static final java.util.regex.Pattern LEGACY = java.util.regex.Pattern.compile("^(([A-Z0-9]|[A-Z0-9]{2}|[A-Z0-9]{3})+)+(-[A-Z0-9]+)*$");
    private static final int LAB_SAFETY_MAX = 28;

    @Operation(summary = "쿠폰 코드 모양 확인")
    @GetMapping("/check")
    public ResponseEntity<CommonResult<Map<String, Object>>> check(@RequestParam String code) {
        if (!SecureKitSwitch.on("input-limits")) {
            if (code.length() > LAB_SAFETY_MAX) {
                return ResponseEntity.badRequest().body(CommonResult.validateFailed("랩 안전 장치: 28자를 넘는 입력은 받지 않습니다"));
            }
            return ResponseEntity.ok(CommonResult.success(Map.<String, Object>of("valid", LEGACY.matcher(code).matches())));
        }
        if (code.length() > MAX_LEN) {
            SecurityEventLogger.denied("input.denied.too_long", "9-3")
                    .target("field:couponCode")
                    .reason("길이 상한(" + MAX_LEN + "자) 초과, 정규식 검사 전에 거절")
                    .log();
            return ResponseEntity.badRequest().body(CommonResult.validateFailed("쿠폰 코드는 " + MAX_LEN + "자까지입니다"));
        }
        return ResponseEntity.ok(CommonResult.success(Map.<String, Object>of("valid", CODE.matcher(code).matches())));
    }
}
