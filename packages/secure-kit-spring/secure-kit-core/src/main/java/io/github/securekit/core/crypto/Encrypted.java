package io.github.securekit.core.crypto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 칸은 저장소에 암호문으로 둔다(field-crypto 부품).
 *
 * <pre>
 * public class Member {
 *     &#64;Encrypted(blindIndex = "phoneIndex") private String phone;
 *     private String phoneIndex;   // 검색용 해시가 들어갈 칸
 * }
 * </pre>
 *
 * 저장 직전 {@link EncryptedFields#seal}, 읽은 직후 {@link EncryptedFields#open}을 부른다.
 * JPA는 AttributeConverter, MyBatis는 TypeHandler 안에서 같은 FieldCipher를 부르면 된다.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Encrypted {
    /** 검색용 해시를 넣을 같은 객체의 칸 이름. 비우면 만들지 않는다 */
    String blindIndex() default "";
}
