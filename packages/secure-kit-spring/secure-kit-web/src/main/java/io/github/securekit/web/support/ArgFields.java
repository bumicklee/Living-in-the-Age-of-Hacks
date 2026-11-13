package io.github.securekit.web.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.securekit.core.Hashes;
import io.github.securekit.web.KitContext;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;

/** 애스펙트가 메서드 인자에서 값을 꺼내는 도우미(Map 칸 · getter · record 접근자) */
public final class ArgFields {
    private ArgFields() {
    }

    public static Method method(ProceedingJoinPoint pjp) {
        return ((MethodSignature) pjp.getSignature()).getMethod();
    }

    public static HttpServletRequest request() {
        return KitContext.current();
    }

    /** &#64;RequestBody 인자. 없으면 null */
    public static Object body(ProceedingJoinPoint pjp) {
        Method m = method(pjp);
        Annotation[][] anns = m.getParameterAnnotations();
        for (int i = 0; i < anns.length; i++) {
            for (Annotation a : anns[i]) if (a instanceof RequestBody) return pjp.getArgs()[i];
        }
        return null;
    }

    /** 인자들에서 이름이 name인 칸 값을 찾는다 */
    public static Object field(Object[] args, String name) {
        for (Object arg : args) {
            Object v = fieldOf(arg, name);
            if (v != null) return v;
        }
        return null;
    }

    public static Object fieldOf(Object arg, String name) {
        if (arg == null || name.isEmpty()) return null;
        if (arg instanceof Map<?, ?> m) return m.get(name);
        String cap = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (String getter : new String[]{"get" + cap, name}) {
            try {
                Method g = arg.getClass().getMethod(getter);
                if (g.getParameterCount() == 0) return g.invoke(arg);
            } catch (ReflectiveOperationException ignored) {
                // 다음 모양을 본다
            }
        }
        return null;
    }

    /** 요청 본문의 지문(JSON → sha256). 같은 본문인지 비교할 때만 쓴다 */
    public static String fingerprint(ObjectMapper json, Object body) {
        try {
            return Hashes.sha256Hex(json.writeValueAsString(body));
        } catch (Exception e) {
            return Hashes.sha256Hex(String.valueOf(body));
        }
    }
}
