package com.macro.mall.common.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.macro.mall.common.service.RedisService;
import com.macro.mall.common.service.impl.RedisServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis기본 설정
 * Created by macro on 2020/6/19.
 */
public class BaseRedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory,RedisSerializer<Object> redisSerializer) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(redisSerializer);
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(redisSerializer);
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

    @Bean
    public RedisSerializer<Object> redisSerializer() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        //반드시 설정해야 함. 설정하지 않으면 JSON을 객체로 변환할 수 없고 Map 타입으로 변환됨
        // 다온마켓 7-7: 아무 타입이나 허용(LaissezFaireSubTypeValidator)하던 것을 허용 목록으로 바꾼다
        objectMapper.activateDefaultTyping(typeValidator(), ObjectMapper.DefaultTyping.NON_FINAL);
        //JSON 직렬화기 생성
        return new Jackson2JsonRedisSerializer<>(objectMapper,Object.class);
    }

    /**
     * 다온마켓 7-7: 캐시 값(저장소의 글자)이 만들 수 있는 타입은 이 목록뿐이다. 글자 안의 "@class"가 무엇이든 목록 밖이면 읽기에서 거부된다.
     * 방어 스위치(secure-kit.cache-type-allowlist.enabled)를 끄면 옛 모습(아무 타입이나 허용)으로 돌아간다. 영상·실습용이다.
     */
    static PolymorphicTypeValidator typeValidator() {
        if (!allowListOn()) {
            return LaissezFaireSubTypeValidator.instance;
        }
        return BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.macro.mall.model.")        // 이 쇼핑몰의 모델(UmsAdmin·UmsMember·UmsResource …)
                .allowIfSubType(java.util.ArrayList.class)       // 권한 목록 같은 목록
                .allowIfSubType(java.util.HashMap.class)
                .allowIfSubType(java.util.LinkedHashMap.class)
                .allowIfSubType(java.util.HashSet.class)
                .allowIfSubType(java.util.Date.class)            // 날짜(java.sql.Timestamp 포함)
                .allowIfSubType(java.math.BigDecimal.class)
                .build();
    }

    /** mall-common은 mall-security를 볼 수 없어서 스위치를 환경 변수로 직접 읽는다(SecureKitSwitch와 같은 규칙: 못 읽으면 켜짐) */
    private static boolean allowListOn() {
        String v = System.getenv("SECUREKIT_CACHE_TYPE_ALLOWLIST_ENABLED");
        return v == null || !v.trim().equalsIgnoreCase("false");
    }

    @Bean
    public RedisCacheManager redisCacheManager(RedisConnectionFactory redisConnectionFactory) {
        RedisCacheWriter redisCacheWriter = RedisCacheWriter.nonLockingRedisCacheWriter(redisConnectionFactory);
        //Redis 캐시 유효 기간을 1일로 설정
        RedisCacheConfiguration redisCacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(redisSerializer())).entryTtl(Duration.ofDays(1));
        return new RedisCacheManager(redisCacheWriter, redisCacheConfiguration);
    }


    @Bean
    public RedisService redisService(){
        return new RedisServiceImpl();
    }

}
