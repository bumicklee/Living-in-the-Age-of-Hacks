package com.macro.mall.portal.component;

import com.macro.mall.security.openbao.OpenBaoClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * [다온마켓 10-4 정답] 결제사 웹훅 서명 확인: X-Daon-Signature = hex(HMAC-SHA256(비밀값, 본문 바이트)).
 *
 * - 받은 본문 바이트 그대로 계산한다. JSON을 객체로 바꿨다가 다시 직렬화하면 공백·순서·숫자 모양이 바뀌어 서명이 어긋난다.
 * - 비교는 상수 시간(MessageDigest.isEqual). 어디서 달라지는지가 응답 시간으로 새지 않게.
 * - 비밀값은 OpenBao KV(secret/daon/payment-webhook)에서 처음 쓸 때 한 번 읽는다(A4, 6-3과 같은 방법). 코드·설정·이미지에 두지 않는다.
 * - 비밀값을 못 읽으면 예외(IllegalStateException) → 컨트롤러가 503. 서명을 확인할 수 없으면 받지 않는다(fail-closed, 9-2).
 * - 10-미션: 이 부품을 secure-kit의 WebhookVerifier로 뽑는다.
 */
@Component
public class DaonWebhookVerifier {
    @Autowired
    private OpenBaoClient bao;
    private volatile byte[] key;

    public boolean valid(byte[] body, String signatureHex) {
        if (body == null || signatureHex == null || !signatureHex.matches("[0-9a-fA-F]{64}")) {
            return false;
        }
        byte[] expected = HexFormat.of().formatHex(hmac(body)).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, signatureHex.toLowerCase().getBytes(StandardCharsets.US_ASCII));
    }

    byte[] hmac(byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key(), "HmacSHA256"));
            return mac.doFinal(body);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("웹훅 서명을 계산하지 못했습니다", e);
        }
    }

    private byte[] key() {
        if (key == null) {
            synchronized (this) {
                if (key == null) {
                    String k = bao.read("secret/data/daon/payment-webhook").path("data").path("key").asText("");
                    if (k.isEmpty()) {
                        throw new IllegalStateException("웹훅 서명 비밀을 시크릿 저장소에서 읽지 못했습니다");
                    }
                    key = k.getBytes(StandardCharsets.UTF_8);
                }
            }
        }
        return key;
    }
}
