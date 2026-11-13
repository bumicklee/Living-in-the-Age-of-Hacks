package com.macro.mall.security.crypto;

import com.fasterxml.jackson.databind.JsonNode;
import com.macro.mall.security.openbao.OpenBaoClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * [다온마켓 5-4 정답] 암호화한 전화번호를 찾기 위한 검색용 값. HMAC-SHA256(정규화한 번호, 서버만 아는 키).
 *
 * <pre>
 * 보관 칸(phone)       : 같은 번호도 매번 다른 암호문 → 보관용, 풀 수 있다
 * 검색 칸(phone_hmac)  : 같은 번호는 늘 같은 값 → 찾기용, 되돌릴 수 없다. 형식 v&lt;키 버전&gt;:&lt;16진수 64자&gt;
 * </pre>
 *
 * - 그냥 해시(SHA-256)가 아니라 키를 쓰는 이유: 전화번호는 경우의 수가 적어서 키 없는 해시는 모든 번호를 미리 계산해 맞출 수 있다.
 * - 키는 OpenBao KV(secret/daon/pii-hmac)에서 앱이 뜬 뒤 처음 쓸 때 한 번 읽어 메모리에 둔다. 코드·설정·이미지에 두지 않는다.
 * - 저장할 때와 찾을 때 같은 정규화 함수를 쓴다(하이픈·공백을 뺀 숫자만).
 * - 키를 바꾸면 모든 검색 칸을 다시 계산해야 한다. 그래서 값 앞에 키 버전을 적어 둔다.
 */
@Component
public class PhoneHmac {
    @Autowired
    private OpenBaoClient bao;

    private volatile byte[] key;
    private volatile String version;

    /** 하이픈·공백·괄호를 뺀 숫자만. 저장과 검색이 이 함수 하나를 함께 쓴다 */
    public static String normalize(String phone) {
        return phone == null ? "" : phone.replaceAll("\\D", "");
    }

    public String of(String phone) {
        String n = normalize(phone);
        if (n.isEmpty()) {
            return null;
        }
        load();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return "v" + version + ":" + HexFormat.of().formatHex(mac.doFinal(n.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("검색용 값을 만들지 못했습니다", e);
        }
    }

    private void load() {
        if (key == null) {
            synchronized (this) {
                if (key == null) {
                    JsonNode kv = bao.read("secret/data/daon/pii-hmac").path("data");
                    key = HexFormat.of().parseHex(kv.path("key").asText());
                    version = kv.path("version").asText("1");
                }
            }
        }
    }
}
