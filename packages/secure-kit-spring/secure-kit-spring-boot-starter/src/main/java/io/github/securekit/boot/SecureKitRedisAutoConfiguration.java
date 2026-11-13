package io.github.securekit.boot;

import io.github.securekit.core.store.CounterStore;
import io.github.securekit.redis.RedisCounterStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * secure-kit.store.type: redis 이고 spring-boot-starter-data-redis가 있으면 Redis 저장소를 쓴다.
 * 그렇지 않으면 SecureKitAutoConfiguration의 메모리 저장소(한 대용).
 */
@AutoConfiguration(before = SecureKitAutoConfiguration.class,
        afterName = "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration")
@ConditionalOnClass(name = "org.springframework.data.redis.core.StringRedisTemplate")
@ConditionalOnProperty(prefix = "secure-kit.store", name = "type", havingValue = "redis")
@EnableConfigurationProperties(SecureKitProperties.class)
public class SecureKitRedisAutoConfiguration {
    @Bean
    public CounterStore secureKitCounterStore(StringRedisTemplate redis, SecureKitProperties props) {
        return new RedisCounterStore(redis, props.getStore().getPrefix(), props.getStore().isFailOpen());
    }
}
