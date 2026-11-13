package com.macro.mall.daon;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.mapper.UmsMemberMapper;
import com.macro.mall.model.UmsMember;
import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.securekit.SecureKitSwitch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * [다온마켓 5-4 정답] 전화번호 원문 보기. 목록에는 가린 번호만 나가고, 원문은 이 길로만, 한 번에 한 건, 사유를 적을 때만 나간다.
 *
 * <pre>
 * GET /daon/customer/{id}/phone?reason=...
 *   권한   : /daon/customer/** 권한(슈퍼 관리자). 없으면 403(기본 거부, 2-3)
 *   사유   : 4자 이상(사유에 개인정보를 쓰지 말라고 화면에서 안내). 없으면 400 + pii.reveal.denied
 *   상한   : 관리자 한 명이 하루 50건. 넘으면 429 + pii.reveal.burst(처음 넘은 때 한 번)
 *   기록   : 볼 때마다 pii.reveal(INFO). 사유는 100자까지만 남기고 번호는 남기지 않는다
 * </pre>
 *
 * 스위치 pii-mask를 끄면 사유·상한·기록 없이 원문을 준다(5-3 완료 상태의 느슨한 모습. 영상용).
 */
@RestController
@Tag(name = "DaonPiiRevealController", description = "다온마켓 개인정보 원문 보기(교육용 추가)")
@RequestMapping("/daon/customer")
public class DaonPiiRevealController {
    static final int DAILY_LIMIT = 50;
    static final int REASON_MIN = 4;
    static final int REASON_MAX = 100;

    @Autowired
    private UmsMemberMapper memberMapper;
    @Autowired
    private StringRedisTemplate redis;

    @Operation(summary = "회원 전화번호 원문 보기(사유 필수, 하루 상한)")
    @GetMapping("/{id}/phone")
    public ResponseEntity<CommonResult<Map<String, Object>>> reveal(@PathVariable Long id,
                                                                    @RequestParam(required = false) String reason,
                                                                    Principal principal) {
        String admin = principal == null ? "unknown" : principal.getName();
        boolean guarded = SecureKitSwitch.on("pii-mask");
        String why = reason == null ? "" : reason.replaceAll("\\p{Cntrl}", " ").trim();
        if (guarded && why.length() < REASON_MIN) {
            SecurityEventLogger.denied("pii.reveal.denied", "5-4")
                    .actor("admin:" + admin).target("member:" + id).reason("원문 보기 사유 없음").log();
            return ResponseEntity.badRequest().body(CommonResult.failed("원문을 보는 사유를 적어 주세요"));
        }
        if (guarded) {
            String key = "rl:reveal:" + admin + ":" + LocalDate.now();
            Long n = redis.opsForValue().increment(key);
            if (n != null && n == 1) {
                redis.expire(key, Duration.ofDays(1));
            }
            if (n != null && n > DAILY_LIMIT) {
                if (n == DAILY_LIMIT + 1) {
                    SecurityEventLogger.denied("pii.reveal.burst", "5-4")
                            .actor("admin:" + admin).target("member:" + id)
                            .reason("하루 원문 보기 상한 " + DAILY_LIMIT + "건 초과").count(n.intValue()).log();
                }
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(CommonResult.failed("오늘 볼 수 있는 원문 수를 넘었습니다"));
            }
        }
        UmsMember m = memberMapper.selectByPrimaryKey(id);
        if (m == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResult.failed("없는 회원입니다"));
        }
        if (guarded) {
            SecurityEventLogger.suspicious("pii.reveal", "5-4")
                    .actor("admin:" + admin).target("member:" + id)
                    .reason(why.length() > REASON_MAX ? why.substring(0, REASON_MAX) : why).log();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", m.getId());
        body.put("phone", m.getPhone());
        return ResponseEntity.ok(CommonResult.success(body));
    }
}
