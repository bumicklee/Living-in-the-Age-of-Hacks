package com.macro.mall.portal.controller;

import com.macro.mall.security.token.JwtSigner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * [다온마켓 5-2 정답] 토큰 확인용 공개키 목록. kid와 공개키만 나가고, 로그인 없이 열린다(secure.ignored.urls).
 * 회전 직후 겹침 기간에는 키가 둘, 옛 버전을 폐기하면 하나가 된다.
 */
@RestController
@Tag(name = "DaonJwksController", description = "다온마켓 토큰 공개키 목록(교육용 추가)")
public class DaonJwksController {
    @Autowired
    private JwtSigner signer;

    @Operation(summary = "토큰 확인용 공개키 목록(JWKS)")
    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return signer.jwks();
    }
}
