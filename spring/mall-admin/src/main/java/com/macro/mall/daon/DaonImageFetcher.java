package com.macro.mall.daon;

import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.securekit.SecureKitSwitch;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;

/**
 * [교육용 추가] 이미지 URL을 받아 오는 부분. 수업 2-5(T04).
 */
@Component
public class DaonImageFetcher {
    static final int MAX_BYTES = 5 * 1024 * 1024;

    // 다온마켓 2-5: 밖으로 나가는 길은 이그레스 프록시 하나다(A2, infra/compose.a02.yml이 egress:3128을 넣는다)
    @Value("${daon.egress.proxy:}")
    private String egressProxy;

    public record Fetched(String contentType, byte[] body) {
    }

    /** 막은 요청. 이유는 관리자 화면에 그대로 보인다 */
    static class Blocked extends IOException {
        Blocked(String message) {
            super(message);
        }
    }

    public Fetched fetch(String url) throws IOException {
        if (!SecureKitSwitch.on("egress")) {
            return fetchUnchecked(url);   // [랩 스위치] secure-kit.egress.enabled=false: 고치기 전 모습(영상용)
        }
        // 다온마켓 2-5: 부르기 전에 어디로 나가는지 확인한다
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            throw blocked("egress.denied.bad_url", url, "주소 형식이 올바르지 않습니다");
        }
        // 1) http·https만. file:, ftp:, jar: 같은 스킴은 받지 않는다
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw blocked("egress.denied.scheme", url, "http·https 주소만 가져옵니다");
        }
        if (uri.getHost() == null || uri.getUserInfo() != null) {
            throw blocked("egress.denied.bad_url", url, "주소 형식이 올바르지 않습니다");
        }
        // 2) 이름을 해석해서 내부 주소(루프백·사설·링크 로컬 등)면 거부한다
        for (InetAddress a : InetAddress.getAllByName(uri.getHost())) {
            if (isInternal(a)) {
                throw blocked("egress.denied.internal_address", url, "내부 주소로는 요청하지 않습니다");
            }
        }
        // 3) 프록시가 없으면 나가지 않는다(실패하면 막는다)
        if (egressProxy == null || egressProxy.isBlank() || !egressProxy.contains(":")) {
            throw blocked("egress.denied.no_proxy", url, "이그레스 프록시가 설정되지 않아 밖으로 나가지 않습니다");
        }
        String[] hp = egressProxy.trim().split(":");
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(hp[0], Integer.parseInt(hp[1])));
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection(proxy);
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(5000);
        // 4) 리다이렉트는 따라가지 않는다. 검사한 주소와 실제로 가는 주소가 달라진다(11-1에서 자세히)
        conn.setInstanceFollowRedirects(false);
        try {
            int status = conn.getResponseCode();
            String type = conn.getContentType();
            // 5) 이미지가 아니면 돌려주지 않는다(프록시가 거부한 403 화면도 여기서 걸린다)
            if (status != 200 || type == null || !type.toLowerCase().startsWith("image/")) {
                throw blocked("egress.denied.not_image", url, "이미지 응답이 아닙니다(HTTP " + status + ")");
            }
            try (InputStream in = conn.getInputStream()) {
                return new Fetched(type, in.readNBytes(MAX_BYTES));
            }
        } finally {
            conn.disconnect();
        }
    }

    // 고치기 전 모습: 받은 주소를 그대로 부른다. 랩 스위치를 껐을 때만 쓴다
    private Fetched fetchUnchecked(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(5000);
        try (InputStream in = conn.getInputStream()) {
            return new Fetched(conn.getContentType(), in.readNBytes(MAX_BYTES));
        } finally {
            conn.disconnect();
        }
    }

    static boolean isInternal(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress()
                || a.isSiteLocalAddress() || a.isMulticastAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (b.length == 4) {
            int x = b[0] & 0xff, y = b[1] & 0xff;
            return x == 0 || (x == 100 && (y & 0xc0) == 64) || (x == 198 && (y == 18 || y == 19));   // 0/8, 100.64/10, 198.18/15
        }
        return (b[0] & 0xfe) == 0xfc;   // IPv6 고유 로컬 fc00::/7
    }

    // 다온마켓 2-5 ⑤ 탐지: 막을 때마다 남긴다. 주소는 호스트 이름만 넣는다(경로·쿼리에는 값이 섞일 수 있다)
    private static Blocked blocked(String event, String url, String reason) {
        SecurityEventLogger.denied(event, "2-5")
                .target("host:" + hostOf(url))
                .reason(reason)
                .log();
        return new Blocked(reason);
    }

    private static String hostOf(String url) {
        try {
            String h = URI.create(url.trim()).getHost();
            return h == null ? "?" : h;
        } catch (IllegalArgumentException e) {
            return "?";
        }
    }
}
