package com.macro.mall.daon;

import com.fasterxml.jackson.databind.JsonNode;
import com.macro.mall.common.api.CommonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * [다온마켓 13-6 정답] 관리자 화면의 「권한 상승 요청」. 로그인한 관리자 누구나 부른다(DynamicAuthorizationManager의 SELF_SERVICE).
 *
 * <pre>
 * POST /daon/elevation/request          {"reason":"역할 정리(티켓 OPS-12)","group":"admin-accounts","minutes":30} → 승인 서비스에 요청
 *      (다른 사람(운영 책임)이 승인 서비스에서 승인: POST 127.0.0.1:8097/elevations/{id}/approve)
 * POST /daon/elevation/{id}/activate    승인된 권한 상승 토큰을 받아 확인하고 켠다 → {"group":…, "expiresAt":…}
 * </pre>
 */
@RestController
@Tag(name = "DaonElevationController", description = "[다온마켓 13-6] 시간 제한 권한 상승")
@RequestMapping("/daon/elevation")
public class DaonElevationController {
    private static final Pattern ID = Pattern.compile("ap[0-9a-f]{20}");

    @Autowired
    private DaonApprovalClient client;
    @Autowired
    private DaonOpsElevationService elevation;

    @Operation(summary = "권한 상승 요청(사유·묶음·시간)")
    @PostMapping("/request")
    public ResponseEntity<CommonResult<Object>> request(@RequestBody Map<String, Object> body) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("reason", body.get("reason"));
        req.put("group", body.get("group"));
        req.put("minutes", body.get("minutes"));
        JsonNode r = client.elevation("POST", "/elevations", req);
        if (r == null || !r.hasNonNull("id")) {
            return ResponseEntity.badRequest().body(CommonResult.validateFailed(r == null ? "승인 서비스에 닿지 않습니다" : r.path("reason").asText("요청이 올바르지 않습니다")));
        }
        return ResponseEntity.ok(CommonResult.success(Map.of("id", r.path("id").asText(), "status", r.path("status").asText())));
    }

    @Operation(summary = "승인된 권한 상승 켜기")
    @PostMapping("/{id}/activate")
    public ResponseEntity<CommonResult<Object>> activate(@PathVariable("id") String id) {
        if (!ID.matcher(id).matches()) {
            return ResponseEntity.badRequest().body(CommonResult.validateFailed("승인 번호 형식이 다릅니다"));
        }
        JsonNode r = client.elevation("GET", "/elevations/" + id + "/token", null);
        String me = SecurityContextHolder.getContext().getAuthentication().getName();
        Long exp = elevation.activate(me, r == null ? null : r.path("token").asText(null));
        if (exp == null) {
            return ResponseEntity.status(403).body(CommonResult.forbidden(null));
        }
        return ResponseEntity.ok(CommonResult.success(Map.of("expiresAt", exp)));
    }
}
