package com.macro.mall.security.secret;

import com.macro.mall.security.securekit.SecureKitSwitch;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * [다온마켓 6-3] 알려진 기본값이면 앱이 시작하지 않는다. 설정이 만들어진 직후(앱 코드보다 먼저) 한 번 검사한다.
 *
 * - 검사 대상(설정 키 이름 목록): DB 비밀번호, 모니터링 화면 비밀번호, JWT 서명값(5-2에서 없앴지만 다시 들어오면 막는다).
 *   RabbitMQ·MinIO 키는 6-4에서 서버 쪽 계정을 바꾼 뒤 이 목록에 더한다.
 * - 값이 {@link KnownDefaultSecrets}에 있으면 SECURITY 한 줄(config.startup.denied_default_secret, WARN)을 남기고 시작을 멈춘다.
 *   줄에는 <b>설정 키 이름만</b> 넣고 값은 넣지 않는다. 앱이 바로 멈추므로 이 줄이 마지막 줄이다. 설정이 만들어지는 때는 로그 설정이 아직 없어 표준 오류에 같은 모양으로 직접 쓴다.
 * - 스위치 secure-kit.startup-check.enabled=false(make switch K=startup-check V=off)면 검사하지 않는다(영상용).
 */
public class StartupSecretCheck implements EnvironmentPostProcessor, Ordered {
    /** 6-4에서 RabbitMQ·MinIO 키를 이 목록에 더한다(✏️ 6-4) */
    static final List<String> KEYS = List.of(
            "spring.datasource.password",
            "spring.datasource.druid.stat-view-servlet.login-password",
            "jwt.secret");


    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        if (!SecureKitSwitch.on("startup-check")) {
            return;
        }
        if (KnownDefaultSecrets.isEmpty()) {
            throw new IllegalStateException("기본값 목록(daon/known-default-secrets.txt)을 읽지 못해 시작하지 않습니다");
        }
        for (String key : KEYS) {
            String value;
            try {
                value = env.getProperty(key);
            } catch (IllegalArgumentException unresolved) {
                continue;               // 자리 표시(${…})를 못 채운 칸은 다른 곳에서 실패한다. 여기서는 값이 있는 칸만 본다
            }
            if (KnownDefaultSecrets.contains(value)) {
                System.err.println(OffsetDateTime.now(ZoneId.of("Asia/Seoul")) + "  WARN 1 --- [main] SECURITY : {\"event\":\"config.startup.denied_default_secret\",\"lesson\":\"6-3\",\"actor\":\"system\",\"target\":\"" + key
                        + "\",\"ip\":null,\"ua\":null,\"reason\":\"알려진 기본값 시크릿으로 기동 시도, 시작 중단\",\"count\":1,\"ts\":\""
                        + OffsetDateTime.now(ZoneId.of("Asia/Seoul")) + "\",\"trace_id\":null}");
                throw new IllegalStateException("알려진 기본값 시크릿이 설정되어 시작하지 않습니다: " + key + " (값은 적지 않습니다)");
            }
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;       // 시크릿 저장소에서 값을 채운 뒤(DaonVaultPostProcessor) 마지막에 본다
    }
}
