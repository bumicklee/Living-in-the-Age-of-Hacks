package com.macro.mall.security.secret;

import java.util.Set;

/**
 * [다온마켓 6-2] 미끼 자격증명(canary). 저장소·문서에 일부러 남겨 둔 「쓰면 안 되는 가짜 토큰」 이름 목록.
 *
 * - 값은 진짜 토큰이 아니라 사람이 알아보는 표시 문자열이다. 어떤 계정에도 연결되어 있지 않다.
 * - 이 값이 요청에 실려 오면 저장소나 문서가 밖으로 샜다는 뜻이다. 필터가 거부하고 secret.canary_used를 남긴다.
 * - 새 미끼를 문서에 넣을 때는 여기에도 함께 적는다(허용 목록이 아니라 「감시 목록」이다).
 */
public final class CanaryTokens {
    private static final Set<String> NAMES = Set.of("daon-canary-postman-do-not-use");

    private CanaryTokens() {
    }

    public static boolean isCanary(String bearerValue) {
        return bearerValue != null && NAMES.contains(bearerValue.trim());
    }

    /** 이벤트의 target에 넣는 이름: canary:<이름>. 이름이 목록에 있을 때만 값 그대로(미끼라서 비밀이 아니다) */
    public static String label(String bearerValue) {
        return "canary:" + (isCanary(bearerValue) ? bearerValue.trim() : "?");
    }
}
