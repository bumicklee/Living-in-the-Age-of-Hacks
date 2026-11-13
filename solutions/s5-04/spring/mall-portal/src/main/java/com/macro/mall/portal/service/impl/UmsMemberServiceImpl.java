package com.macro.mall.portal.service.impl;

import cn.hutool.core.util.StrUtil;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.mapper.UmsMemberLevelMapper;
import com.macro.mall.mapper.UmsMemberMapper;
import com.macro.mall.model.UmsMember;
import com.macro.mall.model.UmsMemberExample;
import com.macro.mall.model.UmsMemberLevel;
import com.macro.mall.model.UmsMemberLevelExample;
import com.macro.mall.portal.domain.MemberDetails;
import com.macro.mall.portal.domain.UmsMemberUpdateParam;
import com.macro.mall.portal.service.UmsMemberCacheService;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.security.crypto.PhoneIndex;
import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.password.PasswordPolicy;
import com.macro.mall.security.ratelimit.LoginRateLimiter;
import com.macro.mall.security.ratelimit.UnknownAccountWatch;
import com.macro.mall.security.securekit.SecureKitSwitch;
import com.macro.mall.security.util.JwtTokenUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Date;
import java.util.List;
import java.util.Random;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.macro.mall.portal.component.DaonSmsSender;

/**
 * 회원 관리 Service 구현 클래스
 * Created by macro on 2018/8/3.
 */
