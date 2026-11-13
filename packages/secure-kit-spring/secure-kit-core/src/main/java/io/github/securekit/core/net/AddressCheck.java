package io.github.securekit.core.net;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * 서버가 대신 부를 주소가 내부망인지 본다(SSRF). 이름을 풀어 나온 주소 **전부**에 적용한다.
 *
 * 막는 것: 루프백(127/8, ::1), 사설망(10/8, 172.16/12, 192.168/16), 링크 로컬(169.254/16 = 클라우드 메타데이터, fe80::/10),
 * 0.0.0.0/8, 통신사 공유망(100.64/10), 멀티캐스트, IPv6 고유 로컬(fc00::/7).
 */
public final class AddressCheck {
    private AddressCheck() {
    }

    public static boolean isInternal(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress()
                || a.isSiteLocalAddress() || a.isMulticastAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (a instanceof Inet4Address) {
            int first = b[0] & 0xff;
            int second = b[1] & 0xff;
            return first == 0 || (first == 100 && second >= 64 && second <= 127);
        }
        if (a instanceof Inet6Address) {
            int first = b[0] & 0xff;
            if ((first & 0xfe) == 0xfc) return true;                 // fc00::/7
            boolean mapped = true;                                      // ::ffff:a.b.c.d
            for (int i = 0; i < 10; i++) if (b[i] != 0) mapped = false;
            if (mapped && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff) {
                try {
                    return isInternal(InetAddress.getByAddress(new byte[]{b[12], b[13], b[14], b[15]}));
                } catch (java.net.UnknownHostException e) {
                    return true;
                }
            }
        }
        return false;
    }
}
