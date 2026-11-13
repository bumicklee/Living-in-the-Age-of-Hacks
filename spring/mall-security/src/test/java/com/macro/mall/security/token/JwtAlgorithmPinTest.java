package com.macro.mall.security.token;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.security.openbao.OpenBaoClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * [다온마켓 5-2] 서버가 정한 검증 방식 하나만 쓰는지 확인한다. HTTP 요청도 금고도 쓰지 않는다(공개키를 직접 넣어 준다).
 * 거절해야 할 토큰: 알고리즘이 다른 것, 서명 칸이 빈 것, 모르는 kid, 대상(aud)이 다른 것. 정상 토큰 하나는 통과해야 한다.
 */
class JwtAlgorithmPinTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static KeyPair pair;
    private static JwtSigner signer;

    @BeforeAll
    static void setUp() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        pair = g.generateKeyPair();
        String pem = "-----BEGIN PUBLIC KEY-----\n" + Base64.getMimeEncoder().encodeToString(pair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
        JsonNode keys = JSON.readTree("{\"latest_version\":1,\"min_decryption_version\":1,\"keys\":{\"1\":{\"public_key\":"
                + JSON.writeValueAsString(pem) + "}}}");
        OpenBaoClient stub = new OpenBaoClient() {
            @Override
            public JsonNode read(String path) {
                return keys;
            }
        };
        signer = new JwtSigner();
        ReflectionTestUtils.setField(signer, "bao", stub);
        ReflectionTestUtils.setField(signer, "audience", "daon-portal");
    }

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String signed(String header, String payload) throws Exception {
        String input = b64(header) + "." + b64(payload);
        Signature s = Signature.getInstance("SHA256withECDSAinP1363Format");
        s.initSign(pair.getPrivate());
        s.update(input.getBytes(StandardCharsets.US_ASCII));
        return input + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(s.sign());
    }

    private static final String OK_HEADER = "{\"alg\":\"ES256\",\"typ\":\"JWT\",\"kid\":\"daon-jwt:v1\"}";
    private static final String OK_PAYLOAD = "{\"sub\":\"alice\",\"iss\":\"daon-market\",\"aud\":\"daon-portal\",\"exp\":4102444800}";

    @Test
    void 정상_토큰은_통과한다() throws Exception {
        Map<String, Object> p = signer.verify(signed(OK_HEADER, OK_PAYLOAD));
        assertNotNull(p, "기대: 서버가 정한 방식으로 서명된 토큰은 통과");
    }

    @Test
    void 알고리즘이_다르면_거절한다() {
        assertNull(signer.verify(b64("{\"alg\":\"none\",\"typ\":\"JWT\"}") + "." + b64(OK_PAYLOAD) + "."), "기대: alg none 거절");
        assertNull(signer.verify(b64("{\"alg\":\"HS512\",\"typ\":\"JWT\",\"kid\":\"daon-jwt:v1\"}") + "." + b64(OK_PAYLOAD) + ".AAAA"),
                "기대: HS512 같은 다른 알고리즘 거절");
    }

    @Test
    void 서명_칸이_비어_있으면_거절한다() {
        assertNull(signer.verify(b64(OK_HEADER) + "." + b64(OK_PAYLOAD) + "."), "기대: 서명 칸이 빈 토큰 거절");
    }

    @Test
    void 모르는_kid는_거절한다() throws Exception {
        assertNull(signer.verify(signed("{\"alg\":\"ES256\",\"typ\":\"JWT\",\"kid\":\"daon-jwt:v9\"}", OK_PAYLOAD)), "기대: 목록에 없는 kid 거절");
    }

    @Test
    void 대상이_다르면_거절한다() throws Exception {
        assertNull(signer.verify(signed(OK_HEADER, OK_PAYLOAD.replace("daon-portal", "daon-admin"))), "기대: 다른 앱용 토큰(aud) 거절");
    }

    @Test
    void 서명이_바뀌면_거절한다() throws Exception {
        String t = signed(OK_HEADER, OK_PAYLOAD);
        String[] p = t.split("\\.");
        assertNull(signer.verify(p[0] + "." + b64(OK_PAYLOAD.replace("alice", "admin")) + "." + p[2]), "기대: 내용을 바꾸면 서명이 안 맞아 거절");
    }
}
