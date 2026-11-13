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
import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.password.PasswordPolicy;
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
        example.or(example.createCriteria().andPhoneEqualTo(telephone));
        List<UmsMember> umsMembers = memberMapper.selectByExample(example);
        if (!CollectionUtils.isEmpty(umsMembers)) {
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
        umsMember.setPassword(null);
    }

    @Override
    public String generateAuthCode(String telephone) {
        // 여기서는 인증 코드 생성 로직을 모의로 구현했으며, 실제 환경에서는 SMS 서비스를 연동해야 함(SMS4J 사용 가능)
        // ✏️ 4-5: 번호를 예측할 수 있는 java.util.Random으로 만들고, 문자 대신 돌려준다(컨트롤러가 응답에 싣는다)(다온마켓 교육용 표시)
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
        UmsMemberExample example = new UmsMemberExample();
        example.createCriteria().andPhoneEqualTo(telephone);
        List<UmsMember> memberList = memberMapper.selectByExample(example);
        // ✏️ 4-4: 없는 전화번호면 다른 문구로 답해 회원인지 알려 준다(다온마켓 교육용 표시)
        if(CollectionUtils.isEmpty(memberList)){
            Asserts.fail("존재하지 않는 계정입니다");
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
        // ✏️ 4-3: 실패를 세지 않는다. 몇 번을 틀려도, 계정을 바꿔 가며 넣어도 같은 속도로 받는다(다온마켓 교육용 표시)
        try {
            // ✏️ 4-4: 없는 회원은 여기서 바로 예외가 나 아래 bcrypt 비교를 건너뛴다(응답이 빠르다)(다온마켓 교육용 표시)
            UserDetails userDetails = loadUserByUsername(username);
            if(!passwordEncoder.matches(password,userDetails.getPassword())){
                throw new BadCredentialsException("비밀번호가 올바르지 않습니다");
            }
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            token = jwtTokenUtil.generateToken(userDetails);
            // 다온마켓 4-2 ⑤ 탐지: 맞는 비밀번호지만 유출 목록에 있다(평문이 메모리에 있는 지금 한 번만 본다)
            if (SecureKitSwitch.on("password-policy")) {
                passwordPolicy.noteLogin(password, "member:" + ((MemberDetails) userDetails).getUmsMember().getId());
            }
        } catch (AuthenticationException e) {
            LOGGER.warn("로그인 예외:{}", e.getMessage());
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
        // ✏️ 4-5: 맞아도 번호를 지우지 않고, 틀린 횟수를 세지 않고, String.equals로 비교한다(다온마켓 교육용 표시)
        String realAuthCode = memberCacheService.getAuthCode(telephone);
        return authCode.equals(realAuthCode);
    }

}
