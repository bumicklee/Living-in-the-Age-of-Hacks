package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * [교육용 추가 T23] 쿠폰 코드 모양 확인. GET /daon/coupon/check?code=DAON-2026-SPRING
 *
 * 쿠폰 코드는 영문 대문자·숫자 덩어리를 하이픈으로 이은 모양이다. 9-1·9-3 시작 상태는 이것을
 * 중첩 반복이 있는 정규식 하나로 검사한다(교과서 예제 수준). 길이가 늘수록 실패하는 입력의 검사 시간이 두 배씩 는다.
 */
@RestController
@Tag(name = "DaonCouponController", description = "[교육용 추가 9-1] 쿠폰 코드 모양 확인")
@RequestMapping("/daon/coupon")
public class DaonCouponController {
    private static final Pattern CODE = Pattern.compile("^(([A-Z0-9]|[A-Z0-9]{2}|[A-Z0-9]{3})+)+(-[A-Z0-9]+)*$");
    /** 랩 안전 장치(수업의 고치기가 아니다): PC가 오래 멈추지 않게 28자를 넘으면 검사하지 않는다 */
    private static final int LAB_SAFETY_MAX = 28;

    @Operation(summary = "쿠폰 코드 모양 확인")
    @GetMapping("/check")
    public ResponseEntity<CommonResult<Map<String, Object>>> check(@RequestParam String code) {
        if (code.length() > LAB_SAFETY_MAX) {
            return ResponseEntity.badRequest().body(CommonResult.validateFailed("랩 안전 장치: 28자를 넘는 입력은 받지 않습니다"));
        }
        boolean ok = CODE.matcher(code).matches();
        return ResponseEntity.ok(CommonResult.success(Map.<String, Object>of("valid", ok)));
    }
}
