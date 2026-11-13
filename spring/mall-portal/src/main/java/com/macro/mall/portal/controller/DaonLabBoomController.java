package com.macro.mall.portal.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * [랩 도구 9-2] 일부러 예상 밖 예외를 내는 주소. GET /daon/lab/boom (로그인 필요)
 *
 * 랩(compose.lab.yml의 DAON_LAB_BOOM=true)에서만 등록된다. 운영 코드에 남기지 않는다.
 * 오류 경로가 장애를 다른 문제(401)로 바꿔 내보내는지, 500 + trace_id로 내보내는지 확인할 때 쓴다.
 */
@RestController
@ConditionalOnProperty(name = "daon.lab.boom", havingValue = "true")
@Tag(name = "DaonLabBoomController", description = "[랩 도구 9-2] 일부러 예외 내기")
public class DaonLabBoomController {
    @Operation(summary = "일부러 예외 내기(랩 전용)")
    @GetMapping("/daon/lab/boom")
    public String boom() {
        throw new IllegalStateException("랩 전용: 일부러 낸 예외(9-2)");
    }
}
