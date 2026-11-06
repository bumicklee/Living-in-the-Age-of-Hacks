package com.macro.mall.portal.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;


/**
 * Jackson관련 설정
 * json에서 null 필드를 반환하지 않도록 설정
 * Created by macro on 2018/8/2.
 */
@Configuration
public class JacksonConfig {
    @Bean
    @Primary
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper jacksonObjectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper objectMapper = builder.createXmlMapper(false).build();
        // 이 메서드로 mapper 객체를 설정하며, 직렬화되는 모든 객체는 이 규칙에 따라 직렬화됩니다
        // Include.ALWAYS 기본
        // Include.NON_DEFAULT 속성이 기본값이면 직렬화하지 않음
        // Include.NON_EMPTY 속성이 비어 있음("") 또는 NULL이면 직렬화하지 않으며, 반환되는 json에 이 필드가 없습니다
        // Include.NON_NULL 속성이 NULL인 필드는 직렬화하지 않음
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return objectMapper;
    }
}
