package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.component.DaonStepUpClient;
import com.macro.mall.portal.service.UmsMemberService;
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
 * [교육용 추가 T22] 고위험 작업(환불 계좌·배송지 변경 등) 앞 문자 본인 확인.
 *
 * POST /daon/verify/sms/send            → 회원 번호(랩: 010-0000-&lt;회원 ID 네 자리&gt;)로 인증 번호 문자
 * POST /daon/verify/sms/check?code=…    → 맞으면 5분 동안 「문자 확인됨」(Redis daon:smsverified:&lt;회원 ID&gt;)
 *
 * 번호를 응답·로그에 싣지 않는다. 틀린 번호는 HTTP 400.
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
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(CommonResult.failed("문자를 보내지 못했습니다. 잠시 뒤 다시 시도해 주세요"));
        }
        return ResponseEntity.ok(CommonResult.success(null, "인증 번호를 문자로 보냈습니다"));
    }

    @Operation(summary = "인증 번호 확인")
    @PostMapping("/check")
    public ResponseEntity<CommonResult<String>> check(@RequestParam String code) {
        UmsMember m = memberService.getCurrentMember();
        if (!client.verify(phoneOf(m.getId()), code)) {
            return ResponseEntity.badRequest().body(CommonResult.validateFailed("인증 번호가 맞지 않습니다"));
        }
        redis.opsForValue().set("daon:smsverified:" + m.getId(), "1", Duration.ofMinutes(5));
        return ResponseEntity.ok(CommonResult.success("verified", "본인 확인을 마쳤습니다"));
    }

    /** 랩의 가짜 번호. 시드 회원의 번호와 같은 모양(010-0000-0101 = alice) */
    static String phoneOf(Long memberId) {
        return String.format("010-0000-%04d", memberId % 10000);
    }
}
