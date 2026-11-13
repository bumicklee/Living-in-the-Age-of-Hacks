package io.github.securekit.web.error;

import io.github.securekit.core.SecureKitException;
import io.github.securekit.core.json.MiniJson;
import io.github.securekit.web.KitContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** 필터(컨트롤러 밖)에서 막을 때 오류 본문을 쓴다. 모양은 컨트롤러 쪽과 같다 */
public final class KitErrorWriter {
    private KitErrorWriter() {
    }

    public static void write(HttpServletRequest request, HttpServletResponse response, SecureKitException e) throws IOException {
        if (response.isCommitted()) return;
        response.resetBuffer();
        response.setStatus(e.status());
        for (Map.Entry<String, String> h : e.headers().entrySet()) response.setHeader(h.getKey(), h.getValue());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        byte[] body = MiniJson.write(e.body(KitContext.traceId(request))).getBytes(StandardCharsets.UTF_8);
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
        response.flushBuffer();
    }
}
