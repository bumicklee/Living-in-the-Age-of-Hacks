package com.macro.mall.daon;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.security.audit.AuditLog;
import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.securekit.DegradedMode;
import com.macro.mall.security.trace.TraceId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * [다온마켓 14-3 정답] 축소 운영 켜고 끄기. 「주문은 받고 정산은 멈춘다」를 사람이 한 번에 켜는 스위치.
 *
 * GET  /daon/mode              지금 모드(payment = normal | degraded)
 * POST /daon/mode/payment      {"mode":"degraded"|"normal","reason":"결제사 응답 지연(가짜 티켓 INC-14)"}
 *
 * - 고위험 기능이다: 권한표(관리자 계정 관리와 같은 권한) + 13-6 권한 상승(묶음 incident-response, OpsElevationGate). 사유는 필수(100자까지).
 * - 켜면: Redis mall:mode:payment=degraded → 고객 앱은 결제를 대기 큐로, 결제 확인은 「확인 중」 · 관리자 앱은 환불·정산 503(DaonDegradedModeFilter),
 *   지연 실행 환불을 가져가지 않음 · 승인 서비스의 출금 서킷 브레이커를 연다(승인 토큰 자동 발급 멈춤, 다시 닫기는 서로 다른 두 사람, 13-4)
 * - 끄면: normal → 대기 결제를 천천히 다시 처리(DaonPendingPayments). 브레이커는 자동으로 닫지 않는다(사람 둘이 확인하고 닫는다)
 * - ops.mode.degraded(WARN): 사고 선언 후보(14-4 런북 첫 단계). 켜고 끈 사람·시각·사유는 감사 로그(ops.mode.set)에도 남는다.
 */
@RestController
@Tag(name = "DaonModeController", description = "[다온마켓 14-3] 축소 운영")
@RequestMapping("/daon/mode")
public class DaonModeController {
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @Autowired
    private DegradedMode degradedMode;
    @Autowired
    private DaonApprovalClient approval;

    @Operation(summary = "지금 모드")
    @GetMapping
    public CommonResult<Map<String, Object>> state() {
        return CommonResult.success(Map.of(DegradedMode.PAYMENT, degradedMode.isOn(DegradedMode.PAYMENT) ? "degraded" : "normal"));
    }

    @Operation(summary = "결제 축소 운영 켜고 끄기(권한 상승·사유 필수)")
    @PostMapping("/payment")
    public ResponseEntity<CommonResult<Map<String, Object>>> payment(@RequestBody Map<String, Object> body) {
        String mode = String.valueOf(body.get("mode"));
        String reason = body.get("reason") == null ? "" : String.valueOf(body.get("reason")).trim();
        if (!("degraded".equals(mode) || "normal".equals(mode)) || reason.isEmpty() || reason.length() > 100) {
            return ResponseEntity.badRequest().body(CommonResult.validateFailed("mode(degraded|normal)와 사유(100자까지)가 필요합니다"));
        }
        String who = SecurityContextHolder.getContext().getAuthentication().getName();
        boolean degraded = "degraded".equals(mode);
        degradedMode.set(DegradedMode.PAYMENT, degraded);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(DegradedMode.PAYMENT, mode);
        if (degraded) {
            SecurityEventLogger.denied("ops.mode.degraded", "14-3")
                    .actor("admin:" + who)
                    .target("feature:payment")
                    .reason("결제사 응답 지연으로 축소 운영 시작(사유는 감사 로그에)")
                    .log();
            out.put("payoutBreaker", approval.configured() ? approval.holdPayouts("축소 운영(결제)") : "승인 서비스 없음");
        } else {
            out.put("payoutBreaker", "그대로(다시 닫기는 서로 다른 두 사람, POST /breaker/close)");
        }
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("ts", OffsetDateTime.now(ZONE).toString());
        e.put("trace_id", TraceId.current());
        e.put("actor", "admin:" + who);
        e.put("action", "ops.mode.set");
        e.put("target", "feature:payment=" + mode);
        e.put("count", 1);
        e.put("reason", reason);
        e.put("result", "ok");
        AuditLog.write(e);
        return ResponseEntity.ok(CommonResult.success(out));
    }
}
