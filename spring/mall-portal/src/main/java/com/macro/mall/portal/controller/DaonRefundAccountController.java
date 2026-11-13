package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.component.DaonMailSender;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.stepup.StepUpGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Duration;
import java.util.Map;

/**
 * [교육용 추가] 다온마켓 환불 계좌(수업 4-7). 취소·반품 환불을 받을 계좌 하나. 값은 가짜다.
 * 저장은 Redis mall:refund:&lt;회원 ID&gt; = "은행|계좌"(랩을 단순하게 두려는 것. 회사에서는 DB 표와 암호화, 5-4).
 *
 * GET  /member/refund-account                       → 은행과 가린 계좌(뒤 4자리만)
 * POST /member/refund-account  bank, account         → 바꾼다
 */
@Controller
@Tag(name = "DaonRefundAccountController", description = "[교육용 추가] 환불 계좌")
@RequestMapping("/member/refund-account")
public class DaonRefundAccountController {
    @Autowired
    private UmsMemberService memberService;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private StepUpGuard stepUpGuard;
    @Autowired
    private DaonMailSender mail;

    @Operation(summary = "환불 계좌 보기")
    @RequestMapping(method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<Map<String, String>> get() {
        UmsMember m = memberService.getCurrentMember();
        String v = redis.opsForValue().get("mall:refund:" + m.getId());
        if (v == null) {
            return CommonResult.success(Map.of("bank", "", "account", ""));
        }
        String[] p = v.split("\\|", 2);
        return CommonResult.success(Map.of("bank", p[0], "account", mask(p[1])));
    }

    @Operation(summary = "환불 계좌 바꾸기")
    @RequestMapping(method = RequestMethod.POST)
    @ResponseBody
    public CommonResult<Map<String, String>> change(@RequestParam String bank, @RequestParam String account) {
        // 다온마켓 4-7: 비밀번호를 넣은 지 5분이 지났으면 재인증(401 step_up_required). 바꾸면 기록하고 메일로 알린다
        stepUpGuard.require(Duration.ofMinutes(5), "refund_account");
        UmsMember m = memberService.getCurrentMember();
        redis.opsForValue().set("mall:refund:" + m.getId(), bank + "|" + account);
        SecurityEventLogger.suspicious("account.change.high_risk", "4-7")
                .target("member:" + m.getId())
                .reason("바뀐 항목: refund_account")
                .log();
        mail.send(DaonMailSender.addressOf(m.getUsername()), "[다온마켓] 환불 계좌가 바뀌었습니다",
                m.getNickname() + "님, 환불 계좌가 " + bank + " " + mask(account) + "(으)로 바뀌었습니다. 직접 바꾸지 않았다면 고객센터로 알려 주세요.\n(교육용 랩의 가짜 메일입니다)");
        return CommonResult.success(Map.of("bank", bank, "account", mask(account)));
    }

    static String mask(String account) {
        String d = account == null ? "" : account.replaceAll("\\D", "");
        return d.length() <= 4 ? "****" : "*".repeat(d.length() - 4) + d.substring(d.length() - 4);
    }
}
