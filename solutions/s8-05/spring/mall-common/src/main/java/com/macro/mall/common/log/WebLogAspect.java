package com.macro.mall.common.log;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.json.JSONUtil;
import com.macro.mall.common.domain.WebLog;
import com.macro.mall.common.util.RequestUtil;
import io.swagger.v3.oas.annotations.Operation;
import net.logstash.logback.marker.Markers;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 통합 로그 처리 애스펙트
 * Created by macro on 2018/4/26.
 */
@Aspect
@Component
@Order(1)
public class WebLogAspect {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebLogAspect.class);

    @Pointcut("execution(public * com.macro.mall.controller.*.*(..))||execution(public * com.macro.mall.*.controller.*.*(..))")
    public void webLog() {
    }

    @Before("webLog()")
    public void doBefore(JoinPoint joinPoint) throws Throwable {
    }

    @AfterReturning(value = "webLog()", returning = "ret")
    public void doAfterReturning(Object ret) throws Throwable {
    }

    @Around("webLog()")
    public Object doAround(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        //현재 요청 객체 조회
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes.getRequest();
        //요청 정보 기록(Logstash를 통해 전달Elasticsearch)
        WebLog webLog = new WebLog();
        Object result = joinPoint.proceed();
        Signature signature = joinPoint.getSignature();
        MethodSignature methodSignature = (MethodSignature) signature;
        Method method = methodSignature.getMethod();
        if (method.isAnnotationPresent(Operation.class)) {
            Operation log = method.getAnnotation(Operation.class);
            webLog.setDescription(log.summary());
        }
        long endTime = System.currentTimeMillis();
        String urlStr = request.getRequestURL().toString();
        webLog.setBasePath(StrUtil.removeSuffix(urlStr, URLUtil.url(urlStr).getPath()));
        webLog.setUsername(request.getRemoteUser());
        webLog.setIp(RequestUtil.getRequestIp(request));
        webLog.setMethod(request.getMethod());
        webLog.setParameter(getParameter(method, joinPoint.getArgs()));
        webLog.setResult(result);
        webLog.setSpendTime((int) (endTime - startTime));
        webLog.setStartTime(startTime);
        webLog.setUri(request.getRequestURI());
        webLog.setUrl(request.getRequestURL().toString());
        if (LogMask.enabled()) {
            // 다온마켓 8-5(F35): 응답 본문(result)은 남기지 않는다. 요청 인자는 허용 목록 밖 값을 ***로. 수준은 DEBUG
            // (logback-spring.xml이 이 로거를 INFO로 두므로 운영에서는 찍히지 않고, 디버깅할 때만 켠다)
            webLog.setResult(null);
            webLog.setParameter(allowed(webLog.getParameter()));
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("{} {} {}ms params={}", webLog.getMethod(), webLog.getUri(), webLog.getSpendTime(),
                        JSONUtil.toJsonStr(webLog.getParameter()));
            }
            return result;
        }
        Map<String,Object> logMap = new HashMap<>();
        logMap.put("url",webLog.getUrl());
        logMap.put("method",webLog.getMethod());
        logMap.put("parameter",webLog.getParameter());
        logMap.put("spendTime",webLog.getSpendTime());
        logMap.put("description",webLog.getDescription());
//        LOGGER.info("{}", JSONUtil.parse(webLog));
        LOGGER.info(Markers.appendEntries(logMap), JSONUtil.parse(webLog).toString());
        return result;
    }

    /**
     * 다온마켓 8-5: 요청 인자를 이름 허용 목록(LogMask.ALLOWED_PARAMS)으로 줄인다. 목록 밖 이름은 남기고 값은 ***.
     * 요청 본문(@RequestBody) 객체도 JSON으로 바꿔 같은 규칙을 적용한다(비밀번호·전화번호·주소가 들어 있을 수 있다).
     */
    private static Object allowed(Object parameter) {
        if (parameter == null) {
            return null;
        }
        try {
            return keep(JSONUtil.parse(JSONUtil.toJsonStr(parameter)));
        } catch (RuntimeException e) {
            return "***";                      // JSON으로 바꿀 수 없는 값은 통째로 가린다
        }
    }

    private static Object keep(Object node) {
        if (node instanceof cn.hutool.json.JSONObject o) {
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            for (String k : o.keySet()) {
                Object v = o.get(k);
                out.put(k, LogMask.ALLOWED_PARAMS.contains(k) ? keep(v) : "***");
            }
            return out;
        }
        if (node instanceof cn.hutool.json.JSONArray a) {
            List<Object> out = new ArrayList<>();
            for (Object v : a) {
                out.add(keep(v));
            }
            return out;
        }
        return node;
    }

    /**
     * 메서드와 전달된 파라미터로 요청 파라미터 조회
     */
    private Object getParameter(Method method, Object[] args) {
        List<Object> argList = new ArrayList<>();
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            //RequestBody 어노테이션이 붙은 파라미터를 요청 파라미터로 사용
            RequestBody requestBody = parameters[i].getAnnotation(RequestBody.class);
            if (requestBody != null) {
                argList.add(args[i]);
            }
            //RequestParam 어노테이션이 붙은 파라미터를 요청 파라미터로 사용
            RequestParam requestParam = parameters[i].getAnnotation(RequestParam.class);
            if (requestParam != null) {
                Map<String, Object> map = new HashMap<>();
                String key = parameters[i].getName();
                if (!StrUtil.isEmpty(requestParam.value())) {
                    key = requestParam.value();
                }
                if(args[i]!=null){
                    map.put(key, args[i]);
                    argList.add(map);
                }
            }
        }
        if (argList.size() == 0) {
            return null;
        } else if (argList.size() == 1) {
            return argList.get(0);
        } else {
            return argList;
        }
    }
}