@Service
public class UmsMemberServiceImpl implements UmsMemberService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UmsMemberServiceImpl.class);
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private LoginRateLimiter loginRateLimiter;
    @Autowired
    private PhoneIndex phoneIndex;
    @Autowired
    private UnknownAccountWatch unknownAccountWatch;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private DaonSmsSender sms;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Duration CODE_TTL = Duration.ofSeconds(90);
    private static final int CODE_MAX_FAILS = 5;
    @Autowired
    private JwtTokenUtil jwtTokenUtil;
    @Autowired
    private UmsMemberMapper memberMapper;
    @Autowired
    private UmsMemberLevelMapper memberLevelMapper;
    @Autowired
    private UmsMemberCacheService memberCacheService;
    @Autowired
    private PasswordPolicy passwordPolicy;
    @Value("${redis.key.authCode}")
    private String REDIS_KEY_PREFIX_AUTH_CODE;
    @Value("${redis.expire.authCode}")
    private Long AUTH_CODE_EXPIRE_SECONDS;

    @Override
    public UmsMember getByUsername(String username) {
        UmsMember member = memberCacheService.getMember(username);
        if(member!=null) return member;
        UmsMemberExample example = new UmsMemberExample();
        example.createCriteria().andUsernameEqualTo(username);
        List<UmsMember> memberList = memberMapper.selectByExample(example);
        if (!CollectionUtils.isEmpty(memberList)) {
            member = memberList.get(0);
            memberCacheService.setMember(member);
            return member;
        }
        return null;
    }

    @Override
    public UmsMember getById(Long id) {
        return memberMapper.selectByPrimaryKey(id);
    }

    @Override
    public void register(String username, String password, String telephone, String authCode) {
        // 다온마켓 4-2: 길이와 유출 목록부터 본다(인증 번호를 쓰기 전에. 거절돼도 번호를 다시 받지 않게)
        if (SecureKitSwitch.on("password-policy")) {
            passwordPolicy.check(password, "path:/sso/register");
        }
        //인증 코드 검증
        if(!verifyAuthCode(authCode,telephone)){
            Asserts.fail("인증 코드 오류");
        }
        //이미 있는 사용자인지 조회
        UmsMemberExample example = new UmsMemberExample();
        example.createCriteria().andUsernameEqualTo(username);
        boolean masked = SecureKitSwitch.on("pii-mask");
        if (!masked) {
            example.or(example.createCriteria().andPhoneEqualTo(telephone));   // 5-3 완료 상태: 암호문과 비교해 못 찾는다
        }
        List<UmsMember> umsMembers = memberMapper.selectByExample(example);
        // 다온마켓 5-4: 번호는 검색용 값(키를 쓴 HMAC)으로 찾는다
        boolean phoneTaken = masked && phoneIndex.findMemberId(telephone).isPresent();
        if (!CollectionUtils.isEmpty(umsMembers) || phoneTaken) {
            Asserts.fail("이미 존재하는 사용자입니다");
        }
        //해당 사용자가 없어 추가 작업을 수행하지 않음
        UmsMember umsMember = new UmsMember();
        umsMember.setUsername(username);
        umsMember.setPhone(telephone);
        umsMember.setPassword(passwordEncoder.encode(password));
        umsMember.setCreateTime(new Date());
        umsMember.setStatus(1);
        //기본 회원 등급 조회 후 설정
        UmsMemberLevelExample levelExample = new UmsMemberLevelExample();
        levelExample.createCriteria().andDefaultStatusEqualTo(1);
        List<UmsMemberLevel> memberLevelList = memberLevelMapper.selectByExample(levelExample);
        if (!CollectionUtils.isEmpty(memberLevelList)) {
            umsMember.setMemberLevelId(memberLevelList.get(0).getId());
        }
        memberMapper.insert(umsMember);
        if (masked) {
            phoneIndex.indexByUsername(username, telephone);   // 다온마켓 5-4: 검색 칸도 함께 채운다
        }
        umsMember.setPassword(null);
    }

    @Override
    public String generateAuthCode(String telephone) {
        // 여기서는 인증 코드 생성 로직을 모의로 구현했으며, 실제 환경에서는 SMS 서비스를 연동해야 함(SMS4J 사용 가능)
        // 다온마켓 4-5: SecureRandom으로 만들고 문자로만 보낸다. 응답에는 싣지 않는다(컨트롤러). 다시 받으면 실패 횟수도 처음부터
        if (SecureKitSwitch.on("reset-flow")) {
            String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
            redis.opsForValue().set("pwreset:sms:" + telephone, code, CODE_TTL);
            redis.delete("rl:sms:fail:" + telephone);
            sms.send(telephone, "[다온마켓] 인증 번호 " + code + " (90초 안에 입력, 교육용 랩의 가짜 문자)");
            return null;
        }
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for(int i=0;i<6;i++){
            sb.append(random.nextInt(10));
        }
        memberCacheService.setAuthCode(telephone,sb.toString());
        return sb.toString();
    }

    @Override
    public void updatePassword(String telephone, String password, String authCode) {
        List<UmsMember> memberList;
        if (SecureKitSwitch.on("pii-mask")) {      // 다온마켓 5-4: 검색용 값으로 회원 번호를 찾고, 그 회원을 읽는다
            memberList = phoneIndex.findMemberId(telephone).map(id -> List.of(memberMapper.selectByPrimaryKey(id))).orElse(List.of());
        } else {
            UmsMemberExample example = new UmsMemberExample();
            example.createCriteria().andPhoneEqualTo(telephone);   // 5-3 완료 상태: 암호문과 비교해 못 찾는다
            memberList = memberMapper.selectByExample(example);
        }
        // 다온마켓 4-4: 없는 전화번호도 인증 번호가 틀렸을 때와 같은 문구(회원인지 드러내지 않는다). 흐름은 4-5에서 고친다
        if(CollectionUtils.isEmpty(memberList)){
            Asserts.fail(SecureKitSwitch.on("login-timing") ? "인증 코드 오류" : "존재하지 않는 계정입니다");
        }
        // 다온마켓 4-2: 새 비밀번호도 같은 규칙(인증 번호를 쓰기 전에)
        if (SecureKitSwitch.on("password-policy")) {
            passwordPolicy.check(password, "member:" + memberList.get(0).getId());
        }
        //인증 코드 검증
        if(!verifyAuthCode(authCode,telephone)){
            Asserts.fail("인증 코드 오류");
        }
        UmsMember umsMember = memberList.get(0);
        umsMember.setPassword(passwordEncoder.encode(password));
        memberMapper.updateByPrimaryKeySelective(umsMember);
        memberCacheService.delMember(umsMember.getId());
    }

    @Override
    public UmsMember getCurrentMember() {
        SecurityContext ctx = SecurityContextHolder.getContext();
        Authentication auth = ctx.getAuthentication();
        MemberDetails memberDetails = (MemberDetails) auth.getPrincipal();
        return memberDetails.getUmsMember();
    }

    @Override
    public void updateIntegration(Long id, Integer integration) {
        UmsMember record=new UmsMember();
        record.setId(id);
        record.setIntegration(integration);
        memberMapper.updateByPrimaryKeySelective(record);
        memberCacheService.delMember(id);
    }

    @Override
    public void updateInfo(UmsMemberUpdateParam param) {
        UmsMember current = getCurrentMember();
        if (!SecureKitSwitch.on("request-dto")) {
            updateInfoUnchecked(current, param);   // [랩 스위치] secure-kit.request-dto.enabled=false: 고치기 전 모습(영상용)
            return;
        }
        // 다온마켓 2-4: 허용한 항목만 새 객체에 옮긴다. 등급·포인트·상태는 이 경로로 바뀌지 않는다
        UmsMember record = new UmsMember();
        record.setId(current.getId());
        record.setNickname(param.getNickname());
        record.setGender(param.getGender());
        record.setBirthday(param.getBirthday());
        record.setCity(param.getCity());
        record.setJob(param.getJob());
        record.setPersonalizedSignature(param.getPersonalizedSignature());
        memberCacheService.delMember(current.getId());
        memberMapper.updateByPrimaryKeySelective(record);
        // 다온마켓 2-4 ⑤ 탐지: 허용 밖 항목(등급·포인트 등)을 보낸 요청은 정상 처리하되 의심 이벤트로 남긴다(항목 이름만)
        if (!param.getIgnoredFields().isEmpty()) {
            SecurityEventLogger.suspicious("input.unexpected_fields", "2-4")
                    .actor("member:" + current.getId())
                    .target("member:" + current.getId())
                    .reason("허용 밖 항목 무시: " + String.join(",", param.getIgnoredFields()))
                    .log();
        }
    }

    // 고치기 전 모습: 본문에 들어온 항목을 모두 회원 엔티티로 옮겨 저장한다. 랩 스위치를 껐을 때만 쓴다
    private void updateInfoUnchecked(UmsMember current, UmsMemberUpdateParam param) {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        java.util.Map<String, Object> all = new java.util.HashMap<>();
        all.put("nickname", param.getNickname());
        all.put("gender", param.getGender());
        all.put("birthday", param.getBirthday());
        all.put("city", param.getCity());
        all.put("job", param.getJob());
        all.put("personalizedSignature", param.getPersonalizedSignature());
        all.putAll(param.getIgnoredValues());
        UmsMember member = mapper.convertValue(all, UmsMember.class);
        member.setId(current.getId());
        memberCacheService.delMember(current.getId());
        memberMapper.updateByPrimaryKeySelective(member);
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        UmsMember member = getByUsername(username);
        if(member!=null){
            return new MemberDetails(member);
        }
        throw new UsernameNotFoundException("사용자 이름 또는 비밀번호 오류");
    }

    @Override
    public String login(String username, String password) {
        String token = null;
        //비밀번호는 클라이언트에서 암호화한 뒤 전달해야 함
        // 다온마켓 4-3: 계정·IP·기기 세 축의 실패 제한. 넘으면 HTTP 429(LoginRateLimitHandler)
        loginRateLimiter.check(username);
        try {
            // 다온마켓 4-4: 없는 회원도 더미 해시와 한 번 비교한다(있는 회원과 걸리는 시간을 맞춘다)
            UserDetails userDetails;
            try {
                userDetails = loadUserByUsername(username);
            } catch (UsernameNotFoundException e) {
                unknownAccountWatch.equalizeAndCount(password);
                throw e;
            }
            if(!passwordEncoder.matches(password,userDetails.getPassword())){
                throw new BadCredentialsException("비밀번호가 올바르지 않습니다");
            }
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            token = jwtTokenUtil.generateToken(userDetails);
            loginRateLimiter.succeeded(username);
            // 다온마켓 4-2 ⑤ 탐지: 맞는 비밀번호지만 유출 목록에 있다(평문이 메모리에 있는 지금 한 번만 본다)
            if (SecureKitSwitch.on("password-policy")) {
                passwordPolicy.noteLogin(password, "member:" + ((MemberDetails) userDetails).getUmsMember().getId());
            }
        } catch (AuthenticationException e) {
            LOGGER.warn("로그인 예외:{}", e.getMessage());
            loginRateLimiter.failed(username);
        }
        return token;
    }

    @Override
    public String refreshToken(String token) {
        // ✏️ 4-8: 만료 전이면 언제든 새 7일 토큰을 준다. 헌 토큰도 그대로 살아 있다(다온마켓 교육용 표시)
        return jwtTokenUtil.refreshHeadToken(token);
    }

    //입력한 인증 코드 검증
    private boolean verifyAuthCode(String authCode, String telephone){
        if(StrUtil.isEmpty(authCode)){
            return false;
        }
        // 다온마켓 4-5: 맞으면 바로 지운다(1회용). 틀리면 세고 5번째에 번호를 버린다. 비교는 MessageDigest.isEqual
        if (SecureKitSwitch.on("reset-flow")) {
            String real = redis.opsForValue().get("pwreset:sms:" + telephone);
            if (real == null) {
                return false;
            }
            if (MessageDigest.isEqual(real.getBytes(StandardCharsets.UTF_8), authCode.getBytes(StandardCharsets.UTF_8))) {
                redis.delete("pwreset:sms:" + telephone);
                redis.delete("rl:sms:fail:" + telephone);
                return true;
            }
            Long fails = redis.opsForValue().increment("rl:sms:fail:" + telephone);
            redis.expire("rl:sms:fail:" + telephone, Duration.ofMinutes(10));
            if (fails != null && fails >= CODE_MAX_FAILS) {
                redis.delete("pwreset:sms:" + telephone);
                SecurityEventLogger.denied("auth.reset.code_failed_burst", "4-5")
                        .actor("anonymous")
                        .target("phone:" + phoneRef(telephone))
                        .reason("인증 번호 " + fails + "번 실패, 번호 폐기")
                        .count(fails.intValue())
                        .log();
            }
            return false;
        }
        String realAuthCode = memberCacheService.getAuthCode(telephone);
        return authCode.equals(realAuthCode);
    }


    /** 이벤트에 전화번호 원문 대신 넣는 짧은 해시 */
    private static String phoneRef(String telephone) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(telephone.getBytes(StandardCharsets.UTF_8))).substring(0, 8);
        } catch (java.security.NoSuchAlgorithmException e) {
            return "unknown";
        }
    }
}
