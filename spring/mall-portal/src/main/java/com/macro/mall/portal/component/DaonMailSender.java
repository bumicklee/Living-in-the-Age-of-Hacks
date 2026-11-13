package com.macro.mall.portal.component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

/**
 * [교육용 추가] 다온마켓 메일 보내기(랩의 가짜 메일 Mailpit, SMTP mailpit:1025, 인증 없음).
 *
 * 원본에는 메일 기능이 없다. 의존성을 늘리지 않으려고 SMTP 몇 줄을 직접 쓴다(랩 부품).
 * 회사 서비스에서는 spring-boot-starter-mail(JavaMailSender)이나 메일 발송사 SDK를 쓴다.
 * 받는 주소는 랩 회원 규칙 &lt;아이디&gt;@daon.example(Express 시드와 같은 규칙)이고, 랩 밖으로는 나가지 않는다.
 */
@Component
public class DaonMailSender {
    private static final Logger LOG = LoggerFactory.getLogger(DaonMailSender.class);
    private static final String FROM = "no-reply@daon.example";

    @Value("${daon.smtp.host:mailpit}")
    private String host;
    @Value("${daon.smtp.port:1025}")
    private int port;

    /** 랩 회원 메일 주소(시드 규칙). 회원 표에 메일 칸이 없어서 아이디로 만든다 */
    public static String addressOf(String username) {
        return username + "@daon.example";
    }

    /** 보내고 true. 실패하면 로그만 남기고 false(메일이 안 가도 요청은 같은 응답을 준다) */
    public boolean send(String to, String subject, String text) {
        if (!to.endsWith("@daon.example")) {
            LOG.warn("랩 메일은 *.daon.example로만 보냅니다");
            return false;
        }
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 2000);
            s.setSoTimeout(3000);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
            Writer out = new OutputStreamWriter(s.getOutputStream(), StandardCharsets.US_ASCII);
            expect(in, "220");
            cmd(out, in, "HELO daon-market", "250");
            cmd(out, in, "MAIL FROM:<" + FROM + ">", "250");
            cmd(out, in, "RCPT TO:<" + to + ">", "250");
            cmd(out, in, "DATA", "354");
            String date = ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.US));
            StringBuilder m = new StringBuilder();
            m.append("From: =?UTF-8?B?").append(b64("다온마켓")).append("?= <").append(FROM).append(">\r\n");
            m.append("To: <").append(to).append(">\r\n");
            m.append("Subject: =?UTF-8?B?").append(b64(subject)).append("?=\r\n");
            m.append("Date: ").append(date).append("\r\n");
            m.append("Message-ID: <").append(UUID.randomUUID()).append("@daon.example>\r\n");
            m.append("MIME-Version: 1.0\r\n");
            m.append("Content-Type: text/plain; charset=UTF-8\r\n");
            m.append("Content-Transfer-Encoding: base64\r\n\r\n");
            String body = Base64.getMimeEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
            m.append(body).append("\r\n.\r\n");
            out.write(m.toString());
            out.flush();
            expect(in, "250");
            cmd(out, in, "QUIT", "221");
            return true;
        } catch (IOException | IllegalStateException e) {
            LOG.warn("메일을 보내지 못했습니다({}:{}): {}", host, port, e.getMessage());
            return false;
        }
    }

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static void cmd(Writer out, BufferedReader in, String line, String code) throws IOException {
        out.write(line + "\r\n");
        out.flush();
        expect(in, code);
    }

    private static void expect(BufferedReader in, String code) throws IOException {
        String line;
        do {
            line = in.readLine();
            if (line == null) {
                throw new IOException("SMTP 연결이 끊겼습니다");
            }
        } while (line.length() > 3 && line.charAt(3) == '-');   // 여러 줄 응답(250-...)
        if (!line.startsWith(code)) {
            throw new IllegalStateException("SMTP 응답 " + line);
        }
    }
}
