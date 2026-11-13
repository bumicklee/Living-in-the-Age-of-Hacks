package com.macro.mall.common.util;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * [다온마켓 6-4 정답 · E09] 요청한 쪽의 IP. 속도 제한·보안 이벤트가 쓴다.
 *
 * <pre>
 * String ip = ClientIp.of(request);
 * </pre>
 *
 * - 기본은 소켓 주소(getRemoteAddr)다. X-Forwarded-For는 누구나 보낼 수 있는 헤더라서 그냥 믿지 않는다.
 * - 소켓 주소가 <b>믿는 프록시</b>(게이트웨이·고객 웹)일 때만 그 프록시가 넣은 X-Forwarded-For의 <b>마지막 값</b>을 쓴다.
 *   마지막 값은 바로 앞 프록시가 직접 본 주소다. 앞쪽 값은 요청한 쪽이 꾸며서 보낼 수 있다.
 * - 믿는 프록시 목록은 환경 변수 DAON_TRUSTED_PROXIES(이름 또는 주소를 쉼표로). 이름은 30초마다 다시 풀어 본다(컨테이너 주소가 바뀌어도 된다).
 *   목록이 비어 있으면 아무도 믿지 않는다(실패하면 막는다).
 * - 마지막 값이 IP 모양이 아니면 소켓 주소를 쓴다.
 * - switchOn()는 영상용 랩 스위치다. 끄면 6-4 이전처럼 늘 소켓 주소(=게이트웨이 뒤에서는 모두 같은 주소)를 쓴다.
 */
public final class ClientIp {
    private static final Pattern IP_LITERAL = Pattern.compile("[0-9a-fA-F:.]{2,45}");
    private static final long REFRESH_MS = 30_000;
    private static volatile Set<String> trusted = Set.of();
    private static volatile long loadedAt = 0;

    private ClientIp() {
    }

    /** 랩 스위치(make switch K=ip-trust V=off → SECUREKIT_IP_TRUST_ENABLED=false). SecureKitSwitch와 같은 규칙: 값이 없으면 켜짐. mall-common에는 그 클래스가 없어 환경 변수를 직접 본다 */
    private static boolean switchOn() {
        String v = System.getenv("SECUREKIT_IP_TRUST_ENABLED");
        return v == null || !v.trim().equalsIgnoreCase("false");
    }

    public static String of(HttpServletRequest req) {
        String remote = req.getRemoteAddr();
        if (!switchOn() || !isTrustedProxy(remote)) {
            return remote;
        }
        return lastForwarded(req.getHeaders("X-Forwarded-For"), remote);
    }

    /** X-Forwarded-For 헤더 줄들을 쉼표로 펼친 뒤 마지막 값. IP 모양이 아니거나 없으면 fallback */
    static String lastForwarded(Enumeration<String> headers, String fallback) {
        String last = null;
        while (headers != null && headers.hasMoreElements()) {
            for (String part : headers.nextElement().split(",")) {
                if (!part.isBlank()) {
                    last = part.strip();
                }
            }
        }
        return last != null && IP_LITERAL.matcher(last).matches() ? last : fallback;
    }

    static boolean isTrustedProxy(String remote) {
        return remote != null && proxies().contains(remote);
    }

    private static Set<String> proxies() {
        long now = System.currentTimeMillis();
        if (now - loadedAt > REFRESH_MS) {
            synchronized (ClientIp.class) {
                if (now - loadedAt > REFRESH_MS) {
                    trusted = resolve(System.getenv("DAON_TRUSTED_PROXIES"));
                    loadedAt = now;
                }
            }
        }
        return trusted;
    }

    static Set<String> resolve(String list) {
        Set<String> out = new HashSet<>();
        if (list == null) {
            return out;
        }
        for (String name : list.split(",")) {
            String n = name.strip();
            if (n.isEmpty()) {
                continue;
            }
            try {
                for (InetAddress a : InetAddress.getAllByName(n)) {
                    out.add(a.getHostAddress());
                }
            } catch (Exception unresolved) {
                // 아직 뜨지 않은 프록시는 건너뛴다. 다음 갱신(30초)에 다시 풀어 본다
            }
        }
        return out;
    }
}
