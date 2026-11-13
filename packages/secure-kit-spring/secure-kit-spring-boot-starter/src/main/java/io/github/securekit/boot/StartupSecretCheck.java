package io.github.securekit.boot;

import io.github.securekit.core.event.SecurityEvents;
import io.github.securekit.core.secret.SecretRules;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 기본값 시크릿으로 기동하지 않기(6-3). 설정을 다 읽은 뒤, 빈을 만들기 전에 돈다.
 *
 * 이름이 secret·password·key·token으로 끝나는 설정(application.yml·환경 변수 등)의 값이
 * changeme·root 같은 기본값이거나 12자 미만이면 config.startup.denied_default_secret을 남기고 기동을 멈춘다.
 * 값은 찍지 않는다. 끄기: secure-kit.startup-check.enabled: false · 예외 키: secure-kit.startup-check.ignore
 */
public class StartupSecretCheck implements EnvironmentPostProcessor, Ordered {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        if (!env.getProperty("secure-kit.startup-check.enabled", Boolean.class, true)) return;
        List<String> ignore = Arrays.asList(env.getProperty("secure-kit.startup-check.ignore", String[].class, new String[0]));
        Map<String, String> secrets = new LinkedHashMap<>();
        for (PropertySource<?> source : env.getPropertySources()) {
            if (!(source instanceof EnumerablePropertySource<?> e) || "systemProperties".equals(source.getName())) continue;
            for (String name : e.getPropertyNames()) {
                if (!SecretRules.isSecretName(name) || ignore.contains(name) || secrets.containsKey(name)) continue;
                secrets.put(name, resolve(env, name, e));
            }
        }
        List<String> denied = SecretRules.denied(secrets);
        if (denied.isEmpty()) return;
        for (String key : denied) {
            SecurityEvents.warn("config.startup.denied_default_secret", "startup-check", "config:" + key, "기본값이거나 12자 미만인 시크릿");
        }
        throw new IllegalStateException("기본값 시크릿으로는 기동하지 않습니다: " + denied);
    }

    private static String resolve(ConfigurableEnvironment env, String name, EnumerablePropertySource<?> source) {
        try {
            String v = env.getProperty(name);
            if (v != null) return v;
        } catch (RuntimeException ignored) {
            // 풀 수 없는 ${…}는 아래 원래 값으로 본다
        }
        Object raw = source.getProperty(name);
        return raw == null ? null : raw.toString();
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
