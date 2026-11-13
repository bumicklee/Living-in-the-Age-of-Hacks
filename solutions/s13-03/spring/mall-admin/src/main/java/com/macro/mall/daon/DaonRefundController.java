package com.macro.mall.daon;

import com.macro.mall.common.api.CommonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * [다온마켓 13-3 정답] 승인 토큰으로 환불 실행(실행기의 입구). 권한은 반품 신청 관리(/returnApply/**)와 같다(MallSecurityConfig).
 *
 * <pre>
 * POST /daon/refund/execute   {"returnId":31,"token":"<승인 서비스가 서명한 토큰>"}
 *      → 200 실행 · 403 거부(refund.denied.no_approval)
 * </pre>
 * 반품 확인 화면은 이 주소를 부르지 않는다(확인 → 승인 요청 → 실행이 서비스 안에서 이어진다). 관리자 화면의 「다시 실행」 버튼과
 * make check의 「토큰 없이·위조·금액 변경·재사용」 시험이 쓴다.
 */
@RestController
@Tag(name = "DaonRefundController", description = "[다온마켓 13-3] 승인 토큰으로 환불 실행")
@RequestMapping("/daon/refund")
public class DaonRefundController {
    @Autowired
    private DaonRefundExecutor executor;

    @Operation(summary = "승인 토큰으로 환불 실행")
    @PostMapping("/execute")
    public ResponseEntity<CommonResult<Map<String, Object>>> execute(@RequestBody Map<String, Object> body) {
        Long returnId;
        try {
            returnId = Long.valueOf(String.valueOf(body.get("returnId")));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(CommonResult.validateFailed("returnId가 필요합니다"));
        }
        Object token = body.get("token");
        boolean done = executor.execute(returnId, token == null ? null : String.valueOf(token));
        if (!done) {
            return ResponseEntity.status(403).body(CommonResult.forbidden(null));
        }
        return ResponseEntity.ok(CommonResult.success(Map.of("returnId", returnId, "refunded", true)));
    }
}
