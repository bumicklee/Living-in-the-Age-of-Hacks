package com.macro.mall.security.securekit;

import com.macro.mall.common.log.LogMask;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * [다온마켓 8-5] 방어 스위치 log-mask(secure-kit.log-mask.enabled)를 mall-common의 LogMask에 넣는다.
 * mall-common(WebLogAspect·MaskingConverter)은 mall-security를 모르기 때문에 이 다리가 필요하다.
 * 7-미션의 서명 확인도 SecureKitSwitch.on()을 거치므로 그대로 적용된다. 값을 못 읽으면 켜짐(가림).
 */
@Component
public class LogMaskBridge {
    @PostConstruct
    public void apply() {
        LogMask.setEnabled(SecureKitSwitch.on("log-mask"));
    }
}
