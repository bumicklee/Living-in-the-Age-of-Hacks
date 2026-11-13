package com.macro.mall.security.password;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.security.event.SecurityEventLogger;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 다온마켓 4-2: 비밀번호 정책. 길이와 유출 목록만 본다. 문자 종류(대문자·특수문자)는 강제하지 않는다.
 *
 * - 길이 8자 이상 64자 이하, UTF-8로 72바이트 이하. bcrypt는 72바이트 뒤를 잘라 버린다(한글은 한 글자 3바이트).
 * - 유출 목록은 앱이 뜰 때 한 번 읽어 SHA-1 집합으로 둔다(요청마다 파일을 읽지 않는다). 외부 API를 부르지 않는다.
 *   랩 목록은 seed/password/breached-top.txt(가짜 목록, tools/gen_password_list.py). 비교는 소문자로 바꾼 값으로 한다.
 * - 로그에는 비밀번호를 넣지 않는다. target에는 회원 ID나 경로만.
 */
@Component
public class PasswordPolicy {
    private static final Logger LOG = LoggerFactory.getLogger(PasswordPolicy.class);
    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 64;
    public static final int MAX_BYTES = 72;

    @Value("${daon.password.breached-list:/daon/password/breached-top.txt}")
    private String listPath;

    private volatile Set<String> breached = Set.of();

    @PostConstruct
    void load() {
        long t0 = System.nanoTime();
        Path p = Path.of(listPath);
        try {
            List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
            Set<String> set = new HashSet<>(lines.size() * 2);
            for (String line : lines) {
                String v = line.strip();
                if (!v.isEmpty() && !v.startsWith("#")) {
                    set.add(sha1(v.toLowerCase(Locale.ROOT)));
                }
            }
            breached = Set.copyOf(set);
            LOG.info("[4-2] 유출 비밀번호 목록 {}개를 읽었습니다({}ms, {})", set.size(), (System.nanoTime() - t0) / 1_000_000, p);
        } catch (IOException e) {
            // 목록이 없어도 길이 규칙은 그대로 건다. 운영에서는 이 줄이 보이면 배포 사고다
            LOG.warn("[4-2] 유출 비밀번호 목록을 읽지 못했습니다({}). 길이 규칙만 적용합니다", p);
        }
    }

    /** 가입·비밀번호 변경 앞에서 부른다. 어기면 Asserts.fail(응답 code 500, 문구 하나) */
    public void check(String password, String target) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH
                || password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            Asserts.fail("비밀번호는 8자 이상 64자 이하로 정해 주세요");
        }
        if (isBreached(password)) {
            // 정상 사용자도 겪는 일이라 거부지만 INFO로 남긴다(부록 C 「거부는 WARN」의 예외)
            SecurityEventLogger.suspicious("auth.password.rejected_breached", "4-2")
                    .target(target)
                    .reason("유출 목록에 있는 비밀번호로 가입·변경 시도")
                    .log();
            Asserts.fail("다른 곳에서 유출된 적 있는 비밀번호입니다");
        }
    }

    public boolean isBreached(String password) {
        return password != null && breached.contains(sha1(password.toLowerCase(Locale.ROOT)));
    }

    /** 로그인 성공 직후(평문이 아직 메모리에 있을 때) 한 번 대조한다. 이 계정은 비밀번호 변경을 권할 후보가 된다 */
    public void noteLogin(String password, String member) {
        if (isBreached(password)) {
            SecurityEventLogger.suspicious("auth.login.breached_password_in_use", "4-2")
                    .actor(member)
                    .target(member)
                    .reason("로그인은 성공했지만 비밀번호가 유출 목록에 있음")
                    .log();
        }
    }

    private static String sha1(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
