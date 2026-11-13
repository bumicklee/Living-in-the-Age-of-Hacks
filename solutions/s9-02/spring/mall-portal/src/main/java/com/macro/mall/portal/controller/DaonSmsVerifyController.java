package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.component.DaonStepUpClient;
import com.macro.mall.portal.component.DaonStepUpClient.DependencyUnavailableException;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.security.event.SecurityEventLogger;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * [교육용 추가 T22 · 9-2 정답] 고위험 작업 앞 문자 본인 확인.
 *
 * POST /daon/verify/sms/send            → 회원 번호(랩: 010-0000-&lt;회원 ID 네 자리&gt;)로 인증 번호 문자
 * POST /daon/verify/sms/check?code=…    → 맞으면 5분 동안 「문자 확인됨」(Redis mall:daon:smsverified:&lt;회원 ID&gt;)
 *
 * 9-2: 번호가 틀림 → HTTP 400, 확인 불가(문자 사업자 장애) → HTTP 503 「잠시 뒤 다시 시도해 주세요」 + auth.fail_closed.dependency_error.
 * 고객 불편을 줄이는 길은 통과가 아니라 다시 시도 안내와 다른 수단(고객센터 확인)이다.
 */
@RestController
@Tag(name = "DaonSmsVerifyController", description = "[교육용 추가 9-2] 고위험 작업 앞 문자 본인 확인")
@RequestMapping("/daon/verify/sms")
public class DaonSmsVerifyController {
    @Autowired
    private UmsMemberService memberService;
    @Autowired
    private DaonStepUpClient client;
    @Autowired
    private StringRedisTemplate redis;

    @Operation(summary = "인증 번호 문자 보내기")
    @PostMapping("/send")
    public ResponseEntity<CommonResult<String>> send() {
        UmsMember m = memberService.getCurrentMember();
        try {
            client.start(phoneOf(m.getId()));
        } catch (Exception e) {
            return unavailable(m, e.getMessage());
        }
        return ResponseEntity.ok(CommonResult.success(null, "인증 번호를 문자로 보냈습니다"));
    }

    @Operation(summary = "인증 번호 확인")
    @PostMapping("/check")
    public ResponseEntity<CommonResult<String>> check(@RequestParam String code) {
        UmsMember m = memberService.getCurrentMember();
        boolean ok;
        try {
            ok = client.verify(phoneOf(m.getId()), code);
        } catch (DependencyUnavailableException e) {
            return unavailable(m, e.getMessage());
        }
        if (!ok) {
            return ResponseEntity.badRequest().body(CommonResult.validateFailed("인증 번호가 맞지 않습니다"));
        }
        redis.opsForValue().set("mall:daon:smsverified:" + m.getId(), "1", Duration.ofMinutes(5));
        return ResponseEntity.ok(CommonResult.success("verified", "본인 확인을 마쳤습니다"));
    }

    private ResponseEntity<CommonResult<String>> unavailable(UmsMember m, String why) {
        SecurityEventLogger.denied("auth.fail_closed.dependency_error", "9-2")
                .actor("member:" + m.getId())
                .target("dependency:sms-stub")
                .reason("본인 확인 서버 오류로 거부(" + why + ")")
                .log();
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(CommonResult.failed("본인 확인을 지금 할 수 없습니다. 잠시 뒤 다시 시도해 주세요"));
    }

    /** 랩의 가짜 번호. 시드 회원의 번호와 같은 모양(010-0000-0101 = alice) */
    static String phoneOf(Long memberId) {
        return String.format("010-0000-%04d", memberId % 10000);
    }
}
