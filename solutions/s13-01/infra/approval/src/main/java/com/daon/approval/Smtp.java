package com.daon.approval;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * [랩 도구] 아주 작은 SMTP 보내기(표준 라이브러리만). 랩의 가짜 메일 서버 Mailpit(mailpit:1025)에만 보낸다.
 * 회사에서는 사내 메일 발송 서비스·메시지 큐를 쓴다. 본문과 제목은 UTF-8(base64)로 보낸다.
 */
public final class Smtp {
    private Smtp() {
    }

    public static void send(String to, String subject, String text) throws IOException {
        String host = Bao.env("DAON_SMTP_HOST", "mailpit");
        int port = Integer.parseInt(Bao.env("DAON_SMTP_PORT", "1025"));
        String from = "approval@daon.example";
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 3000);
            s.setSoTimeout(5000);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
            OutputStream out = s.getOutputStream();
            expect(in, "220");
            cmd(out, in, "HELO approval.daon.example", "250");
            cmd(out, in, "MAIL FROM:<" + from + ">", "250");
            cmd(out, in, "RCPT TO:<" + to + ">", "250");
            cmd(out, in, "DATA", "354");
            String enc = "=?UTF-8?B?" + Base64.getEncoder().encodeToString(subject.getBytes(StandardCharsets.UTF_8)) + "?=";
            String msg = "From: <" + from + ">\r\nTo: <" + to + ">\r\nSubject: " + enc + "\r\n"
                    + "MIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n"
                    + Base64.getMimeEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)) + "\r\n.";
            cmd(out, in, msg, "250");
            cmd(out, in, "QUIT", "221");
        }
    }

    private static void cmd(OutputStream out, BufferedReader in, String line, String code) throws IOException {
        out.write((line + "\r\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
        expect(in, code);
    }

    private static void expect(BufferedReader in, String code) throws IOException {
        String line;
        do {
            line = in.readLine();
            if (line == null || !line.startsWith(code)) {
                throw new IOException("메일 서버 응답이 예상과 다릅니다: " + (line == null ? "끊김" : line.substring(0, Math.min(3, line.length()))));
            }
        } while (line.length() > 3 && line.charAt(3) == '-');
    }
}
