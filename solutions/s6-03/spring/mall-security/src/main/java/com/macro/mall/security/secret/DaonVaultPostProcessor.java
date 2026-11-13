package com.macro.mall.security.secret;

import com.fasterxml.jackson.databind.JsonNode;
import com.macro.mall.security.openbao.OpenBaoClient;
import com.macro.mall.security.openbao.OpenBaoException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * [다온마켓 6-3] 앱이 시작할 때 시크릿 저장소(OpenBao)에서 설정 값을 읽어 설정에 넣는다.
 *
 * <pre>
 * secret/daon/&lt;앱&gt;            KV: druid_password → 모니터링 화면 비밀번호
 * database/creds/&lt;앱&gt;         동적 DB 자격증명: 쓸 때 받고 1시간 뒤 사라지는 계정(권한은 역할에 정의된 만큼만)
 * </pre>
 *
 * - 앱마다 자기 경로만 읽을 수 있다(OpenBao 정책). 저장소가 늦게 뜨면 몇 번 다시 시도하고, 끝내 못 읽으면 시작하지 않는다(실패하면 막는다).
 * - 읽은 값은 설정 맨 앞(daonVault)에 넣어 설정 파일의 자리 표시보다 먼저 쓰인다. 값은 어디에도 찍지 않는다(건수·계정 이름 앞부분만).
 * - OpenBao 접속 정보(역할 파일)가 없는 환경(A4를 켜지 않음)에서는 아무것도 하지 않는다.
 */
public class DaonVaultPostProcessor implements EnvironmentPostProcessor, Ordered {
    private final Log log;

    public DaonVaultPostProcessor(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(DaonVaultPostProcessor.class);
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        OpenBaoClient bao = new OpenBaoClient();
        if (!bao.configured()) {
            return;
        }
        String role = System.getenv().getOrDefault("DAON_OPENBAO_ROLE", "portal");
        Map<String, Object> props = new HashMap<>();
        OpenBaoException last = null;
        for (int attempt = 1; attempt <= 10; attempt++) {        // 저장소가 앱보다 늦게 뜰 수 있다
            try {
                JsonNode kv = bao.read("secret/data/daon/" + role).path("data");
                if (kv.hasNonNull("druid_password")) {
                    props.put("spring.datasource.druid.stat-view-servlet.login-password", kv.get("druid_password").asText());
                }
                JsonNode creds = bao.readRaw("database/creds/" + role);
                String user = creds.path("data").path("username").asText("");
                String pass = creds.path("data").path("password").asText("");
                if (user.isEmpty() || pass.isEmpty()) {
                    throw new OpenBaoException("동적 DB 자격증명 응답이 비어 있습니다", 200);
                }
                props.put("spring.datasource.username", user);
                props.put("spring.datasource.password", pass);
                LeaseKeeper.track(bao, creds.path("lease_id").asText(), (int) creds.path("lease_duration").asLong(3600), role);
                log.info("[6-3] 시크릿 저장소에서 설정 " + props.size() + "개를 읽었습니다(DB 계정 " + user.substring(0, Math.min(8, user.length())) + "…, 임대 "
                        + creds.path("lease_duration").asLong() + "초)");
                last = null;
                break;
            } catch (OpenBaoException e) {
                last = e;
                log.warn("[6-3] 시크릿 저장소 읽기 실패(" + attempt + "/10): " + e.getMessage());
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (last != null) {
            throw new IllegalStateException("시크릿 저장소에서 설정을 읽지 못해 시작하지 않습니다: " + last.getMessage());
        }
        env.getPropertySources().addFirst(new MapPropertySource("daonVault", props));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;     // StartupSecretCheck(마지막)보다 먼저
    }
}
