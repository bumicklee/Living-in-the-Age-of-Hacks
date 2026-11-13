package com.macro.mall.security.web;

import com.macro.mall.security.securekit.SecureKitSwitch;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * [다온마켓 6-5] 운영(prod) 프로필에서는 API 문서 화면(Swagger UI)과 문서 JSON(/v3/api-docs)을 끈다.
 * 개발 때 편하라고 켜 둔 문서 화면이 운영에서도 로그인 없이 열려 있으면 모든 API 목록과 입력 형태가 공개된다.
 *
 * - 스위치 secure-kit.docs-closed.enabled=false(make switch K=docs-closed V=off)면 켜 둔 채로 둔다(영상용).
 * - 개발(dev) 프로필에서는 건드리지 않는다.
 */
public class DocsClosedPostProcessor implements EnvironmentPostProcessor, Ordered {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        boolean prod = false;
        for (String p : env.getActiveProfiles()) {
            prod |= p.equalsIgnoreCase("prod");
        }
        for (String p : env.getProperty("spring.profiles.active", "").split(",")) {
            prod |= p.trim().equalsIgnoreCase("prod");
        }
        if (prod && SecureKitSwitch.on("docs-closed")) {
            env.getPropertySources().addFirst(new MapPropertySource("daonDocsClosed", Map.of(
                    "springdoc.api-docs.enabled", "false",
                    "springdoc.swagger-ui.enabled", "false")));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
