package com.macro.mall.security.securekit;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;

/**
 * [다온마켓 12-2 정답 · A9] 파트너 경로는 파트너 입구(게이트웨이 8087)로 들어온 요청만 받는다.
 *
 * - 「게이트웨이에서 왔나」는 소켓 주소(getRemoteAddr)로 본다. 헤더(X-Forwarded-For·X-Daon-Entrance 같은 것)는 누구나 보낼 수 있어 믿지 않는다.
 * - 게이트웨이 이름은 환경 변수 DAON_PARTNER_GATEWAY(기본 gateway, compose.a09.yml). 30초마다 다시 풀어 본다.
 *   이름을 못 풀면 아무도 믿지 않는다(실패하면 막는다).
 * - 고객 웹(8088)·고객 API 입구(8086 → Varnish)·앱 직접 주소(8085)로 온 파트너 요청은 404(그런 주소는 없다고 답한다).
 * - 12-3: 게이트웨이가 넘긴 클라이언트 인증서 헤더도 이 검사를 지난 요청에서만 읽는다(PartnerClientCert).
 * - 방어 스위치가 없다(구조). 회사에서는 앱 포트를 게이트웨이 뒤 망에만 열어 같은 효과를 낸다.
 */
public final class PartnerEntrance {
    private static final long REFRESH_MS = 30_000;
    private static volatile Set<String> gateways = Set.of();
    private static volatile long loadedAt = 0;

    private PartnerEntrance() {
    }

    public static boolean fromGateway(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        return remote != null && addresses().contains(remote);
    }

    private static Set<String> addresses() {
        long now = System.currentTimeMillis();
        if (now - loadedAt > REFRESH_MS) {
            synchronized (PartnerEntrance.class) {
                if (now - loadedAt > REFRESH_MS) {
                    gateways = resolve(System.getenv().getOrDefault("DAON_PARTNER_GATEWAY", "gateway"));
                    loadedAt = now;
                }
            }
        }
        return gateways;
    }

    static Set<String> resolve(String names) {
        Set<String> out = new HashSet<>();
        for (String n : names.split(",")) {
            String name = n.strip();
            if (name.isEmpty()) {
                continue;
            }
            try {
                for (InetAddress a : InetAddress.getAllByName(name)) {
                    out.add(a.getHostAddress());
                }
            } catch (Exception unresolved) {
                // 게이트웨이가 아직 없다(A9 전). 다음 갱신에 다시 풀어 본다
            }
        }
        return out;
    }
}
