package com.macro.mall.security.securekit;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 7-미션: 방어 스위치 파일 서명. 가짜 키·가짜 값만 쓴다. */
class SwitchSignatureTest {
    private static final String KEY = "unit-test-fake-key";

    private static String hmac(String text) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        StringBuilder hex = new StringBuilder();
        for (byte b : mac.doFinal(text.getBytes(StandardCharsets.UTF_8))) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private static Map<String, String> env(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void noOffSwitchMeansNothingToVerify() {
        assertTrue(SwitchSignature.valid(env("SECUREKIT_OWNERSHIP_ENABLED", "true")));
        assertTrue(SwitchSignature.valid(env()));
    }

    @Test
    void validSignatureIsAccepted() throws Exception {
        String canonical = "SECUREKIT_OWNERSHIP_ENABLED=false\nSECUREKIT_UPLOAD_ENABLED=false";
        Map<String, String> e = env("SECUREKIT_UPLOAD_ENABLED", "false", "SECUREKIT_OWNERSHIP_ENABLED", "false", "DAON_SWITCH_SIGN_KEY", KEY, "SECUREKIT_SIG", hmac(canonical));
        assertEquals(canonical, SwitchSignature.canonical(e));
        assertTrue(SwitchSignature.valid(e));
    }

    @Test
    void handEditedFileIsRejected() throws Exception {
        String signedFor = "SECUREKIT_OWNERSHIP_ENABLED=false";
        // 서명은 ownership 하나에 대한 것인데 파일에 upload 꺼짐이 더 적혔다
        Map<String, String> e = env("SECUREKIT_OWNERSHIP_ENABLED", "false", "SECUREKIT_UPLOAD_ENABLED", "false", "DAON_SWITCH_SIGN_KEY", KEY, "SECUREKIT_SIG", hmac(signedFor));
        assertFalse(SwitchSignature.valid(e));
    }

    @Test
    void missingSignatureOrKeyIsRejected() {
        assertFalse(SwitchSignature.valid(env("SECUREKIT_OWNERSHIP_ENABLED", "false")));
        assertFalse(SwitchSignature.valid(env("SECUREKIT_OWNERSHIP_ENABLED", "false", "SECUREKIT_SIG", "00")));
        assertFalse(SwitchSignature.valid(env("SECUREKIT_OWNERSHIP_ENABLED", "false", "DAON_SWITCH_SIGN_KEY", KEY, "SECUREKIT_SIG", "00")));
    }
}
