package io.github.securekit.web.filter;

import io.github.securekit.core.SecureKitException;
import io.github.securekit.core.event.SecurityEvents;
import io.github.securekit.web.error.KitErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * 요청 본문 크기 상한(9-3). 설정 secure-kit.limits.max-body(기본 1MB), 경로별 secure-kit.limits.paths.
 *
 * - Content-Length가 상한을 넘으면 읽기 전에 413 + input.denied.too_large.
 * - 길이를 밝히지 않은(chunked) 본문은 읽으면서 세다가 넘는 순간 멈춘다.
 */
public class RequestSizeLimitFilter extends OncePerRequestFilter {
    private final long maxBody;
    private final Map<String, Long> paths;

    public RequestSizeLimitFilter(long maxBody, Map<String, Long> paths) {
        this.maxBody = maxBody;
        this.paths = Map.copyOf(paths);
    }

    long limitFor(String uri) {
        Long exact = paths.get(uri);
        if (exact != null) return exact;
        for (Map.Entry<String, Long> e : paths.entrySet()) {
            if (e.getKey().endsWith("/**") && uri.startsWith(e.getKey().substring(0, e.getKey().length() - 3))) return e.getValue();
        }
        return maxBody;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long limit = limitFor(request.getRequestURI());
        long declared = request.getContentLengthLong();
        if (declared > limit) {
            KitErrorWriter.write(request, response, tooLarge(request, declared, limit));
            return;
        }
        chain.doFilter(declared >= 0 ? request : new Counting(request, limit), response);
    }

    static SecureKitException tooLarge(HttpServletRequest request, long size, long limit) {
        SecurityEvents.warn("input.denied.too_large", "limits", "path:" + request.getRequestURI(),
                "본문 " + size + "바이트 > 상한 " + limit + "바이트");
        return new SecureKitException(413, "요청이 너무 큽니다");
    }

    /** 길이를 모르는 본문: 읽은 만큼 세다가 상한을 넘으면 예외 */
    private static final class Counting extends HttpServletRequestWrapper {
        private final long limit;
        private ServletInputStream stream;

        Counting(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream != null) return stream;
            ServletInputStream in = super.getInputStream();
            HttpServletRequest req = (HttpServletRequest) getRequest();
            stream = new ServletInputStream() {
                long read;

                private void count(long n) {
                    read += n;
                    if (read > limit) throw tooLarge(req, read, limit);
                }

                @Override
                public int read() throws IOException {
                    int b = in.read();
                    if (b >= 0) count(1);
                    return b;
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    int n = in.read(b, off, len);
                    if (n > 0) count(n);
                    return n;
                }

                @Override
                public boolean isFinished() {
                    return in.isFinished();
                }

                @Override
                public boolean isReady() {
                    return in.isReady();
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    in.setReadListener(listener);
                }
            };
            return stream;
        }
    }
}
