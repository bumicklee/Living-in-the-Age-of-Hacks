package com.macro.mall.portal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.component.DaonMailSender;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.ratelimit.LoginRateLimiter;
import com.macro.mall.security.util.JwtTokenUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Map;

/**
 * [다온마켓 4-7 정답] 재인증(step-up). 현재 비밀번호 + 「다온 인증 앱」 번호 일치.
 *
 * POST /sso/step-up/start                     → 인증 앱(가짜, 127.0.0.1:9104)에 두 자리 숫자를 보낸다. 10분에 3번까지
 * POST /sso/step-up/confirm  password, number → 맞으면 auth_time이 지금인 새 토큰
 *
 * - 번호는 응답에 싣지 않는다. 사용자는 앱에 뜬 숫자를 보고 고객 웹에 넣는다(아무 생각 없이 「승인」만 누르는 방식보다 낫다).
 * - 인증 앱 요청이 10분에 3번을 넘으면 auth.mfa.push_flood + 메일 알림, 10분 동안 더 보내지 않는다(MFA 피로 공격).
 * - 재인증 실패도 4-3 속도 제한에 센다. 따로 두면 재인증 API가 새 대입 창구가 된다.
 * - SMS는 두 번째 수단으로 쓰지 않는다(4-7: 번호를 받는 길이 공격받을 수 있다).
 */
@Controller
@Tag(name = "DaonStepUpController", description = "[다온마켓 4-7] 고위험 작업 재인증")
@RequestMapping("/sso/step-up")
public class DaonStepUpController {
    private static final Logger LOG = LoggerFactory.getLogger(DaonStepUpController.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int PUSH_LIMIT = 3;
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    @Autowired
    private UmsMemberService memberService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtTokenUtil jwtTokenUtil;
    @Autowired
    private LoginRateLimiter loginRateLimiter;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private DaonMailSender mail;
    @Value("${jwt.tokenHead}")
    private String tokenHead;
    @Value("${daon.authapp.url:${DAON_AUTHAPP_URL:http://authapp-stub:8000}}")
    private String authAppUrl;

    @Operation(summary = "재인증 시작: 인증 앱에 번호를 보낸다")
    @RequestMapping(value = "/start", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult<String> start() {
        UmsMember m = memberService.getCurrentMember();
        String countKey = "rl:push:" + m.getId();
        Long n = redis.opsForValue().increment(countKey);
        if (n != null && n == 1) {
            redis.expire(countKey, WINDOW);
        }
        if (n != null && n > PUSH_LIMIT) {
            if (n == PUSH_LIMIT + 1) {
                SecurityEventLogger.denied("auth.mfa.push_flood", "4-7")
                        .target("member:" + m.getId())
                        .reason("인증 앱 요청 10분 " + PUSH_LIMIT + "회 초과, 10분 동안 막음")
                        .count(n.intValue())
                        .log();
                mail.send(DaonMailSender.addressOf(m.getUsername()), "[다온마켓] 인증 요청이 너무 많이 왔습니다",
                        m.getNickname() + "님, 짧은 시간에 본인 확인 요청이 여러 번 왔습니다. 직접 하지 않았다면 비밀번호를 바꿔 주세요.\n(교육용 랩의 가짜 메일입니다)");
            }
            return CommonResult.failed("본인 확인 요청이 너무 많습니다. 10분 뒤 다시 시도해 주세요");
        }
        String number = String.format("%02d", RANDOM.nextInt(100));
        redis.opsForValue().set("pwreset:stepup:" + m.getId(), number, Duration.ofMinutes(2));
        push(m.getUsername(), number);
        return CommonResult.success(null, "인증 앱에 번호를 보냈습니다");
    }

    @Operation(summary = "재인증 확인: 현재 비밀번호 + 인증 앱 번호")
    @RequestMapping(value = "/confirm", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult<Map<String, String>> confirm(@RequestParam String password, @RequestParam String number) {
        UmsMember m = memberService.getCurrentMember();
        loginRateLimiter.check(m.getUsername());
        String expected = redis.opsForValue().getAndDelete("pwreset:stepup:" + m.getId());   // 한 번만 쓴다
        boolean ok = expected != null
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), number.getBytes(StandardCharsets.UTF_8))
                && passwordEncoder.matches(password, m.getPassword());
        if (!ok) {
            loginRateLimiter.failed(m.getUsername());
            SecurityEventLogger.denied("auth.stepup.failed", "4-7")
                    .target("member:" + m.getId())
                    .reason("재인증 실패(비밀번호 또는 번호 일치)")
                    .log();
            return CommonResult.validateFailed("본인 확인에 실패했습니다");
        }
        UserDetails user = memberService.loadUserByUsername(m.getUsername());
        return CommonResult.success(Map.of("token", jwtTokenUtil.generateToken(user), "tokenHead", tokenHead));
    }

    private void push(String username, String number) {
        try {
            String body = JSON.writeValueAsString(Map.of("to", username, "number", number, "text", "다온마켓 본인 확인 요청"));
            HttpRequest req = HttpRequest.newBuilder(URI.create(authAppUrl + "/v1/push"))
                    .timeout(Duration.ofSeconds(3)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<Void> res = http.send(req, HttpResponse.BodyHandlers.discarding());
            if (res.statusCode() / 100 != 2) {
                LOG.warn("인증 앱이 거절했습니다(HTTP {})", res.statusCode());
            }
        } catch (java.io.IOException e) {
            LOG.warn("인증 앱에 닿지 않습니다: {}", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
