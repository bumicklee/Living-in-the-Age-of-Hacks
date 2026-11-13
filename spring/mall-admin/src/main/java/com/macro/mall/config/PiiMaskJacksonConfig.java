package com.macro.mall.config;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.security.crypto.PhoneMaskSerializer;
import com.macro.mall.security.securekit.SecureKitSwitch;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * [다온마켓 5-4 정답] 관리자 응답의 주문 수령인 전화번호를 서버에서 가린다(주문 목록·상세).
 * 모델 클래스(생성 코드)를 고치지 않고 Jackson 믹스인으로 붙인다. 이 설정은 관리자 앱에만 있어서 고객 앱의 응답은 그대로다.
 * 스위치 pii-mask를 끄면 가리지 않는다(앱이 다시 뜰 때 반영된다).
 */
@Configuration
public class PiiMaskJacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer piiMaskCustomizer() {
        return builder -> {
            if (SecureKitSwitch.on("pii-mask")) {
                builder.mixIn(OmsOrder.class, OrderPhoneMask.class);
            }
        };
    }

    abstract static class OrderPhoneMask {
        @JsonSerialize(using = PhoneMaskSerializer.class)
        abstract String getReceiverPhone();
    }
}
