package com.macro.mall.portal.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.mapper.UmsMemberMapper;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.component.DaonMailSender;
import com.macro.mall.portal.service.DaonPasswordResetService;
import com.macro.mall.portal.service.UmsMemberCacheService;
import com.macro.mall.portal.service.UmsMemberService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * [교육용 추가] 다온마켓 재설정 링크. 토큰은 Redis pwreset:&lt;토큰&gt; → 회원 ID.
 */
@Service
public class DaonPasswordResetServiceImpl implements DaonPasswordResetService {
    @Autowired
    private UmsMemberService memberService;
    @Autowired
    private UmsMemberMapper memberMapper;
    @Autowired
    private UmsMemberCacheService memberCacheService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private DaonMailSender mail;
    @Autowired
    private HttpServletRequest request;

    @Override
    public void sendLink(String username) {
        UmsMember member = memberService.getByUsername(username);
        if (member == null) {
            return;                                   // 없는 아이디여도 같은 응답(회원 여부를 알려 주지 않는다)
        }
        // ✏️ 4-5: 토큰 원문을 그대로 저장하고, 24시간 동안 몇 번이든 쓸 수 있다(다온마켓 교육용 표시)
        String token = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set("pwreset:" + token, String.valueOf(member.getId()), Duration.ofHours(24));
        // ✏️ 4-5: 링크 호스트를 요청의 Host 헤더에서 가져온다(다온마켓 교육용 표시)
        String base = request.getScheme() + "://" + request.getHeader("Host");
        String link = base + "/reset?token=" + token;
        mail.send(DaonMailSender.addressOf(member.getUsername()), "[다온마켓] 비밀번호 재설정 링크",
                member.getNickname() + "님, 아래 링크에서 새 비밀번호를 정해 주세요.\n\n" + link
                        + "\n\n요청하지 않았다면 이 메일을 지우세요.\n(교육용 랩의 가짜 메일입니다)");
    }

    @Override
    public void reset(String token, String password) {
        String id = token == null ? null : redis.opsForValue().get("pwreset:" + token);
        if (id == null) {
            Asserts.fail("링크가 올바르지 않거나 만료되었습니다");
        }
        UmsMember record = new UmsMember();
        record.setId(Long.valueOf(id));
        record.setPassword(passwordEncoder.encode(password));
        memberMapper.updateByPrimaryKeySelective(record);
        memberCacheService.delMember(record.getId());
    }
}
