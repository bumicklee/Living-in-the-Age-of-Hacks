package com.macro.mall.security.securekit;

import jakarta.servlet.http.HttpServletRequest;

import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;

/**
 * [다온마켓 12-3 정답 · secure-kit 부품] 파트너 입구가 확인한 클라이언트 인증서.
 *
 * <pre>
 * 게이트웨이(8087, ssl_verify_client optional): 랩 내부 CA가 서명한 인증서면 PEM을 URL 인코딩해
 *   X-Daon-Client-Cert 헤더로 넘긴다. 확인 실패·인증서 없음이면 빈 값(= 헤더를 지움). 밖에서 온 같은 이름 헤더는 늘 지운다.
 * 앱: 그 헤더를 「파트너 입구에서 온 요청」(PartnerEntrance)에서만 읽는다. 지문 = base64url(SHA-256(인증서 DER)) = RFC 8705 x5t#S256
 * </pre>
 *
 * - 인증서를 앱이 다시 확인(체인·만료)하지 않는다. 확인은 게이트웨이가 했고, 앱은 「그 게이트웨이가 넘겼나」만 본다.
 *   그래서 헤더 지우기(게이트웨이)와 소켓 주소 확인(앱) 둘 중 하나라도 빠지면 아무나 헤더를 꾸며 넣을 수 있다.
 * - 이 클래스는 지문과 CN만 돌려준다. 인증서 원문은 로그에 남기지 않는다.
 */
public final class PartnerClientCert {
    public static final String HEADER = "X-Daon-Client-Cert";

    /** 지문(x5t#S256)과 주인 이름(CN) */
    public record Info(String thumbprint, String commonName) {
    }

    private PartnerClientCert() {
    }

    /** 파트너 입구가 넘긴 인증서. 없거나 읽지 못하면 null */
    public static Info of(HttpServletRequest request) {
        if (!PartnerEntrance.fromGateway(request)) {
            return null;
        }
        String header = request.getHeader(HEADER);
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            String pem = URLDecoder.decode(header, StandardCharsets.UTF_8);
            X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());
            return new Info(Base64.getUrlEncoder().withoutPadding().encodeToString(digest), commonName(cert));
        } catch (Exception e) {
            return null;
        }
    }

    private static String commonName(X509Certificate cert) {
        try {
            for (Rdn rdn : new LdapName(cert.getSubjectX500Principal().getName()).getRdns()) {
                if ("CN".equalsIgnoreCase(rdn.getType())) {
                    return String.valueOf(rdn.getValue());
                }
            }
        } catch (Exception ignored) {
            // 이름을 못 읽으면 null(발급을 거절한다)
        }
        return null;
    }
}
