package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.service.DaonPasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * [교육용 추가] 다온마켓 비밀번호 재설정 링크(로그인 없이, /sso/** 화이트리스트 안). 수업 4-5(T11).
 * 고객 웹 화면: http://127.0.0.1:8088/reset (링크 요청) → 메일(Mailpit 127.0.0.1:8025) → /reset?token=… (새 비밀번호)
 */
@RestController
@Tag(name = "DaonPasswordResetController", description = "다온마켓 비밀번호 재설정 링크(교육용 추가)")
@RequestMapping("/sso/reset")
public class DaonPasswordResetController {
    @Autowired
    private DaonPasswordResetService resetService;

    @Operation(summary = "재설정 링크 메일 보내기")
    @PostMapping("/link")
    public CommonResult<Object> link(@RequestParam String username) {
        resetService.sendLink(username);
        return CommonResult.success(null, "가입한 메일로 재설정 링크를 보냈습니다");
    }

    @Operation(summary = "링크 속 토큰으로 새 비밀번호 정하기")
    @PostMapping("/confirm")
    public CommonResult<Object> confirm(@RequestParam String token, @RequestParam String password) {
        resetService.reset(token, password);
        return CommonResult.success(null, "비밀번호를 바꿨습니다");
    }
}
