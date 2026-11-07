package com.macro.mall.daon;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;

/**
 * [교육용 추가] 이미지 URL을 받아 오는 부분. 수업 2-5(T04).
 */
@Component
public class DaonImageFetcher {
    static final int MAX_BYTES = 5 * 1024 * 1024;

    public record Fetched(String contentType, byte[] body) {
    }

    public Fetched fetch(String url) throws IOException {
        // ✏️ 2-5: 받은 주소를 그대로 부른다. 어디로 나가는지 확인하지 않는다(다온마켓 교육용 표시)
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(5000);
        try (InputStream in = conn.getInputStream()) {
            return new Fetched(conn.getContentType(), in.readNBytes(MAX_BYTES));
        } finally {
            conn.disconnect();
        }
    }
}
