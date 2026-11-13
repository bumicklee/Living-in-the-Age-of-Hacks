package com.macro.mall.security.limits;

import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.securekit.SecureKitSwitch;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * [다온마켓 9-3 정답] 요청 본문 크기 상한(기본 1MB, DAON_MAX_BODY_BYTES). 넘으면 끝까지 읽지 않고 413.
 *
 * - Content-Length가 있으면 그 값으로 먼저 거절한다.
 * - Content-Length가 없는 청크 전송은 상한 + 1바이트까지만 읽어 본다(헤더만 믿으면 청크 전송으로 빠져나간다).
 * - 파일 업로드(multipart)는 3-6의 상한(멀티파트 5MB·코드 2MB)이 맡는다. 폼 전송은 Tomcat의 폼 상한이 맡는다.
 * - 상한은 두 겹: 게이트웨이(nginx client_max_body_size)와 앱. 이 필터는 앱 쪽 겹이다.
 * - 거절한 뒤 남은 본문은 Tomcat이 server.tomcat.max-swallow-size(기본 2MB)까지만 읽어 버리고, 그보다 크면 연결을 끊는다.
 * - SecureKitSwitch.on("input-limits") 줄은 영상용 랩 스위치다.
 */
public class RequestSizeLimitFilter extends OncePerRequestFilter {
    static final long MAX = maxBytes();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ct = request.getContentType() == null ? "" : request.getContentType().toLowerCase();
        if (!SecureKitSwitch.on("input-limits") || ct.startsWith("multipart/") || ct.startsWith("application/x-www-form-urlencoded")) {
            chain.doFilter(request, response);
            return;
        }
        long declared = request.getContentLengthLong();
        if (declared > MAX) {
            reject(request, response, declared);
            return;
        }
        boolean chunked = declared < 0 && "chunked".equalsIgnoreCase(request.getHeader("Transfer-Encoding"));
        if (!chunked) {
            chain.doFilter(request, response);
            return;
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        InputStream in = request.getInputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = in.read(chunk)) != -1) {
            buf.write(chunk, 0, n);
            if (buf.size() > MAX) {
                reject(request, response, -1);
                return;
            }
        }
        chain.doFilter(new Cached(request, buf.toByteArray()), response);
    }

    private static void reject(HttpServletRequest request, HttpServletResponse response, long size) throws IOException {
        SecurityEventLogger.denied("input.denied.too_large", "9-3")
                .request(request)
                .target("path:" + safePath(request.getRequestURI()))
                .reason("본문 크기 상한(" + (MAX / 1024) + "KB) 초과(" + bucket(size) + ")")
                .log();
        response.setStatus(413);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().println("{\"code\":413,\"message\":\"요청 본문이 너무 큽니다(상한 " + (MAX / 1024) + "KB)\"}");
        response.getWriter().flush();
    }

    /** 실제 바이트 수 대신 구간만 남긴다 */
    static String bucket(long size) {
        if (size < 0) {
            return "청크 전송, 상한 넘음";
        }
        long mb = 1024 * 1024;
        return size <= 10 * mb ? "1~10MB" : size <= 100 * mb ? "10~100MB" : "100MB 넘음";
    }

    static String safePath(String uri) {
        String p = uri == null ? "" : uri.replaceAll("[^A-Za-z0-9/._-]", "");
        return p.length() > 120 ? p.substring(0, 120) : p;
    }

    private static long maxBytes() {
        try {
            String v = System.getenv("DAON_MAX_BODY_BYTES");
            return v == null ? 1024 * 1024 : Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return 1024 * 1024;
        }
    }

    /** 미리 읽은 본문을 다시 읽게 해 주는 감싸개 */
    static final class Cached extends HttpServletRequestWrapper {
        private final byte[] body;

        Cached(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public int read() {
                    return in.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return in.read(b, off, len);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }
}
