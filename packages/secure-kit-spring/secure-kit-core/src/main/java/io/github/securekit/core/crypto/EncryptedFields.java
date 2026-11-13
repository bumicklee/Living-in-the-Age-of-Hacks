package io.github.securekit.core.crypto;

import io.github.securekit.core.event.SecurityEvents;

import java.lang.reflect.Field;

/**
 * &#64;Encrypted가 붙은 String 칸을 암호화·복호화하는 변환기.
 * 넘긴 객체의 칸을 그 자리에서 바꾼다(복사본을 만들지 않는다). 저장용 객체에만 쓴다.
 */
public class EncryptedFields {
    private final FieldCipher cipher;

    public EncryptedFields(FieldCipher cipher) {
        this.cipher = cipher;
    }

    public FieldCipher cipher() {
        return cipher;
    }

    /** 저장 직전: 평문 → enc:1:… , 검색용 해시 칸 채우기 */
    public <T> T seal(T entity) {
        for (Field f : entity.getClass().getDeclaredFields()) {
            Encrypted enc = f.getAnnotation(Encrypted.class);
            if (enc == null || f.getType() != String.class) continue;
            String plain = (String) read(f, entity);
            if (plain == null || plain.startsWith(FieldCipher.PREFIX)) continue;
            if (!enc.blindIndex().isEmpty()) {
                write(field(entity.getClass(), enc.blindIndex()), entity, cipher.blindIndex(plain));
            }
            write(f, entity, cipher.encrypt(plain));
        }
        return entity;
    }

    /** 읽은 직후: enc:1:… → 평문. 풀리지 않으면 null로 두고 crypto.decrypt.failed를 남긴다 */
    public <T> T open(T entity) {
        for (Field f : entity.getClass().getDeclaredFields()) {
            if (f.getAnnotation(Encrypted.class) == null || f.getType() != String.class) continue;
            String stored = (String) read(f, entity);
            if (stored == null || !stored.startsWith(FieldCipher.PREFIX)) continue;
            try {
                write(f, entity, cipher.decrypt(stored));
            } catch (IllegalArgumentException e) {
                SecurityEvents.warn("crypto.decrypt.failed", "field-crypto", "field:" + f.getName(), "암호문을 풀지 못했다(키·위조 확인)");
                write(f, entity, null);
            }
        }
        return entity;
    }

    private static Field field(Class<?> type, String name) {
        try {
            return type.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("검색용 해시 칸이 없다: " + name);
        }
    }

    private static Object read(Field f, Object o) {
        try {
            f.setAccessible(true);
            return f.get(o);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void write(Field f, Object o, Object v) {
        try {
            f.setAccessible(true);
            f.set(o, v);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
