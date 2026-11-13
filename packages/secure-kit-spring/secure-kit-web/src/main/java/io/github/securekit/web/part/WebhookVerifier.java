package io.github.securekit.web.part;

import io.github.securekit.core.SecureKitException;
import io.github.securekit.core.event.SecurityEvents;
import io.github.securekit.core.store.CounterStore;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/**
 * 웹훅 확인과 중복 막기(10-4). 설정 secure-kit.webhook.secret · tolerance(기본 5분).
 *
 * 1. 서명: X-Signature = "sha256=" + hex(HMAC-SHA256(비밀값, "&lt;X-Timestamp&gt;.&lt;본문 바이트&gt;")).
 *    본문을 JSON으로 다시 만들지 말고 받은 바이트 그대로 계산한다.
 * 2. 시각: X-Timestamp가 지금과 5분 넘게 차이 나면 막는다(가로챈 웹훅 다시 보내기).
 * 3. 중복: 같은 eventId는 한 번만 처리한다(결제사는 같은 웹훅을 여러 번 보낸다).
 */
public class WebhookVerifier {
    private final byte[] secret;
    private final Duration tolerance;
    private final CounterStore store;

    public WebhookVerifier(String secret, Duration tolerance, CounterStore store) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.tolerance = tolerance;
        this.store = store;
    }

    /** 서명·시각이 틀리면 401 + payment.denied.bad_signature */
    public void verify(String timestamp, String signature, byte[] body) {
        long ts;
        try {
            ts = Long.parseLong(timestamp == null ? "" : timestamp.trim());
        } catch (NumberFormatException e) {
            throw bad("X-Timestamp가 없거나 숫자가 아니다");
        }
        long now = System.currentTimeMillis() / 1000;
        if (Math.abs(now - ts) > tolerance.getSeconds()) throw bad("허용 시간 밖의 웹훅");
        if (signature == null || !signature.startsWith("sha256=")) throw bad("서명 형식이 틀렸다");
        byte[] want = ("sha256=" + hmacHex(ts + ".", body)).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(want, signature.trim().toLowerCase().getBytes(StandardCharsets.US_ASCII))) {
            throw bad("서명이 맞지 않는다");
        }
    }

    /** 처음 받은 eventId면 true. 두 번째부터 false + payment.duplicate.event(INFO) */
    public boolean firstDelivery(String eventId) {
        if (store.putIfAbsent("webhook:event:" + eventId, "1", Duration.ofDays(3))) return true;
        SecurityEvents.info("payment.duplicate.event", "idempotency", "event:" + eventId, "같은 웹훅을 다시 받았다(처리하지 않음)");
        return false;
    }

    private String hmacHex(String prefix, byte[] body) {
        try {
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(secret, "HmacSHA256"));
            m.update(prefix.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(m.doFinal(body));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static SecureKitException bad(String reason) {
        SecurityEvents.warn("payment.denied.bad_signature", "idempotency", "webhook", reason);
        return new SecureKitException(401, "서명을 확인하지 못했습니다");
    }
}
