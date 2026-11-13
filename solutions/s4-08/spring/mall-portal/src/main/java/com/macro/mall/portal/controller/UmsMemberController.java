package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.domain.UmsMemberUpdateParam;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.security.securekit.SecureKitSwitch;
import com.macro.mall.security.token.TokenService;
import com.macro.mall.security.util.JwtTokenUtil;
import org.springframework.security.core.userdetails.UserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

/**
 * 회원 관리Controller
 * Created by macro on 2018/8/3.
 */
@Controller
@Tag(name = "UmsMemberController", description = "회원 로그인·가입 관리")
@RequestMapping("/sso")
public class UmsMemberController {
    @Value("${jwt.tokenHeader}")
    private String tokenHeader;
    @Value("${jwt.tokenHead}")
    private String tokenHead;
    @Autowired
    private UmsMemberService memberService;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Operation(summary = "회원가입")
    @RequestMapping(value = "/register", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult register(@RequestParam String username,
                                 @RequestParam String password,
                                 @RequestParam String telephone,
                                 @RequestParam String authCode) {
        memberService.register(username, password, telephone, authCode);
        return CommonResult.success(null,"회원가입 성공");
    }

    @Operation(summary = "회원 로그인")
    @RequestMapping(value = "/login", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult login(@RequestParam String username,
                              @RequestParam String password) {
        String token = memberService.login(username, password);
        if (token == null) {
            return CommonResult.validateFailed("아이디 또는 비밀번호가 올바르지 않습니다");   // 다온마켓 4-4: 관리자 로그인과 같은 문구
        }
        Map<String, String> tokenMap = new HashMap<>();
        tokenMap.put("token", token);
        tokenMap.put("tokenHead", tokenHead);
        if (SecureKitSwitch.on("token-revoke")) {
            tokenMap.put("refreshToken", tokenService.issueRefresh(username, jwtTokenUtil.getAuthTime(token)));   // 다온마켓 4-8: 15분 액세스 + 회전하는 갱신 토큰
        }
        return CommonResult.success(tokenMap);
    }

    @Operation(summary = "회원 정보 조회")
    @RequestMapping(value = "/info", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult info(Principal principal) {
        if(principal==null){
            return CommonResult.unauthorized(null);
        }
        UmsMember member = memberService.getCurrentMember();
        return CommonResult.success(member);
    }

    // [교육용 추가] 다온마켓 회원정보 수정(닉네임·성별·생일·도시·직업·소개)
    @Operation(summary = "회원정보 수정")
    @RequestMapping(value = "/updateInfo", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult updateInfo(Principal principal,
                                   // 다온마켓 2-4: 요청 전용 DTO로 받는다. 고칠 수 있는 항목만 DTO에 있다
                                   @Validated @RequestBody UmsMemberUpdateParam param) {
        if (principal == null) {
            return CommonResult.unauthorized(null);
        }
        memberService.updateInfo(param);
        return CommonResult.success(null, "회원정보 수정 성공");
    }

    @Operation(summary = "인증 코드 발급")
    @RequestMapping(value = "/getAuthCode", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult getAuthCode(@RequestParam String telephone) {
        String authCode = memberService.generateAuthCode(telephone);
        // 다온마켓 4-5: 번호는 문자로만 간다. 응답에는 「보냈다」만
        if (authCode == null) {
            return CommonResult.success(null, "문자로 인증 번호를 보냈습니다");
        }
        return CommonResult.success(authCode,"인증 코드 발급 성공");
    }

    @Operation(summary = "회원 비밀번호 변경")
    @RequestMapping(value = "/updatePassword", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult updatePassword(@RequestParam String telephone,
                                 @RequestParam String password,
                                 @RequestParam String authCode) {
        memberService.updatePassword(telephone,password,authCode);
        return CommonResult.success(null,"비밀번호 수정 성공");
    }


    @Operation(summary = "갱신 토큰으로 새 토큰 받기(다온마켓 4-8: 쓰면 새 갱신 토큰으로 바뀐다)")
    @RequestMapping(value = "/refreshToken", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult rotate(@RequestParam String refreshToken) {
        TokenService.Rotated r = tokenService.rotate(refreshToken);
        if (r == null) {
            return CommonResult.unauthorized(null);
        }
        UserDetails user = memberService.loadUserByUsername(r.username());
        Map<String, String> tokenMap = new HashMap<>();
        tokenMap.put("token", jwtTokenUtil.generateToken(user, r.authTime()));   // auth_time은 그대로(4-7 재인증 의미를 지킨다)
        tokenMap.put("tokenHead", tokenHead);
        tokenMap.put("refreshToken", r.refreshToken());
        return CommonResult.success(tokenMap);
    }

    @Operation(summary = "로그아웃(다온마켓 4-8: 액세스 토큰 폐기 + 갱신 토큰 계열 끊기)")
    @RequestMapping(value = "/logout", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult logout(HttpServletRequest request, @RequestParam(required = false) String refreshToken) {
        String token = jwtTokenUtil.stripHead(request.getHeader(tokenHeader));
        if (token != null) {
            tokenService.logout(jwtTokenUtil.getJti(token), jwtTokenUtil.getRemainingSeconds(token), refreshToken);
        }
        return CommonResult.success(null);
    }

    @Operation(summary = "새로고침token")
    @RequestMapping(value = "/refreshToken", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult refreshToken(HttpServletRequest request) {
        if (SecureKitSwitch.on("token-revoke")) {
            return CommonResult.failed("갱신 토큰으로 POST /sso/refreshToken 을 부르세요");   // 만료 전이면 계속 새로 주던 방식은 끈다
        }
        String token = request.getHeader(tokenHeader);
        String refreshToken = memberService.refreshToken(token);
        if (refreshToken == null) {
            return CommonResult.failed("token이미 만료되었습니다!");
        }
        Map<String, String> tokenMap = new HashMap<>();
        tokenMap.put("token", refreshToken);
        tokenMap.put("tokenHead", tokenHead);
        return CommonResult.success(tokenMap);
    }
}
