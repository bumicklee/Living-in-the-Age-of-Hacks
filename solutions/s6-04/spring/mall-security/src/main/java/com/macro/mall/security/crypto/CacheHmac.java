package com.macro.mall.security.crypto;

import com.fasterxml.jackson.databind.JsonNode;
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
 * [다온마켓 6-4] Redis에 올려 둔 권한 목록이 저장소에서 바뀌지 않았는지 확인하는 서명(HMAC-SHA256).
 *
 * - 캐시 값을 쓸 때 서명을 옆 키(…:sig)에 함께 쓰고, 읽을 때 다시 계산해 맞지 않거나 서명이 없으면 값을 버리고 DB에서 다시 읽는다.
 * - 저장소에 인증을 켰어도 앱 자격증명이 새거나 저장소 권한이 넓으면 캐시를 쓸 수 있다. 권한처럼 중요한 값은 저장소를 믿지 않고 확인한다.
 * - 서명 키는 OpenBao KV(secret/daon/cache-hmac)에서 처음 쓸 때 한 번 읽어 메모리에 둔다. 코드·설정·이미지에 두지 않는다.
 */
@Component
public class CacheHmac {
    @Autowired
    private OpenBaoClient bao;
    private volatile byte[] key;

    public String sign(String canonical) {
        load();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("캐시 서명을 만들지 못했습니다", e);
        }
    }

    /** 시간 일정 비교(서명 문자열이 어디서 달라지는지 응답 시간으로 새지 않게) */
    public boolean verify(String canonical, String signature) {
        return signature != null && MessageDigest.isEqual(sign(canonical).getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
    }

    private void load() {
        if (key == null) {
            synchronized (this) {
                if (key == null) {
                    JsonNode kv = bao.read("secret/data/daon/cache-hmac").path("data");
                    String k = kv.path("key").asText("");
                    if (k.isEmpty()) {
                        throw new IllegalStateException("캐시 서명 키를 시크릿 저장소에서 읽지 못했습니다");
                    }
                    key = k.getBytes(StandardCharsets.UTF_8);
                }
            }
        }
    }
}
