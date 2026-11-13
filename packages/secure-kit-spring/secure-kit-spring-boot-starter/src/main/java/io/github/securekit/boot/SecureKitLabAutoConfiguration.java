package io.github.securekit.boot;

import io.github.securekit.core.KitResettable;
import io.github.securekit.core.store.CounterStore;
import io.github.securekit.web.aspect.ApprovalService;
import io.github.securekit.web.token.KitTokenService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/** 랩 전용 주소. secure-kit.lab.expose: true 일 때만 생긴다(기본 꺼짐) */
@AutoConfiguration(after = SecureKitAnnotationsAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "secure-kit.lab", name = "expose", havingValue = "true")
public class SecureKitLabAutoConfiguration {
    @Bean
    public SecureKitLabController secureKitLabController(CounterStore store, ObjectProvider<KitResettable> resettables,
                                                         ObjectProvider<KitTokenService> tokens, ApprovalService approvals) {
        return new SecureKitLabController(store, resettables, tokens, approvals);
    }
}
