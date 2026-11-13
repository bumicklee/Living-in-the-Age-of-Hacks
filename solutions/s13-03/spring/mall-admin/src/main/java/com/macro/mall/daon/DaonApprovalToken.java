package com.macro.mall.daon;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.security.openbao.OpenBaoClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * [다온마켓 13-3 정답] 승인 서비스가 서명한 토큰 확인. 관리자 앱은 금고에 「확인」만 부탁할 수 있다(정책 daon-approval-verify).
 * 서명(transit/sign/daon-approval)은 승인 서비스 정책에만 있어서, 관리자 앱이 스스로 승인 토큰을 만들 수 없다.
 *
 * 돌려주는 것: 서명이 맞고 typ·kind가 기대한 것이면 내용(map), 아니면 null. 만료·대상·1회용 번호는 부른 쪽이 본다.
 */
@Component
public class DaonApprovalToken {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String KID = "daon-approval:v";

    @Autowired
    private OpenBaoClient bao;

    public Map<String, Object> verify(String token, String kind) {
        try {
            if (token == null) {
                return null;
            }
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3 || parts[2].isEmpty()) {
                return null;
            }
            Map<String, Object> header = JSON.readValue(Base64.getUrlDecoder().decode(parts[0]), new TypeReference<>() { });
            String kid = String.valueOf(header.get("kid"));
            if (!"ES256".equals(header.get("alg")) || !"daon-approval".equals(header.get("typ")) || !kid.startsWith(KID)) {
                return null;
            }
            int version = Integer.parseInt(kid.substring(KID.length()));
            String input = parts[0] + "." + parts[1];
            boolean valid = bao.write("transit/verify/daon-approval", Map.of(
                    "input", Base64.getEncoder().encodeToString(input.getBytes(StandardCharsets.US_ASCII)),
                    "signature", "vault:v" + version + ":" + parts[2],
                    "hash_algorithm", "sha2-256",
                    "marshaling_algorithm", "jws")).path("valid").asBoolean(false);
            if (!valid) {
                return null;
            }
            Map<String, Object> p = JSON.readValue(Base64.getUrlDecoder().decode(parts[1]), new TypeReference<>() { });
            return kind.equals(p.get("kind")) ? p : null;
        } catch (Exception e) {
            return null;                                   // 형식 오류·금고 거부(정책)·금고 장애 모두 「확인 안 됨」
        }
    }
}
