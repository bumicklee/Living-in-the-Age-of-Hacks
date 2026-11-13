package io.github.securekit.web.part;

import io.github.securekit.core.SecureKitException;
import io.github.securekit.core.event.SecurityEvents;
import io.github.securekit.core.net.AddressCheck;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 내부망으로 나가는 요청 막기(SSRF, 2-5). 바꿔 쓰기: RestTemplate·HttpClient 대신 safeHttpClient.get(url).
 *
 * 1. 스킴은 http·https만(file·gopher 등 → 400 egress.denied.scheme).
 * 2. 이름을 풀어 나온 주소 **전부**를 확인한다. 하나라도 내부망이면 403 egress.denied.internal_address.
 * 3. 확인한 그 주소로 직접 붙는다(확인 뒤 다시 이름을 풀면 DNS 리바인딩으로 바뀔 수 있다).
 * 4. 리다이렉트는 따라가지 않는다(3xx를 그대로 돌려준다). 응답 크기·시간 상한이 있다.
 *
 * 읽히게 하려고 HTTP/1.0 GET 한 가지만 직접 쓴다. 회사에서는 같은 규칙을 HTTP 클라이언트의 DNS 해석기에 넣는다.
 */
public class SafeHttpClient {
    private final int timeoutMillis;
    private final int maxBytes;

    public SafeHttpClient(int timeoutMillis, int maxBytes) {
        this.timeoutMillis = timeoutMillis;
        this.maxBytes = maxBytes;
    }

    /** 응답(상태·본문). 머리는 읽고 버린다 */
    public static final class Response {
        private final int status;
        private final byte[] body;

        Response(int status, byte[] body) {
            this.status = status;
            this.body = body;
        }

        public int status() {
            return status;
        }

        public byte[] body() {
            return body;
        }
    }

    public Response get(String url) {
        URI u;
        try {
            u = new URI(url);
        } catch (Exception e) {
            throw denied(400, "egress.denied.bad_url", "url", "주소 형식이 틀렸다");
        }
        String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw denied(400, "egress.denied.scheme", "scheme:" + (scheme.isEmpty() ? "-" : scheme), "http·https만 허용");
        }
        String host = u.getHost();
        if (host == null || host.isEmpty()) throw denied(400, "egress.denied.bad_url", "url", "호스트가 없다");
        InetAddress[] all;
        try {
            all = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw denied(400, "egress.denied.bad_url", "host:" + host, "이름을 풀 수 없다");
        }
        for (InetAddress a : all) {
            if (AddressCheck.isInternal(a)) {
                throw denied(403, "egress.denied.internal_address", "host:" + host, "내부망 주소로 나가는 요청");
            }
        }
        int port = u.getPort() > 0 ? u.getPort() : (scheme.equals("https") ? 443 : 80);
        try {
            return call(all[0], host, port, scheme.equals("https"), u.getRawPath(), u.getRawQuery());
        } catch (IOException e) {
            throw new SecureKitException(502, "연결된 서비스가 응답하지 않습니다");
        }
    }

    private Response call(InetAddress addr, String host, int port, boolean tls, String path, String query) throws IOException {
        Socket raw = new Socket();
        raw.connect(new InetSocketAddress(addr, port), timeoutMillis);     // 확인한 주소로 붙는다
        raw.setSoTimeout(timeoutMillis);
        Socket s = raw;
        if (tls) {
            SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(raw, host, port, true);
            SSLParameters p = ssl.getSSLParameters();
            p.setEndpointIdentificationAlgorithm("HTTPS");                 // 인증서 이름 확인
            if (!host.startsWith("[") && !Character.isDigit(host.charAt(host.length() - 1))) {
                p.setServerNames(List.of(new SNIHostName(host)));
            }
            ssl.setSSLParameters(p);
            ssl.startHandshake();
            s = ssl;
        }
        try (Socket sock = s) {
            String target = (path == null || path.isEmpty() ? "/" : path) + (query == null ? "" : "?" + query);
            String req = "GET " + target + " HTTP/1.0\r\nHost: " + host + "\r\nUser-Agent: secure-kit\r\nAccept: */*\r\n\r\n";
            OutputStream out = sock.getOutputStream();
            out.write(req.getBytes(StandardCharsets.US_ASCII));
            out.flush();
            return read(sock.getInputStream());
        }
    }

    private Response read(InputStream in) throws IOException {
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            all.write(buf, 0, n);
            if (all.size() > maxBytes + 16384) break;                       // 응답 크기 상한
        }
        byte[] b = all.toByteArray();
        int split = indexOf(b, new byte[]{'\r', '\n', '\r', '\n'});
        if (split < 0) throw new IOException("응답 머리가 없다");
        String head = new String(b, 0, split, StandardCharsets.ISO_8859_1);
        String[] statusLine = head.split("\r\n", 2)[0].split(" ");
        int status = statusLine.length > 1 ? Integer.parseInt(statusLine[1]) : 502;
        int from = split + 4;
        int len = Math.min(b.length - from, maxBytes);
        byte[] body = new byte[Math.max(0, len)];
        System.arraycopy(b, from, body, 0, body.length);
        return new Response(status, body);
    }

    private static int indexOf(byte[] b, byte[] pat) {
        outer:
        for (int i = 0; i + pat.length <= b.length; i++) {
            for (int j = 0; j < pat.length; j++) if (b[i + j] != pat[j]) continue outer;
            return i;
        }
        return -1;
    }

    private static SecureKitException denied(int status, String event, String target, String reason) {
        SecurityEvents.warn(event, "safe-http-client", target, reason);
        return new SecureKitException(status, status == 403 ? "허용하지 않는 주소입니다" : "주소가 올바르지 않습니다");
    }
}
