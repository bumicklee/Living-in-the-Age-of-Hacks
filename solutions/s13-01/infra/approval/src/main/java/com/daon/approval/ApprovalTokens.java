package com.daon.approval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * [A10] 서명된 승인 토큰. 서명은 금고 transit 키 daon-approval(ECDSA P-256)이 한다. 키는 금고 밖으로 나오지 않고,
 * 서명 권한(transit/sign/daon-approval)은 승인 서비스 정책에만 있다. 관리자 앱은 확인(transit/verify)만 한다.
 *
 * <pre>
 * 헤더  {"alg":"ES256","typ":"daon-approval","kid":"daon-approval:v1"}
 * 내용  {"jti":"<1회용 번호>","kind":"refund","approval":"ap…","return_id":31,"order_id":9001,"order_sn":"DAON…",
 *        "amount":"10000","requested_by":"staff_order","approved_by":"policy","iat":…,"exp":…}
 * 토큰  base64url(헤더).base64url(내용).base64url(서명)   ← JWT와 같은 모양이지만 로그인 토큰이 아니다(typ로 구분)
 * </pre>
 *
 * 금액은 글자("10000")로 넣는다(소수·부동소수 차이로 실행기가 잘못 비교하지 않게, 원 단위 정수).
 */
@Component
public class ApprovalTokens {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Bao bao;

    public ApprovalTokens(Bao bao) {
        this.bao = bao;
    }

    /** 환불 승인 토큰 */
    public String refund(Map<String, Object> row, long minutes) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("jti", row.get("nonce"));
        p.put("kind", "refund");
        p.put("approval", row.get("id"));
        p.put("return_id", ((Number) row.get("return_id")).longValue());
        p.put("order_id", ((Number) row.get("order_id")).longValue());
        p.put("order_sn", row.get("order_sn"));
        p.put("amount", ((BigDecimal) row.get("amount")).setScale(0, java.math.RoundingMode.DOWN).toPlainString());
        p.put("requested_by", row.get("requested_by"));
        p.put("approved_by", row.get("approved_by"));
        return sign(p, minutes);
    }

    /** 권한 상승 토큰(13-6): 누가·어떤 묶음을·언제까지 */
    public String elevation(Map<String, Object> row) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("jti", row.get("nonce"));
        p.put("kind", "ops-elevation");
        p.put("approval", row.get("id"));
        p.put("sub", row.get("requested_by"));
        p.put("group", row.get("target"));
        p.put("approved_by", row.get("approved_by"));
        return sign(p, ((Number) row.get("minutes")).longValue());
    }

    private String sign(Map<String, Object> payload, long minutes) {
        long now = System.currentTimeMillis() / 1000L;
        payload.put("iat", now);
        payload.put("exp", now + minutes * 60L);
        try {
            String body = b64(JSON.writeValueAsBytes(payload));
            // 헤더의 kid는 서명한 키 버전. 먼저 최신 버전을 읽어 헤더를 만들고, 서명 응답의 버전과 같은지 본다
            int latest = bao.read("transit/keys/daon-approval").path("latest_version").asInt(1);
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "ES256");
            header.put("typ", "daon-approval");
            header.put("kid", "daon-approval:v" + latest);
            String input = b64(JSON.writeValueAsBytes(header)) + "." + body;
            JsonNode res = bao.write("transit/sign/daon-approval", Map.of(
                    "input", Base64.getEncoder().encodeToString(input.getBytes(StandardCharsets.US_ASCII)),
                    "hash_algorithm", "sha2-256",
                    "marshaling_algorithm", "jws"));
            String[] sig = res.path("signature").asText("").split(":", 3);      // vault:v1:<base64url(r||s)>
            if (sig.length != 3 || !("v" + latest).equals(sig[1])) {
                throw new IllegalStateException("서명 응답이 예상과 다릅니다");
            }
            return input + "." + sig[2];
        } catch (Exception e) {
            throw new IllegalStateException("승인 토큰을 서명하지 못했습니다: " + e.getMessage());
        }
    }

    private static String b64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
