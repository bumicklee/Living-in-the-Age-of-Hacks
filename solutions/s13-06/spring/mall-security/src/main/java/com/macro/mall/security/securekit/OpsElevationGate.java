package com.macro.mall.security.securekit;

import org.springframework.util.AntPathMatcher;

import java.util.List;

/**
 * [다온마켓 13-6 정답] 고위험 기능과 시간 제한 권한 상승(JIT)의 문.
 *
 * 고위험 묶음(group) → 주소. 이 주소들은 역할(권한표)이 있어도 「지금 살아 있는 권한 상승」이 없으면 403(ops.denied.no_elevation).
 * 상시 슈퍼 권한을 가진 사람이 있어도, 고위험 기능은 사유 + 다른 사람의 승인 + 시간 제한을 거쳐야 쓴다.
 *
 * 고위험 표시를 권한표(ums_resource)에 칸으로 더하지 않고 목록을 코드에 둔다(원본 권한 화면을 바꾸지 않으려고).
 * 구현(isActive)은 관리자 앱이 준다(DaonOpsElevationService: 승인 서비스가 서명한 권한 상승 토큰을 확인한 뒤 Redis에 만료 시각까지).
 */
public interface OpsElevationGate {
    /** 묶음 이름 → 이 묶음에 드는 주소(POST만 고위험으로 본다. 목록 보기는 평소 권한으로) */
    List<String[]> HIGH_RISK = List.of(
            new String[]{"admin-accounts", "/admin/role/update"},
            new String[]{"admin-accounts", "/admin/update/**"},
            new String[]{"admin-accounts", "/admin/updateStatus/**"},
            new String[]{"admin-accounts", "/admin/delete/**"},
            new String[]{"admin-accounts", "/admin/register"},
            new String[]{"admin-accounts", "/role/**"},
            new String[]{"admin-accounts", "/resource/**"},
            new String[]{"admin-accounts", "/menu/**"},
            new String[]{"admin-accounts", "/daon/partner/apps/*/scopes"});
    /** 누구나(로그인한 관리자) 스스로 부를 수 있는 권한 상승 기능. 권한표에 올리지 않는다 */
    String SELF_SERVICE = "/daon/elevation/**";

    /** 이 요청이 고위험이면 묶음 이름, 아니면 null */
    static String groupOf(String method, String path) {
        if (!"POST".equalsIgnoreCase(method)) {
            return null;
        }
        AntPathMatcher m = new AntPathMatcher();
        for (String[] g : HIGH_RISK) {
            if (m.match(g[1], path)) {
                return g[0];
            }
        }
        return null;
    }

    boolean isActive(String username, String group);
}
