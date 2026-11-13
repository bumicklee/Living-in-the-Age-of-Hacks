package com.macro.mall.portal.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.mapper.UmsMemberMapper;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.component.DaonMailSender;
import com.macro.mall.portal.service.DaonPasswordResetService;
import com.macro.mall.portal.service.UmsMemberCacheService;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.password.PasswordPolicy;
import com.macro.mall.security.securekit.SecureKitSwitch;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * [다온마켓 4-5 정답] 재설정 링크. 링크 주소는 설정값(daon.public-base-url) 하나로만 만들고,
 * 토큰은 32바이트 난수 · Redis에는 SHA-256 해시만 · 15분 · 한 번 쓰면 지운다. 바뀐 뒤 알림 메일.
 *
 * - 요청의 Host·X-Forwarded-Host는 링크에 쓰지 않는다. 설정과 다르면 auth.reset.host_mismatch만 남긴다.
 * - 쓴 토큰을 다시 내면 auth.reset.token_reused(15분 동안 「쓴 토큰」 표시를 남겨 둔다).
 * - SecureKitSwitch.on("reset-flow") 줄은 영상용 랩 스위치다. 손으로 고칠 때는 쓰지 않는다(else 쪽은 시작 코드 그대로).
 */
@Service
public class DaonPasswordResetServiceImpl implements DaonPasswordResetService {
    private static final Duration TTL = Duration.ofMinutes(15);
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private UmsMemberService memberService;
    @Autowired
    private UmsMemberMapper memberMapper;
    @Autowired
    private UmsMemberCacheService memberCacheService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private PasswordPolicy passwordPolicy;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private DaonMailSender mail;
    @Autowired
    private HttpServletRequest request;
    @Value("${daon.public-base-url:${DAON_PUBLIC_BASE_URL:http://127.0.0.1:8088}}")
    private String publicBaseUrl;

    @Override
    public void sendLink(String username) {
        if (!SecureKitSwitch.on("reset-flow")) {
            sendLinkStart(username);
            return;
        }
        noteHostMismatch();
        UmsMember member = memberService.getByUsername(username);
        if (member == null) {
            return;                                   // 없는 아이디여도 같은 응답
        }
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        redis.opsForValue().set("pwreset:" + sha256(token), String.valueOf(member.getId()), TTL);
        String link = publicBaseUrl.replaceAll("/+$", "") + "/reset?token=" + token;
        mail.send(DaonMailSender.addressOf(member.getUsername()), "[다온마켓] 비밀번호 재설정 링크",
                member.getNickname() + "님, 아래 링크에서 새 비밀번호를 정해 주세요. 링크는 15분 동안 한 번만 쓸 수 있습니다.\n\n" + link
                        + "\n\n요청하지 않았다면 이 메일을 지우세요.\n(교육용 랩의 가짜 메일입니다)");
    }

    @Override
    public void reset(String token, String password) {
        if (!SecureKitSwitch.on("reset-flow")) {
            resetStart(token, password);
            return;
        }
        String hash = token == null ? "" : sha256(token);
        String id = token == null ? null : redis.opsForValue().getAndDelete("pwreset:" + hash);   // 쓰는 순간 지운다(1회용)
        if (id == null) {
            if (token != null && Boolean.TRUE.equals(redis.hasKey("pwreset:used:" + hash))) {
                SecurityEventLogger.denied("auth.reset.token_reused", "4-5")
                        .actor("anonymous")
                        .target("token:used")
                        .reason("이미 쓴 재설정 토큰 재사용")
                        .log();
            }
            Asserts.fail("링크가 올바르지 않거나 만료되었습니다");
        }
        redis.opsForValue().set("pwreset:used:" + hash, id, TTL);
        if (SecureKitSwitch.on("password-policy")) {
            passwordPolicy.check(password, "member:" + id);
        }
        UmsMember record = new UmsMember();
        record.setId(Long.valueOf(id));
        record.setPassword(passwordEncoder.encode(password));
        memberMapper.updateByPrimaryKeySelective(record);
        memberCacheService.delMember(record.getId());
        UmsMember member = memberMapper.selectByPrimaryKey(record.getId());
        if (member != null) {
            mail.send(DaonMailSender.addressOf(member.getUsername()), "[다온마켓] 비밀번호가 바뀌었습니다",
                    member.getNickname() + "님, 방금 비밀번호가 바뀌었습니다. 직접 바꾸지 않았다면 고객센터로 알려 주세요.\n(교육용 랩의 가짜 메일입니다)");
        }
    }

    /** 요청 Host가 설정된 공개 주소와 다르면 기록만 한다(링크는 어차피 설정값으로 만든다) */
    private void noteHostMismatch() {
        String host = request.getHeader("Host");
        String expected = URI.create(publicBaseUrl).getAuthority();
        if (host != null && expected != null && !host.equalsIgnoreCase(expected)) {
            SecurityEventLogger.denied("auth.reset.host_mismatch", "4-5")
                    .actor("anonymous")
                    .target("path:/sso/reset/link")
                    .reason("요청 Host가 설정된 공개 주소와 다름")
                    .log();
        }
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- 아래는 시작 코드 그대로(랩 스위치를 끈 영상용) ----

    private void sendLinkStart(String username) {
        UmsMember member = memberService.getByUsername(username);
        if (member == null) {
            return;
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set("pwreset:" + token, String.valueOf(member.getId()), Duration.ofHours(24));
        String base = request.getScheme() + "://" + request.getHeader("Host");
        String link = base + "/reset?token=" + token;
        mail.send(DaonMailSender.addressOf(member.getUsername()), "[다온마켓] 비밀번호 재설정 링크",
                member.getNickname() + "님, 아래 링크에서 새 비밀번호를 정해 주세요.\n\n" + link
                        + "\n\n요청하지 않았다면 이 메일을 지우세요.\n(교육용 랩의 가짜 메일입니다)");
    }

    private void resetStart(String token, String password) {
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
