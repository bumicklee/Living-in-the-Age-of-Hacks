package com.macro.mall.daon;

import com.macro.mall.common.api.CommonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * [다온마켓 10-5 정답] 대사 수동 실행. 권한표에는 관리자 계정 관리(/admin/**)와 같은 권한으로 올린다(MallSecurityConfig).
 * 결과에는 불일치 회원·주문 ID만 담는다(금액·개인정보 없음).
 */
@RestController
@Tag(name = "DaonReconcileController", description = "[다온마켓 10-5] 원장 대사")
@RequestMapping("/daon/reconcile")
public class DaonReconcileController {
    @Autowired
    private DaonReconcileJob job;

    @Operation(summary = "대사 지금 실행")
    @PostMapping("/run")
    public CommonResult<Map<String, Object>> run() {
        String who = SecurityContextHolder.getContext().getAuthentication().getName();
        return CommonResult.success(job.run("system:reconcile"), "대사를 마쳤습니다(요청한 관리자 " + who + ")");
    }
}
