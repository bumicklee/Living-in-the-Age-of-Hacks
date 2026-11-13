package com.macro.mall.security.crypto;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/** [다온마켓 5-4 정답] 응답을 만드는 서버에서 전화번호를 가린다(010-****-0101). 브라우저에서만 가리면 응답에는 원문이 남는다 */
public class PhoneMaskSerializer extends JsonSerializer<String> {
    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeString(PiiMask.phone(value));
    }
}
