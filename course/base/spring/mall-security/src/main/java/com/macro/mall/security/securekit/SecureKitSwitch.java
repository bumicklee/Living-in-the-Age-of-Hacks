package com.macro.mall.security.securekit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * [다온마켓 추가] 방어 스위치 읽기: secure-kit.&lt;이름&gt;.enabled (기본 켜짐). 랩-설계.md 12절.
 *
 * <pre>
 * if (SecureKitSwitch.on("ownership")) { ...소유권 검사... }
 * </pre>
 *
 * - 수업 정답(완료 지점)의 보호 코드만 이 스위치를 읽는다. 시작 지점 코드는 읽지 않는다(손으로 고치는 실습은 그대로).
 * - 영상에서 「끄면 레드, 켜면 그린」을 코드 수정 없이 보여 주려는 랩 도구다. make switch K=ownership V=off
 *   → .daon/switches.env의 SECUREKIT_OWNERSHIP_ENABLED=false → 앱 재시작.
 * - 값을 못 읽으면 켜진 것으로 본다(실패하면 막는다). 회사 서비스에 옮길 때는 보호를 끄는 스위치를 두지 않는 편이 낫다.
 */
@Component
public class SecureKitSwitch implements EnvironmentAware {
    private static final Logger LOG = LoggerFactory.getLogger("DAON-LAB");
    /** 섹션 2·3·4에서 쓰는 이름(daon/lab.py의 SWITCHES와 같다). headers는 관리자 화면 nginx가 읽는다(infra/lab-banner) */
    public static final List<String> KNOWN = List.of("ownership", "admin-default-deny", "request-dto", "egress", "host-allowlist",
            "query-binding", "sort-allowlist", "html-sanitize", "upload",
            "password-policy", "login-ratelimit", "login-timing", "reset-flow", "token-revoke", "step-up", "pii-encrypt", "pii-mask", "jwt-hardening",
            "secret-canary", "startup-check", "error-mask", "docs-closed", "cors-allowlist", "sec-headers", "cache-hmac", "ip-trust",
            "cache-type-allowlist", "signed-switches",
            "audit-log", "status-codes", "log-mask", "audit-chain",
            "fail-closed", "error-path", "input-limits", "resilience",
            "money-race", "pay-webhook", "reconcile", "checkout-limits", "sms-budget", "server-price",
            "safe-redirect", "private-cache",
            "partner-scope", "token-binding", "partner-revoke",
            "approval", "dual-approval", "ops-elevation",
            "degraded-mode");

    private static volatile Environment env;

    @Override
    public void setEnvironment(Environment environment) {
        env = environment;
    }

    public static boolean on(String name) {
        String key = "secure-kit." + name + ".enabled";
        String v = null;
        Environment e = env;
        if (e != null) {
            v = e.getProperty(key);
        }
        if (v == null) {
            // 빈이 만들어지기 전(@PostConstruct 등)에도 같은 값을 읽도록 환경 변수를 직접 본다
            v = System.getenv("SECUREKIT_" + name.toUpperCase(Locale.ROOT).replace('-', '_') + "_ENABLED");
        }
        // ✏️ 7-미션: 서명이 맞지 않는 「꺼짐」은 믿지 않는다(SwitchSignature)
        return v == null || !v.trim().equalsIgnoreCase("false");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void report() {
        List<String> off = KNOWN.stream().filter(n -> !on(n)).toList();
        if (!off.isEmpty()) {
            LOG.warn("[랩 스위치] 꺼진 보호: {} (make switch K=<이름> V=on 으로 다시 켭니다)", String.join(", ", off));
        }
    }
}
