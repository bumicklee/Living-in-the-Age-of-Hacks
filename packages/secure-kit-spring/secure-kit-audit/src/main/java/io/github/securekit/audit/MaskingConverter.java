package io.github.securekit.audit;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.github.securekit.core.mask.Masking;

import java.util.List;

/**
 * 로그 속 개인정보·토큰 가리기(8-5). Logback 변환어 %kitmsg = 가린 메시지.
 *
 * <pre>
 * &lt;conversionRule conversionWord="kitmsg" converterClass="io.github.securekit.audit.MaskingConverter"/&gt;
 * &lt;pattern&gt;%d %level %logger %kitmsg%n&lt;/pattern&gt;
 * </pre>
 *
 * 개발자가 실수로 log.info("{}", body)를 해도 파일·수집기로 나가기 전에 가린다.
 * 가릴 칸 이름: secure-kit.log-masking.fields (password·token·secret·card …). 전화번호·JWT 모양은 늘 가린다.
 */
public class MaskingConverter extends ClassicConverter {
    private static volatile boolean enabled = true;

    /** 스타터가 설정을 읽어 부른다 */
    public static void configure(boolean on, List<String> fields) {
        enabled = on;
        if (fields != null && !fields.isEmpty()) Masking.useFields(fields);
    }

    @Override
    public String convert(ILoggingEvent event) {
        String msg = event.getFormattedMessage();
        return enabled ? Masking.apply(msg) : msg;
    }
}
